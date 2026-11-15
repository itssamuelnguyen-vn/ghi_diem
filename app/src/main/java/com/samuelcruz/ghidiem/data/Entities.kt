package com.samuelcruz.ghidiem.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.samuelcruz.ghidiem.core.Ev
import com.samuelcruz.ghidiem.core.Rules
import com.samuelcruz.ghidiem.core.Setup
import com.samuelcruz.ghidiem.core.Sport
import kotlinx.serialization.Serializable

/**
 * A match. The score is never stored as truth: it is replayed from [EventRow]s ([score]/[detail]/[winner] are a
 * cache for the history list). The clock is a stopwatch: [clockBaseMs] counted so far, plus the time since
 * [runningSince] (wall clock, 0 = paused), so it keeps counting while the app is closed.
 */
@Serializable
@Entity(tableName = "matches")
data class MatchRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sport: String,
    val rules: String,
    val nameA: String,
    val nameB: String,
    val firstServer: Int = 0,
    val createdAt: Long,
    /** When the clock was first started (0 = not yet). */
    val startedAt: Long = 0,
    /** 0 = still being played. */
    val finishedAt: Long = 0,
    val clockBaseMs: Long = 0,
    val runningSince: Long = 0,
    /** Show side B on the left (after changing ends). Display only. */
    val swapped: Boolean = false,
    val score: String = "0-0",
    val detail: String = "",
    val winner: Int = -1,
    /** Ended by hand ("Kết thúc" / "Dừng trận"), not by the rules: deleting events must not reopen it. */
    val manualEnd: Boolean = false,
    /** Two against two: names are "Player 1 / Player 2" and the service order follows the players. */
    @ColumnInfo(defaultValue = "0") val doubles: Boolean = false,
    /** Part of a [TournamentRow] (0 = a friendly match), as fixture [fixture] ("rr:0:3", "ko:1:0"). */
    @ColumnInfo(defaultValue = "0") val tournamentId: Long = 0,
    @ColumnInfo(defaultValue = "") val fixture: String = "",
) {
    val sportEnum get() = Sport.of(sport)
    val rulesObj get() = Rules.fromJson(rules)
    val setup get() = Setup(sportEnum, rulesObj, firstServer, doubles)
    /** Players of [side]: two in doubles, else the one name. */
    fun players(side: Int): List<String> =
        com.samuelcruz.ghidiem.core.splitPlayers(name(side)).let { if (doubles && it.size == 2) it else listOf(name(side)) }
    val running get() = runningSince > 0
    val finished get() = finishedAt > 0
    fun name(side: Int) = if (side == 0) nameA else nameB

    fun clockAt(now: Long) = clockBaseMs + if (runningSince > 0) (now - runningSince).coerceAtLeast(0) else 0
    fun startClock(now: Long) = if (running) this else copy(runningSince = now, startedAt = if (startedAt == 0L) now else startedAt)
    fun stopClock(now: Long) = if (!running) this else copy(clockBaseMs = clockAt(now), runningSince = 0)
}

@Serializable
@Entity(
    tableName = "events",
    foreignKeys = [ForeignKey(MatchRow::class, ["id"], ["matchId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("matchId")],
)
data class EventRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    /** 0 = A, 1 = B, -1 = period events. */
    val side: Int,
    /** A [com.samuelcruz.ghidiem.core.Kind]. */
    val kind: Int,
    /** Match clock when it happened. */
    val clockMs: Long,
    /** Wall clock when it was recorded. */
    val at: Long,
    /** Scorer / player, typed after the fact. */
    val note: String = "",
) {
    fun ev() = Ev(side, kind, clockMs, note)
}

/** A small tournament: entrants (one per line, in seed order) play round-robin or knockout with the same rules. */
@Serializable
@Entity(tableName = "tournaments")
data class TournamentRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sport: String,
    val rules: String,
    /** [FORMAT_ROUND_ROBIN] or [FORMAT_KNOCKOUT]. */
    val format: Int,
    /** Team / player names, one per line ("A / B" for a doubles pair). */
    val entrants: String,
    val doubles: Boolean = false,
    val createdAt: Long,
) {
    val sportEnum get() = Sport.of(sport)
    val names get() = entrants.lines().map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val FORMAT_ROUND_ROBIN = 0
        const val FORMAT_KNOCKOUT = 1
    }
}
