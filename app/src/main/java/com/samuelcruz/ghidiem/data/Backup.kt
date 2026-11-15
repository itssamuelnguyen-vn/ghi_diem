package com.samuelcruz.ghidiem.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Everything in the app as one JSON file: the backup format, and the file kept on Google Drive. */
@Serializable
data class Snapshot(
    val app: String = "ghidiem",
    val version: Int = 1,
    val savedAt: Long = 0,
    val matches: List<MatchRow> = emptyList(),
    val events: List<EventRow> = emptyList(),
    val tournaments: List<TournamentRow> = emptyList(),
)

object Backup {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun write(db: AppDatabase, out: OutputStream) = withContext(Dispatchers.IO) {
        val dao = db.matches()
        val s = Snapshot(savedAt = System.currentTimeMillis(), matches = dao.allMatches(), events = dao.allEvents(),
            tournaments = dao.allTournaments())
        out.write(json.encodeToString(Snapshot.serializer(), s).toByteArray())
        out.flush()
    }

    /** Reads a backup and replaces everything with it (all or nothing). Throws on a file that is not ours. */
    suspend fun restore(db: AppDatabase, input: InputStream) = withContext(Dispatchers.IO) {
        val s = json.decodeFromString(Snapshot.serializer(), input.readBytes().decodeToString())
        require(s.app == "ghidiem") { "Không phải file sao lưu của Ghi Điểm" }
        db.matches().replaceAll(s.matches, s.events, s.tournaments)
    }

    fun fileName() = "ghidiem-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".json"
}

/**
 * Safety copies made before sync replaces data (filesDir/snapshots, newest [KEEP] kept). Settings lists them and
 * can restore one, so picking the wrong side of a sync conflict is never final.
 */
object SnapshotFiles {
    private const val KEEP = 5
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

    fun dir(context: android.content.Context) = File(context.filesDir, "snapshots").apply { mkdirs() }

    fun list(context: android.content.Context): List<File> =
        dir(context).listFiles()?.filter { it.name.endsWith(".json") }?.sortedByDescending { it.name }.orEmpty()

    suspend fun saveLocalCopy(context: android.content.Context, db: AppDatabase, label: String) = withContext(Dispatchers.IO) {
        file(context, label).outputStream().use { Backup.write(db, it) }
        trim(context)
    }

    suspend fun saveBytes(context: android.content.Context, bytes: ByteArray, label: String) = withContext(Dispatchers.IO) {
        file(context, label).writeBytes(bytes)
        trim(context)
    }

    private fun file(context: android.content.Context, label: String) =
        File(dir(context), LocalDateTime.now().format(stamp) + "-" + label + ".json")

    private fun trim(context: android.content.Context) = list(context).drop(KEEP).forEach { it.delete() }
}
