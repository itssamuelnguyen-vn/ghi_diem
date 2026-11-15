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
    /** Side serving when the point was played (-1 = not a point / no serve). For statistics. */
    val serverBefore: Int = -1,
    /** A minus B after the event: points in the set (rally), points in the match (tennis), goals (football). */
    val diff: Int = 0,
    /** The event counted (false: a point after the end, a time-out over the limit…). */
    val counted: Boolean = true,
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
    /** Doubles: which player of the serving / receiving side (0 or 1, -1 = singles). */
    val serverPlayer: Int = -1,
    val receiverPlayer: Int = -1,
    val callouts: List<Callout> = emptyList(),
    /** "Deuce", "Tie-break"… */
    val info: String? = null,
    val finished: Boolean = false,
    val winner: Int = -1,
    val lines: List<Line> = emptyList(),
    /** Short result for lists: "2-1" or goals. */
    val score: String = "0-0",
    /** Set scores for lists: "21-19 · 18-21 · 21-15", football "Luân lưu 4-3". */
    val detail: String = "",
    /** Time-outs used in the current set (volleyball) or match (table tennis). */
    val timeouts: List<Int> = listOf(0, 0),
    // Football
    val cards: List<List<Int>> = listOf(listOf(0, 0), listOf(0, 0)),
    /** Number of periods started. */
    val period: Int = 0,
    val periodOpen: Boolean = false,
    /** Match clock when each started period began. */
    val periodStarts: List<Long> = emptyList(),
    /** Penalty shoot-out kicks of each side (true = scored), null = no shoot-out. */
    val shootout: List<List<Boolean>>? = null,
    /** Side to kick next in the shoot-out. */
    val shootoutNext: Int = -1,
    /** Football: players of each side still on the pitch (rules' players minus those sent off). */
    val onField: List<Int> = listOf(0, 0),
    /** Football: names sent off (red card, or second yellow), per side; "?" for a red card without a name. */
    val sentOff: List<List<String>> = listOf(emptyList(), emptyList()),
    /** Football: names with one yellow card who are still on the pitch (a second one sends them off). */
    val booked: List<List<String>> = listOf(emptyList(), emptyList()),
) {
    /** Alert of the last event (shown as a banner until the next event). */
    val alert get() = lines.lastOrNull()?.alert

    val shootoutScore get() = shootout?.let { s -> "${s[0].count { it }}-${s[1].count { it }}" }
}

data class Setup(val sport: Sport, val rules: Rules, val firstServer: Int = 0, val doubles: Boolean = false)

object Scoring {
    fun replay(setup: Setup, events: List<Ev>): Board = when (setup.sport) {
        Sport.FOOTBALL -> Football(setup.rules, setup.firstServer).run(events)
        Sport.TENNIS -> Tennis(setup.rules, setup.firstServer, setup.doubles).run(events)
        else -> Rally(setup.sport, setup.rules, setup.firstServer, setup.doubles).run(events)
    }
}

/**
 * Doubles service order by "turns": each time a side starts serving, its next player in turn serves
 * (A1, B1, A2, B2…). Used by table tennis and tennis; badminton follows the players' courts instead.
 */
private class Turns(first: Int) {
    val served = intArrayOf(0, 0)
    var player = 0
        private set

    init {
        start(first)
    }

    fun start(side: Int) {
        player = served[side] % 2
        served[side]++
    }

    /** The receiving side's player who serves next for that side. */
    fun next(side: Int) = served[side] % 2

    fun copy() = Turns(0).also { it.served[0] = served[0]; it.served[1] = served[1]; it.player = player }
}

/** Badminton, volleyball, table tennis: rally scoring, sets to [Rules.points] (win by 2, optional cap). */
internal class Rally(
    private val sport: Sport,
    private val r: Rules,
    private val firstServer: Int,
    private val doubles: Boolean,
) {
    var cur = intArrayOf(0, 0)
    var won = intArrayOf(0, 0)
    val sets = mutableListOf<SetScore>()
    var server = firstServer
    var finished = false
    var winner = -1
    var timeouts = intArrayOf(0, 0)
    /** Who served first in the current set (table tennis service order). */
    private var setFirst = firstServer
    /** The mid-set break (badminton 11, deciding-set change of ends) already happened in this set. */
    private var midDone = false
    /** Badminton doubles: player standing in the right court [0] and left court [1] of each side. */
    private var courts = arrayOf(intArrayOf(0, 1), intArrayOf(0, 1))
    private var turns = Turns(firstServer)

    private val setIndex get() = sets.size
    /** The last possible set (also the only set of a 1-set match): change of ends in the middle. */
    private val deciding get() = setIndex == r.bestOf - 1
    /** Volleyball's shorter deciding set only exists in matches of several sets. */
    private val target get() = if (deciding && r.bestOf > 1) r.decidingPoints else r.points
    private val mid get() = if (sport == Sport.TABLE_TENNIS) target / 2 else (target + 1) / 2

    private fun clone() = Rally(sport, r, firstServer, doubles).also {
        it.cur = cur.clone(); it.won = won.clone(); it.sets.addAll(sets); it.server = server
        it.finished = finished; it.winner = winner; it.setFirst = setFirst; it.midDone = midDone
        it.timeouts = timeouts.clone(); it.courts = arrayOf(courts[0].clone(), courts[1].clone()); it.turns = turns.copy()
    }

    private fun serveFrom(s: Int) {
        if (s != server) turns.start(s)
        server = s
    }

    /** Adds a point; returns the timeline line. */
    fun point(side: Int, clockMs: Long, time: String): Line {
        val setNo = setIndex + 1
        if (finished) return Line("", "", false, time, setNo, counted = false)
        val before = server
        val o = 1 - side
        cur[side]++
        val t = target
        val setWon = (cur[side] >= t && cur[side] - cur[o] >= 2) || (r.cap > 0 && cur[side] >= r.cap)
        if (setWon) {
            sets += SetScore(cur[0], cur[1], endMs = clockMs)
            won[side]++
            val score = "${cur[0]}-${cur[1]}"
            val diff = cur[0] - cur[1]
            if (won[side] > r.bestOf / 2) {
                finished = true
                winner = side
                server = -1
                return Line(score, "Thắng trận", true, time, setNo, serverBefore = before, diff = diff)
            }
            cur = intArrayOf(0, 0)
            midDone = false
            if (sport.timeoutPerSet) timeouts = intArrayOf(0, 0)
            // Badminton: the winner of a game serves first in the next; volleyball / table tennis alternate.
            setFirst = if (sport == Sport.BADMINTON) side else firstServer xor (setIndex % 2)
            server = setFirst
            turns.start(setFirst) // a new game is always a new service turn
            val rest = if (sport == Sport.VOLLEYBALL) "" else " · Nghỉ giữa ${sport.setWord.lowercase()}"
            return Line(score, "Thắng ${sport.setWord.lowercase()} $setNo", true, time, setNo, "Đổi sân$rest",
                serverBefore = before, diff = diff)
        }
        if (sport == Sport.BADMINTON && side == server) {
            // The serving pair won the rally: its players swap courts, the same player serves again.
            courts[side] = intArrayOf(courts[side][1], courts[side][0])
        }
        serveFrom(if (sport == Sport.TABLE_TENNIS) tableTennisServer() else side)
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
        return Line("${cur[0]}-${cur[1]}", "", false, time, setNo, alert, serverBefore = before, diff = cur[0] - cur[1])
    }

    fun timeout(side: Int, time: String): Line {
        val setNo = setIndex + 1
        if (finished || timeouts[side] >= sport.timeouts) return Line("", "Hội ý", false, time, setNo, counted = false)
        timeouts[side]++
        return Line("", "Hội ý ${timeouts[side]}/${sport.timeouts}", true, time, setNo, diff = cur[0] - cur[1])
    }

    /** Service changes every 2 points, every point from (points-1)-all. */
    private fun tableTennisServer(): Int {
        val deuce = r.points - 1
        val total = cur[0] + cur[1]
        val changes = if (cur[0] >= deuce && cur[1] >= deuce) deuce + (total - 2 * deuce) else total / 2
        return setFirst xor (changes % 2)
    }

    fun run(events: List<Ev>): Board {
        val lines = events.map {
            val time = fmtClock(it.clockMs)
            when (it.kind) {
                Kind.POINT -> point(it.side, it.clockMs, time)
                Kind.TIMEOUT -> timeout(it.side, time)
                else -> Line("", "", false, time, setIndex + 1, counted = false)
            }
        }
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
        // Doubles: badminton by courts (the player in the court of the server's score), others by turns.
        val (sp, rp) = when {
            !doubles || server < 0 -> -1 to -1
            sport == Sport.BADMINTON -> courts[server][cur[server] % 2] to courts[1 - server][cur[server] % 2]
            sport == Sport.TABLE_TENNIS -> turns.player to turns.next(1 - server)
            else -> -1 to -1
        }
        val detail = (sets.map { it.text() } + if (!finished && cur.sum() > 0) listOf("${cur[0]}-${cur[1]}") else emptyList())
            .joinToString(" · ")
        return Board(
            main = listOf(cur[0].toString(), cur[1].toString()),
            setsWon = won.toList(),
            history = sets.toList(),
            setNo = if (finished) sets.size else sets.size + 1,
            server = server,
            serveHint = hint,
            serverPlayer = sp,
            receiverPlayer = rp,
            callouts = callouts,
            info = if (!finished && deciding && r.bestOf > 1) "${sport.setWord} quyết định" else null,
            finished = finished,
            winner = winner,
            lines = lines,
            score = "${won[0]}-${won[1]}",
            detail = detail,
            timeouts = timeouts.toList(),
        )
    }
}

/** Tennis: 15/30/40, deuce or no-ad, sets to [Rules.games] with a 7-point tie-break, final-set options. */
internal class Tennis(private val r: Rules, firstServer: Int, private val doubles: Boolean) {
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
    /** Points won in the whole match, for the chart. */
    private var total = intArrayOf(0, 0)
    private var turns = Turns(firstServer)

    private val finalSet get() = won[0] == r.bestOf / 2 && won[1] == r.bestOf / 2

    init {
        startSet()
    }

    private fun clone() = Tennis(r, server, doubles).also {
        it.p = p.clone(); it.g = g.clone(); it.won = won.clone(); it.sets.addAll(sets); it.server = server
        it.tb = tb; it.superTb = superTb; it.tbTarget = tbTarget; it.tbFirst = tbFirst
        it.finished = finished; it.winner = winner; it.total = total.clone(); it.turns = turns.copy()
    }

    /** Every new game (and every change of server in a tie-break) is a new service turn. */
    private fun serveFrom(s: Int) {
        server = s
        turns.start(s)
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
        if (finished) return Line("", "", false, time, setNo, counted = false)
        val before = server
        total[side]++
        val diff = total[0] - total[1]
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
                serveFrom(1 - tbFirst)
                tb = false; superTb = false
                return endSet(side, set, time, setNo, before, diff)
            }
            val n = p[0] + p[1]
            val next = tbFirst xor (((n + 1) / 2) % 2)
            if (next != server) serveFrom(next)
            return Line(current(), "", false, time, setNo, if (n % 6 == 0) "Đổi sân" else null, before, diff)
        }
        p[side]++
        if (!r.noAd && p[0] >= 4 && p[1] >= 4) p = intArrayOf(3, 3) // back to deuce
        val gameWon = p[side] >= 4 && (r.noAd || p[side] - p[o] >= 2)
        if (!gameWon) return Line(current(), "", false, time, setNo, serverBefore = before, diff = diff)
        val broke = side != server
        g[side]++
        p = intArrayOf(0, 0)
        serveFrom(1 - server)
        val n = r.games
        if (g[side] >= n && g[side] - g[o] >= 2) {
            val set = SetScore(g[0], g[1], endMs = clockMs)
            sets += set
            return endSet(side, set, time, setNo, before, diff)
        }
        val label = if (broke) "Break" else "Game"
        if (g[0] == n && g[1] == n && !(finalSet && r.finalSet == FinalSet.ADVANTAGE)) {
            tb = true; tbTarget = 7; tbFirst = server
            return Line(gameScore(), label, true, time, setNo, "Tie-break", before, diff)
        }
        return Line(gameScore(), label, true, time, setNo, if ((g[0] + g[1]) % 2 == 1) "Đổi sân" else null, before, diff)
    }

    private fun endSet(side: Int, set: SetScore, time: String, setNo: Int, before: Int, diff: Int): Line {
        won[side]++
        if (won[side] > r.bestOf / 2) {
            finished = true
            winner = side
            server = -1
            return Line(set.text(), "Thắng trận", true, time, setNo, serverBefore = before, diff = diff)
        }
        val odd = !set.superTb && (set.a + set.b) % 2 == 1
        startSet()
        return Line(set.text(), "Thắng set $setNo", true, time, setNo,
            if (odd) "Đổi sân · Nghỉ giữa set" else "Nghỉ giữa set", before, diff)
    }

    fun run(events: List<Ev>): Board {
        val lines = events.map {
            val time = fmtClock(it.clockMs)
            if (it.kind == Kind.POINT) point(it.side, it.clockMs, time)
            else Line("", "", false, time, sets.size + 1, counted = false)
        }
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
        val deuceCourt = (p[0] + p[1]) % 2 == 0
        val hint = if (server >= 0) (if (deuceCourt) "Giao bên phải" else "Giao bên trái") else null
        val playing = !finished && (g.sum() > 0 || p.sum() > 0)
        return Board(
            main = listOf(pointText(0), pointText(1)),
            setsWon = won.toList(),
            games = g.toList(),
            history = sets.toList(),
            setNo = if (finished) sets.size else sets.size + 1,
            server = server,
            serveHint = hint,
            // Doubles: the receiving pair keeps courts for the whole set: player 1 right, player 2 left.
            serverPlayer = if (doubles && server >= 0) turns.player else -1,
            receiverPlayer = if (doubles && server >= 0) (if (deuceCourt) 0 else 1) else -1,
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

/**
 * Football: goals (and own goals), cards, substitutions; the clock runs per period (hiệp), shown as the match
 * minute; an optional penalty shoot-out decides a draw (5 kicks each, then sudden death).
 */
internal class Football(private val r: Rules, private val firstKicker: Int) {
    private val goals = intArrayOf(0, 0)
    private val yellow = intArrayOf(0, 0)
    private val red = intArrayOf(0, 0)
    private val starts = mutableListOf<Long>()
    private var ends = 0
    private var pens: Array<MutableList<Boolean>>? = null
    private var penFirst = firstKicker

    private fun score() = "${goals[0]}-${goals[1]}"

    /** Yellow cards per player key (lowercase name) of each side. */
    private val yellows = Array(2) { mutableMapOf<String, Int>() }
    /** The name as typed with the first yellow card (keys are lowercase). */
    private val firstNames = Array(2) { mutableMapOf<String, String>() }
    /** Sent-off players: shown names and their keys. */
    private val off = Array(2) { mutableListOf<String>() }
    private val offKeys = Array(2) { mutableSetOf<String>() }

    private fun sendOff(side: Int, name: String) {
        off[side] += name.ifEmpty { "?" }
        if (name.isNotEmpty()) offKeys[side] += name.lowercase()
    }

    private fun onField(side: Int) = (r.players - off[side].size).coerceAtLeast(0)

    /** "Nam bị đuổi · còn 10 người", plus the 7-player minimum of 11-a-side football. */
    private fun sentOffAlert(side: Int, name: String): String {
        val left = onField(side)
        val who = name.ifEmpty { "1 cầu thủ" }
        val base = "$who bị đuổi · còn $left người"
        return if (r.players == 11 && left < 7) "$base: dưới 7 người, theo luật trận đấu phải dừng" else base
    }

    /** Kicks each side has left in the first five (0 once in sudden death). */
    private fun shootoutWinner(): Int {
        val p = pens ?: return -1
        val k = intArrayOf(p[0].size, p[1].size)
        val s = intArrayOf(p[0].count { it }, p[1].count { it })
        if (k[0] < 5 || k[1] < 5) {
            val left = intArrayOf(5 - min(k[0], 5), 5 - min(k[1], 5))
            if (s[0] > s[1] + left[1]) return 0
            if (s[1] > s[0] + left[0]) return 1
            return -1
        }
        return if (k[0] == k[1] && s[0] != s[1]) (if (s[0] > s[1]) 0 else 1) else -1
    }

    private fun nextKicker(): Int {
        val p = pens ?: return -1
        return if (p[0].size == p[1].size) penFirst else if (p[0].size < p[1].size) 0 else 1
    }

    fun run(events: List<Ev>): Board {
        val lines = events.map { e ->
            val k = starts.size - 1
            val minute = FootballClock.minute(r, starts, e.clockMs)
            when (e.kind) {
                Kind.PERIOD_START -> {
                    starts += e.clockMs
                    Line(score(), "Bắt đầu ${periodName(r, starts.size)}", true,
                        "${FootballClock.base(r, starts.size - 1)}'", starts.size, diff = goals[0] - goals[1])
                }
                Kind.PERIOD_END -> {
                    ends++
                    Line(score(), "Hết ${periodName(r, starts.size)}", true, minute, starts.size, diff = goals[0] - goals[1])
                }
                Kind.GOAL, Kind.OWN_GOAL -> {
                    goals[e.side]++
                    Line(score(), Kind.title(e.kind), true, minute, k + 1, diff = goals[0] - goals[1])
                }
                Kind.YELLOW -> {
                    yellow[e.side]++
                    val name = e.note.trim()
                    val key = name.lowercase()
                    // Cards are counted per player (by the name or shirt number typed): a second yellow is a red.
                    val n = if (name.isEmpty() || key in offKeys[e.side]) 0 else yellows[e.side].merge(key, 1, Int::plus) ?: 1
                    if (n == 1) firstNames[e.side].putIfAbsent(key, name)
                    if (n >= 2) {
                        val shown = firstNames[e.side][key] ?: name
                        sendOff(e.side, shown)
                        Line("", "Thẻ vàng thứ 2 · bị đuổi", true, minute, k + 1, sentOffAlert(e.side, shown), diff = goals[0] - goals[1])
                    } else Line("", Kind.title(e.kind), true, minute, k + 1, diff = goals[0] - goals[1])
                }
                Kind.RED -> {
                    red[e.side]++
                    val name = e.note.trim()
                    if (name.isNotEmpty() && name.lowercase() in offKeys[e.side]) {
                        Line("", "Thẻ đỏ (đã bị đuổi)", true, minute, k + 1, diff = goals[0] - goals[1])
                    } else {
                        sendOff(e.side, name)
                        Line("", "Thẻ đỏ · bị đuổi", true, minute, k + 1, sentOffAlert(e.side, name), diff = goals[0] - goals[1])
                    }
                }
                Kind.SUB -> Line("", Kind.title(e.kind), true, minute, k + 1, diff = goals[0] - goals[1])
                Kind.SHOOTOUT_START -> {
                    if (pens == null) {
                        pens = arrayOf(mutableListOf(), mutableListOf())
                        penFirst = e.side.coerceIn(0, 1)
                    }
                    Line(score(), "Đá luân lưu", true, "", k + 1, diff = goals[0] - goals[1])
                }
                Kind.PEN_SCORED, Kind.PEN_MISSED -> {
                    val p = pens
                    if (p == null || shootoutWinner() >= 0) Line("", "", false, "", k + 1, counted = false)
                    else {
                        p[e.side] += e.kind == Kind.PEN_SCORED
                        val w = shootoutWinner()
                        Line(shootoutScoreText(), Kind.title(e.kind) + if (w >= 0) " · thắng luân lưu" else "", true,
                            "LL", k + 1, diff = goals[0] - goals[1])
                    }
                }
                else -> Line("", "", false, "", k + 1, counted = false)
            }
        }
        val pw = shootoutWinner()
        val w = when {
            goals[0] > goals[1] -> 0
            goals[1] > goals[0] -> 1
            else -> pw
        }
        return Board(
            main = listOf(goals[0].toString(), goals[1].toString()),
            winner = w,
            finished = pw >= 0,
            lines = lines,
            score = score(),
            detail = pens?.let { "Luân lưu ${shootoutScoreText()}" } ?: "",
            cards = listOf(listOf(yellow[0], red[0]), listOf(yellow[1], red[1])),
            period = starts.size,
            periodOpen = starts.size > ends,
            periodStarts = starts.toList(),
            setNo = max(starts.size, 1),
            shootout = pens?.map { it.toList() },
            shootoutNext = if (pw >= 0) -1 else nextKicker(),
            onField = listOf(onField(0), onField(1)),
            sentOff = off.map { it.toList() },
            booked = (0..1).map { s ->
                yellows[s].filter { (k, n) -> n == 1 && k !in offKeys[s] }.keys.map { k -> firstNames[s][k] ?: k }
            },
        )
    }

    private fun shootoutScoreText() = pens?.let { p -> "${p[0].count { it }}-${p[1].count { it }}" } ?: ""
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
