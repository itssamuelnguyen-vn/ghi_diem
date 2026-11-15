package com.samuelcruz.ghidiem.core

import kotlin.math.max
import kotlin.math.min

/** A finished set (or tennis set). [tbA]/[tbB] are tie-break points (-1 = no tie-break). */
data class SetScore(
    val a: Int,
    val b: Int,
    val tbA: Int = -1,
    val tbB: Int = -1,
    val superTb: Boolean = false,
    /** Match clock when the set ended. */
    val endMs: Long = 0,
) {
    val winner get() = if (a > b) 0 else 1
    fun text(): String = when {
        superTb -> "[$a-$b]"
        tbA >= 0 -> "$a-$b(${min(tbA, tbB)})"
        else -> "$a-$b"
    }
}

/** One row of the timeline, made for the event at the same index. */
data class Line(
    /** Score right after the event ("11-9", "3-2 · 30-15", "2-1"), empty for cards. */
    val score: String,
    /** Extra words ("Thắng ván 1", "Break", "Thẻ vàng"), may be empty. */
    val label: String,
    /** Ends a game/set, or a goal/card/period: shown even when the timeline hides single points. */
    val major: Boolean,
    /** Clock text: "12:34" (rally sports) or the minute "45+2'" (football). */
    val time: String,
    /** 1-based set / period the event belongs to. */
    val setNo: Int,
    /** Something to tell the players right after this event ("Đổi sân"). */
    val alert: String? = null,
)

/** "Match point" for one side. */
data class Callout(val side: Int, val text: String)

/** Everything the scoreboard shows, worked out by replaying the events. */
data class Board(
    /** Big number of each side: points of the current set, tennis points ("40", "AD"), or goals. */
    val main: List<String> = listOf("0", "0"),
    val setsWon: List<Int> = listOf(0, 0),
    /** Tennis only: games in the current set. */
    val games: List<Int>? = null,
    val history: List<SetScore> = emptyList(),
    /** 1-based number of the set / period being played. */
    val setNo: Int = 1,
    /** -1 = nobody (football, or the match is over). */
    val server: Int = -1,
    val serveHint: String? = null,
    val callouts: List<Callout> = emptyList(),
    /** "Deuce", "Tie-break"… */
    val info: String? = null,
    val finished: Boolean = false,
    val winner: Int = -1,
    val lines: List<Line> = emptyList(),
    /** Short result for lists: "2-1" or goals. */
    val score: String = "0-0",
    /** Set scores for lists: "21-19 · 18-21 · 21-15". */
    val detail: String = "",
    // Football
    val cards: List<List<Int>> = listOf(listOf(0, 0), listOf(0, 0)),
    /** Number of periods started. */
    val period: Int = 0,
    val periodOpen: Boolean = false,
    /** Match clock when each started period began. */
    val periodStarts: List<Long> = emptyList(),
) {
    /** Alert of the last event (shown as a banner until the next event). */
    val alert get() = lines.lastOrNull()?.alert
}

data class Setup(val sport: Sport, val rules: Rules, val firstServer: Int = 0)

object Scoring {
    fun replay(setup: Setup, events: List<Ev>): Board = when (setup.sport) {
        Sport.FOOTBALL -> Football(setup.rules).run(events)
        Sport.TENNIS -> Tennis(setup.rules, setup.firstServer).run(events)
        else -> Rally(setup.sport, setup.rules, setup.firstServer).run(events)
    }
}

/** Badminton, volleyball, table tennis: rally scoring, sets to [Rules.points] (win by 2, optional cap). */
internal class Rally(private val sport: Sport, private val r: Rules, private val firstServer: Int) {
    var cur = intArrayOf(0, 0)
    var won = intArrayOf(0, 0)
    val sets = mutableListOf<SetScore>()
    var server = firstServer
    var finished = false
    var winner = -1
    /** Who served first in the current set (table tennis service order). */
    private var setFirst = firstServer
    /** The mid-set break (badminton 11, deciding-set change of ends) already happened in this set. */
    private var midDone = false

    private val setIndex get() = sets.size
    /** The last possible set (also the only set of a 1-set match): change of ends in the middle. */
    private val deciding get() = setIndex == r.bestOf - 1
    /** Volleyball's shorter deciding set only exists in matches of several sets. */
    private val target get() = if (deciding && r.bestOf > 1) r.decidingPoints else r.points
    private val mid get() = if (sport == Sport.TABLE_TENNIS) target / 2 else (target + 1) / 2

    private fun clone() = Rally(sport, r, firstServer).also {
        it.cur = cur.clone(); it.won = won.clone(); it.sets.addAll(sets); it.server = server
        it.finished = finished; it.winner = winner; it.setFirst = setFirst; it.midDone = midDone
    }

    /** Adds a point; returns the timeline line. */
    fun point(side: Int, clockMs: Long, time: String): Line {
        val setNo = setIndex + 1
        if (finished) return Line("", "", false, time, setNo)
        val o = 1 - side
        cur[side]++
        val t = target
        val setWon = (cur[side] >= t && cur[side] - cur[o] >= 2) || (r.cap > 0 && cur[side] >= r.cap)
        if (setWon) {
            sets += SetScore(cur[0], cur[1], endMs = clockMs)
            won[side]++
            val score = "${cur[0]}-${cur[1]}"
            if (won[side] > r.bestOf / 2) {
                finished = true
                winner = side
                server = -1
                return Line(score, "Thắng trận", true, time, setNo)
            }
            cur = intArrayOf(0, 0)
            midDone = false
            // Badminton: the winner of a game serves first in the next; volleyball / table tennis alternate.
            setFirst = if (sport == Sport.BADMINTON) side else firstServer xor (setIndex % 2)
            server = setFirst
            val rest = if (sport == Sport.VOLLEYBALL) "" else " · Nghỉ giữa ${sport.setWord.lowercase()}"
            return Line(score, "Thắng ${sport.setWord.lowercase()} $setNo", true, time, setNo, "Đổi sân$rest")
        }
        server = if (sport == Sport.TABLE_TENNIS) tableTennisServer() else side
        var alert: String? = null
        if (!midDone && max(cur[0], cur[1]) == mid) {
            midDone = true
            alert = when {
                sport == Sport.BADMINTON && deciding -> "Nghỉ 60 giây · Đổi sân"
                sport == Sport.BADMINTON -> "Nghỉ 60 giây"
                deciding -> "Đổi sân"
                else -> null
            }
        }
        return Line("${cur[0]}-${cur[1]}", "", false, time, setNo, alert)
    }

    /** Service changes every 2 points, every point from (points-1)-all. */
    private fun tableTennisServer(): Int {
        val deuce = r.points - 1
        val total = cur[0] + cur[1]
        val changes = if (cur[0] >= deuce && cur[1] >= deuce) deuce + (total - 2 * deuce) else total / 2
        return setFirst xor (changes % 2)
    }

    fun run(events: List<Ev>): Board {
        val lines = events.map { point(it.side, it.clockMs, fmtClock(it.clockMs)) }
        val callouts = if (finished) emptyList() else (0..1).mapNotNull { s ->
            val c = clone()
            c.point(s, 0, "")
            when {
                c.finished -> Callout(s, "Match point")
                c.sets.size > sets.size -> Callout(s, if (sport == Sport.VOLLEYBALL) "Set point" else "Game point")
                else -> null
            }
        }
        val hint = if (sport == Sport.BADMINTON && server >= 0) {
            if (cur[server] % 2 == 0) "Giao ô phải" else "Giao ô trái"
        } else null
        val detail = (sets.map { it.text() } + if (!finished && cur.sum() > 0) listOf("${cur[0]}-${cur[1]}") else emptyList())
            .joinToString(" · ")
        return Board(
            main = listOf(cur[0].toString(), cur[1].toString()),
            setsWon = won.toList(),
            history = sets.toList(),
            setNo = if (finished) sets.size else sets.size + 1,
            server = server,
            serveHint = hint,
            callouts = callouts,
            info = if (!finished && deciding && r.bestOf > 1) "${sport.setWord} quyết định" else null,
            finished = finished,
            winner = winner,
            lines = lines,
            score = "${won[0]}-${won[1]}",
            detail = detail,
        )
    }
}

/** Tennis: 15/30/40, deuce or no-ad, sets to [Rules.games] with a 7-point tie-break, final-set options. */
internal class Tennis(private val r: Rules, firstServer: Int) {
    var p = intArrayOf(0, 0)
    var g = intArrayOf(0, 0)
    var won = intArrayOf(0, 0)
    val sets = mutableListOf<SetScore>()
    var server = firstServer
    var tb = false
    var superTb = false
    private var tbTarget = 7
    /** Who served the first point of the tie-break. */
    private var tbFirst = firstServer
    var finished = false
    var winner = -1

    private val finalSet get() = won[0] == r.bestOf / 2 && won[1] == r.bestOf / 2

    init {
        startSet()
    }

    private fun clone() = Tennis(r, server).also {
        it.p = p.clone(); it.g = g.clone(); it.won = won.clone(); it.sets.addAll(sets); it.server = server
        it.tb = tb; it.superTb = superTb; it.tbTarget = tbTarget; it.tbFirst = tbFirst
        it.finished = finished; it.winner = winner
    }

    private fun startSet() {
        g = intArrayOf(0, 0)
        p = intArrayOf(0, 0)
        if (r.bestOf > 1 && finalSet && r.finalSet == FinalSet.SUPER_TIEBREAK) {
            tb = true; superTb = true; tbTarget = 10; tbFirst = server
        }
    }

    private fun gameScore() = "${g[0]}-${g[1]}"

    private fun pointText(side: Int): String {
        if (tb) return p[side].toString()
        val o = 1 - side
        if (p[0] >= 3 && p[1] >= 3) return if (p[side] > p[o]) "AD" else "40"
        return listOf("0", "15", "30", "40")[min(p[side], 3)]
    }

    private fun current() = if (tb) "${gameScore()} · ${p[0]}-${p[1]}" else
        "${gameScore()} · ${pointText(0)}-${pointText(1)}"

    fun point(side: Int, clockMs: Long, time: String): Line {
        val setNo = sets.size + 1
        if (finished) return Line("", "", false, time, setNo)
        val o = 1 - side
        if (tb) {
            p[side]++
            if (p[side] >= tbTarget && p[side] - p[o] >= 2) {
                val set = if (superTb) SetScore(p[0], p[1], superTb = true, endMs = clockMs)
                else {
                    g[side]++
                    SetScore(g[0], g[1], p[0], p[1], endMs = clockMs)
                }
                sets += set
                // The player who received first in the tie-break serves first in the next set.
                server = 1 - tbFirst
                tb = false; superTb = false
                return endSet(side, set, time, setNo)
            }
            val total = p[0] + p[1]
            server = tbFirst xor (((total + 1) / 2) % 2)
            return Line(current(), "", false, time, setNo, if (total % 6 == 0) "Đổi sân" else null)
        }
        p[side]++
        if (!r.noAd && p[0] >= 4 && p[1] >= 4) p = intArrayOf(3, 3) // back to deuce
        val gameWon = p[side] >= 4 && (r.noAd || p[side] - p[o] >= 2)
        if (!gameWon) return Line(current(), "", false, time, setNo)
        val broke = side != server
        g[side]++
        p = intArrayOf(0, 0)
        server = 1 - server
        val n = r.games
        if (g[side] >= n && g[side] - g[o] >= 2) {
            val set = SetScore(g[0], g[1], endMs = clockMs)
            sets += set
            return endSet(side, set, time, setNo)
        }
        val label = if (broke) "Break" else "Game"
        if (g[0] == n && g[1] == n && !(finalSet && r.finalSet == FinalSet.ADVANTAGE)) {
            tb = true; tbTarget = 7; tbFirst = server
            return Line(gameScore(), label, true, time, setNo, "Tie-break")
        }
        return Line(gameScore(), label, true, time, setNo, if ((g[0] + g[1]) % 2 == 1) "Đổi sân" else null)
    }

    private fun endSet(side: Int, set: SetScore, time: String, setNo: Int): Line {
        won[side]++
        if (won[side] > r.bestOf / 2) {
            finished = true
            winner = side
            server = -1
            return Line(set.text(), "Thắng trận", true, time, setNo)
        }
        val odd = !set.superTb && (set.a + set.b) % 2 == 1
        startSet()
        return Line(set.text(), "Thắng set $setNo", true, time, setNo, if (odd) "Đổi sân · Nghỉ giữa set" else "Nghỉ giữa set")
    }

    fun run(events: List<Ev>): Board {
        val lines = events.map { point(it.side, it.clockMs, fmtClock(it.clockMs)) }
        val callouts = if (finished) emptyList() else (0..1).mapNotNull { s ->
            val c = clone()
            c.point(s, 0, "")
            when {
                c.finished -> Callout(s, "Match point")
                c.sets.size > sets.size -> Callout(s, "Set point")
                !tb && c.g[s] + c.g[1 - s] != g[s] + g[1 - s] && s != server -> Callout(s, "Break point")
                else -> null
            }
        }
        val info = when {
            finished -> null
            superTb -> "Super tie-break"
            tb -> "Tie-break"
            !r.noAd && p[0] >= 3 && p[0] == p[1] -> "Deuce"
            r.noAd && p[0] == 3 && p[1] == 3 -> "Điểm quyết định"
            else -> null
        }
        val hint = if (server >= 0) {
            if ((p[0] + p[1]) % 2 == 0) "Giao bên phải" else "Giao bên trái"
        } else null
        val playing = !finished && (g.sum() > 0 || p.sum() > 0)
        return Board(
            main = listOf(pointText(0), pointText(1)),
            setsWon = won.toList(),
            games = g.toList(),
            history = sets.toList(),
            setNo = if (finished) sets.size else sets.size + 1,
            server = server,
            serveHint = hint,
            callouts = callouts,
            info = info,
            finished = finished,
            winner = winner,
            lines = lines,
            score = "${won[0]}-${won[1]}",
            detail = (sets.map { it.text() } + if (playing) listOf(gameScore()) else emptyList()).joinToString(" · "),
        )
    }
}

/** Football: goals and cards; the match clock runs per period (hiệp), shown as the match minute. */
internal class Football(private val r: Rules) {
    private val goals = intArrayOf(0, 0)
    private val yellow = intArrayOf(0, 0)
    private val red = intArrayOf(0, 0)
    private val starts = mutableListOf<Long>()
    private var ends = 0

    fun run(events: List<Ev>): Board {
        val lines = events.map { e ->
            val k = starts.size - 1
            when (e.kind) {
                Kind.PERIOD_START -> {
                    starts += e.clockMs
                    Line("${goals[0]}-${goals[1]}", "Bắt đầu ${periodName(r, starts.size)}", true,
                        "${FootballClock.base(r, starts.size - 1)}'", starts.size)
                }
                Kind.PERIOD_END -> {
                    ends++
                    Line("${goals[0]}-${goals[1]}", "Hết ${periodName(r, starts.size)}", true,
                        FootballClock.minute(r, starts, e.clockMs), starts.size)
                }
                Kind.GOAL -> {
                    goals[e.side]++
                    Line("${goals[0]}-${goals[1]}", "Bàn thắng", true, FootballClock.minute(r, starts, e.clockMs), k + 1)
                }
                Kind.YELLOW -> {
                    yellow[e.side]++
                    Line("", "Thẻ vàng", true, FootballClock.minute(r, starts, e.clockMs), k + 1)
                }
                Kind.RED -> {
                    red[e.side]++
                    Line("", "Thẻ đỏ", true, FootballClock.minute(r, starts, e.clockMs), k + 1)
                }
                else -> Line("", "", false, "", k + 1)
            }
        }
        return Board(
            main = listOf(goals[0].toString(), goals[1].toString()),
            winner = when {
                goals[0] > goals[1] -> 0
                goals[1] > goals[0] -> 1
                else -> -1
            },
            lines = lines,
            score = "${goals[0]}-${goals[1]}",
            cards = listOf(listOf(yellow[0], red[0]), listOf(yellow[1], red[1])),
            period = starts.size,
            periodOpen = starts.size > ends,
            periodStarts = starts.toList(),
            setNo = max(starts.size, 1),
        )
    }
}

/** "Hiệp 1", "Hiệp 2", then "Hiệp phụ 1"… ([n] is 1-based). */
fun periodName(r: Rules, n: Int) = if (n <= r.periods) "hiệp $n" else "hiệp phụ ${n - r.periods}"

object FootballClock {
    /** Length in minutes of period [k] (0-based). */
    fun length(r: Rules, k: Int) = if (k < r.periods) r.periodMinutes else r.extraMinutes

    /** Match minute at which period [k] (0-based) starts: 0, 45, 90, 105. */
    fun base(r: Rules, k: Int) = (0 until k).sumOf { length(r, it) }

    /** Minute of play like a referee writes it: "23'", stoppage time "45+2'". */
    fun minute(r: Rules, starts: List<Long>, clockMs: Long): String {
        val k = starts.size - 1
        if (k < 0) return "0'"
        val m = ((clockMs - starts[k]).coerceAtLeast(0) / 60_000 + 1).toInt()
        val len = length(r, k)
        val base = base(r, k)
        return if (m > len) "${base + len}+${m - len}'" else "${base + m}'"
    }

    /** The big clock: (main "47:12", stoppage "+02:12" or null) for time [inPeriodMs] into period [k]. */
    fun display(r: Rules, k: Int, inPeriodMs: Long): Pair<String, String?> {
        val lenMs = length(r, k) * 60_000L
        val baseMs = base(r, k) * 60_000L
        return if (inPeriodMs > lenMs) fmtMinutes(baseMs + lenMs) to "+" + fmtClock(inPeriodMs - lenMs)
        else fmtMinutes(baseMs + inPeriodMs) to null
    }

    /** Minutes can pass 60 in football ("90:00"), so no hours. */
    private fun fmtMinutes(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }
}
