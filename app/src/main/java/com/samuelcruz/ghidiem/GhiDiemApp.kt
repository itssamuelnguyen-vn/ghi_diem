package com.samuelcruz.ghidiem

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.samuelcruz.ghidiem.data.AppDatabase
import com.samuelcruz.ghidiem.data.Prefs
import com.samuelcruz.ghidiem.data.Repo
import com.samuelcruz.ghidiem.sync.SnapshotSync
import com.samuelcruz.ghidiem.widget.ScoreWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** Holds the long-lived objects (like a Rails initializer). No DI library: there are only a few. */
class GhiDiemApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val prefs: Prefs by lazy { Prefs(this) }
    val repo: Repo by lazy { Repo(db.matches(), ::changed) }
    val speaker: Speaker by lazy { Speaker(this) }
    val sounds: Sounds by lazy { Sounds(this) }
    /** For writes that must finish even if the screen that started them closes. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Short messages from background work (sync), shown by the home screen. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /**
     * Set by the open match screen: gets volume-key presses (true = volume up) when the setting is on.
     * MainActivity routes the keys here instead of changing the volume.
     */
    @Volatile var volumeHandler: ((Boolean) -> Unit)? = null

    /** Result of the last sync, for Settings (conflicts are answered there). */
    var syncState by mutableStateOf<SnapshotSync.Result?>(null)
        private set

    private var widgetJob: Job? = null
    private var syncJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        if (prefs.syncEmail.isNotEmpty()) SnapshotSync.schedule(this)
        refreshWidget() // redraw widgets with this version's code after an update
    }

    /** Something was written: refresh the widget, and upload to Drive a little later (after a burst of points). */
    fun changed() {
        prefs.changedAt = System.currentTimeMillis()
        refreshWidget()
        syncSoon(20_000)
    }

    /** Data was replaced from a backup or from Drive (not a local edit, nothing to upload). */
    fun dataReplaced() = refreshWidget()

    fun refreshWidget() {
        widgetJob?.cancel()
        widgetJob = scope.launch {
            delay(300)
            runCatching { ScoreWidget.refresh(this@GhiDiemApp) }
        }
    }

    /** Syncs with Drive after [delayMs] (each call restarts the wait). Does nothing when not connected. */
    fun syncSoon(delayMs: Long = 0, keep: SnapshotSync.Keep? = null) {
        if (prefs.syncEmail.isEmpty()) return
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(delayMs)
            val r = SnapshotSync.sync(this@GhiDiemApp, keep)
            syncState = r
            if (r == SnapshotSync.Result.Downloaded) messages.tryEmit("Đã cập nhật dữ liệu từ Google Drive")
        }
    }
}
