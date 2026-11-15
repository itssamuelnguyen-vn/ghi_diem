package com.samuelcruz.ghidiem.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class Sport(val title: String, val emoji: String, val setWord: String) {
    FOOTBALL("Bóng đá", "⚽", "Hiệp"),
    BADMINTON("Cầu lông", "🏸", "Ván"),
    VOLLEYBALL("Bóng chuyền", "🏐", "Set"),
    TABLE_TENNIS("Bóng bàn", "🏓", "Ván"),
    TENNIS("Tennis", "🎾", "Set");

    /** Points come one rally at a time and someone serves (everything but football). */
    val hasServe get() = this != FOOTBALL

    fun defaultRules(): Rules = when (this) {
        FOOTBALL -> Rules(periodMinutes = 45, periods = 2, extraMinutes = 15)
        BADMINTON -> Rules(bestOf = 3, points = 21, decidingPoints = 21, cap = 30)
        VOLLEYBALL -> Rules(bestOf = 5, points = 25, decidingPoints = 15, cap = 0)
        TABLE_TENNIS -> Rules(bestOf = 5, points = 11, decidingPoints = 11, cap = 0)
        TENNIS -> Rules(bestOf = 3, games = 6, noAd = false, finalSet = FinalSet.TIEBREAK)
    }

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: BADMINTON
    }
}

/** How the last set of a tennis match is decided when it reaches games-all. */
enum class FinalSet(val title: String) {
    TIEBREAK("Tie-break 7 điểm"),
    SUPER_TIEBREAK("Super tie-break 10 điểm (thay set cuối)"),
    ADVANTAGE("Đánh tiếp đến khi hơn 2 game"),
}

/**
 * The rules a match is played with. One class for every sport (stored as JSON on the match); each sport reads
 * only its own fields.
 */
@Serializable
data class Rules(
    // Rally sports (badminton, volleyball, table tennis) and tennis
    val bestOf: Int = 3,
    /** Points to win a set / game (win by 2). */
    val points: Int = 21,
    /** Points to win the deciding set (volleyball: 15). */
    val decidingPoints: Int = 21,
    /** First to this many points wins even without a 2-point lead (badminton 30); 0 = no cap. */
    val cap: Int = 0,
    // Tennis
    val games: Int = 6,
    val noAd: Boolean = false,
    val finalSet: FinalSet = FinalSet.TIEBREAK,
    // Football
    val periodMinutes: Int = 45,
    val periods: Int = 2,
    val extraMinutes: Int = 15,
) {
    fun toJson() = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun fromJson(s: String): Rules = runCatching { json.decodeFromString(serializer(), s) }.getOrDefault(Rules())
    }
}

/** What an event is. Stored as an Int on the event row. */
object Kind {
    const val POINT = 0
    const val GOAL = 1
    const val YELLOW = 2
    const val RED = 3
    const val PERIOD_START = 10
    const val PERIOD_END = 11

    /** Events the undo button takes back (period changes are not undone). */
    fun undoable(kind: Int) = kind < PERIOD_START
}

/** One thing that happened: [side] 0 or 1 (-1 for period events), at [clockMs] on the match clock. */
data class Ev(val side: Int, val kind: Int, val clockMs: Long, val note: String = "")

/** "12:34", or "1:02:03" past an hour. */
fun fmtClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** "1 giờ 05 phút", "23 phút". */
fun fmtDuration(ms: Long): String {
    val min = ms / 60_000
    return if (min >= 60) "${min / 60} giờ %02d phút".format(min % 60) else "$min phút"
}
