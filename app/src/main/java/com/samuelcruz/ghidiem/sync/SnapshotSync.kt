package com.samuelcruz.ghidiem.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.samuelcruz.ghidiem.GhiDiemApp
import com.samuelcruz.ghidiem.data.Backup
import com.samuelcruz.ghidiem.data.SnapshotFiles
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Google Drive sync, the simple way (same design as Sổ Nợ): all matches are one file, `ghidiem-data.json` (the
 * backup format), in the app's hidden Drive folder. Drive numbers every change of a file ([RemoteFile.version]):
 * - Drive unchanged since our last sync, this phone changed → upload.
 * - Drive changed (another phone), this phone unchanged → download and replace.
 * - Both changed → [Result.Conflict]: the user picks which copy to keep; the other is saved as a safety copy.
 */
object SnapshotSync {
    const val FILE = "ghidiem-data.json"
    private const val WORK = "drive-sync"
    private val lock = Mutex()

    sealed interface Result {
        data object Nothing : Result
        data object Uploaded : Result
        data object Downloaded : Result
        data object Conflict : Result
        data object NeedsSignIn : Result
        data class Failed(val message: String) : Result
    }

    enum class Keep { THIS_PHONE, DRIVE }

    /** Every 6 hours with a network, in the background. */
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)

    /** One sync pass. [keep] answers an earlier [Result.Conflict]. */
    suspend fun sync(context: Context, keep: Keep? = null): Result = lock.withLock {
        val app = context.applicationContext as GhiDiemApp
        val prefs = app.prefs
        if (prefs.syncEmail.isEmpty()) return Result.Nothing
        val result = try {
            var token = GoogleAuth.silentToken(context, prefs.syncEmail) ?: return Result.NeedsSignIn
            try {
                pass(app, Drive(token), keep)
            } catch (e: DriveException) {
                if (e.code != 401) throw e
                GoogleAuth.clearToken(context, token)
                token = GoogleAuth.silentToken(context, prefs.syncEmail) ?: return Result.NeedsSignIn
                pass(app, Drive(token), keep)
            }
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
        prefs.syncError = when (result) {
            is Result.Failed -> result.message
            Result.NeedsSignIn -> "Cần đăng nhập Google lại"
            Result.Conflict -> "Hai máy cùng sửa: chọn giữ bản nào trong Cài đặt"
            else -> ""
        }
        result
    }

    private suspend fun pass(app: GhiDiemApp, drive: Drive, keep: Keep?): Result {
        val prefs = app.prefs
        val remote = drive.list().filter { it.name == FILE }.maxByOrNull { it.modifiedAt }
        val dirty = prefs.changedAt > prefs.lastSyncAt
        val known = remote != null && remote.id == prefs.syncFileId && remote.version == prefs.syncVersion

        fun done(f: RemoteFile) {
            prefs.syncFileId = f.id
            prefs.syncVersion = f.version
            prefs.lastSyncAt = maxOf(System.currentTimeMillis(), prefs.changedAt)
        }

        suspend fun upload(): Result {
            val bytes = ByteArrayOutputStream().also { Backup.write(app.db, it) }.toByteArray()
            val f = if (remote == null) drive.create(FILE, "application/json", bytes) else drive.update(remote.id, "application/json", bytes)
            done(f)
            return Result.Uploaded
        }

        suspend fun download(): Result {
            val bytes = drive.download(remote!!.id)
            Backup.restore(app.db, ByteArrayInputStream(bytes))
            app.dataReplaced()
            done(remote)
            return Result.Downloaded
        }

        return when {
            keep == Keep.THIS_PHONE -> {
                // Keep Drive's copy on this phone before overwriting it, in case it was the wrong choice.
                if (remote != null) SnapshotFiles.saveBytes(app, drive.download(remote.id), "ban-drive-truoc-khi-ghi-de")
                upload()
            }
            keep == Keep.DRIVE && remote != null -> {
                SnapshotFiles.saveLocalCopy(app, app.db, "truoc-khi-lay-tu-drive")
                download()
            }
            remote == null -> upload()
            known -> if (dirty) upload() else Result.Nothing
            // Drive changed elsewhere. A phone that changed too, or never synced but has data, must choose.
            dirty || (prefs.syncVersion == 0L && app.db.matches().allMatches().isNotEmpty()) -> Result.Conflict
            else -> download()
        }
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val r = SnapshotSync.sync(applicationContext)
        return if (r is SnapshotSync.Result.Failed) Result.retry() else Result.success()
    }
}
