package com.samuelcruz.ghidiem.core

import kotlin.math.abs

/** Numbers of one side in one match. */
data class SideStats(
    /** Points (rally sports, tennis) or goals (football, own goals included). */
    val points: Int = 0,
    /** Points played while this side served, and how many of them it won. */
    val servePlayed: Int = 0,
    val serveWon: Int = 0,
    /** Points won while the other side served (break points in spirit). */
    val receiveWon: Int = 0,
    /** Most points in a row. */
    val longestRun: Int = 0,
    /** Biggest lead inside a set (rally), in the match (tennis points, goals). */
    val biggestLead: Int = 0,
    val yellow: Int = 0,
    val red: Int = 0,
    val subs: Int = 0,
    val timeouts: Int = 0,
    /** Goal scorers: name → goals (football, from the notes). */
    val scorers: Map<String, Int> = emptyMap(),
) {
    val servePercent get() = if (servePlayed == 0) null else serveWon * 100 / servePlayed
}

/** One line of the score chart: [diffs] = A minus B after each point of that set (tennis: whole match). */
data class Chart(val title: String, val diffs: List<Int>)

data class MatchStats(
    val sides: List<SideStats>,
    val totalPoints: Int,
    val durationMs: Long,
    /** (set name, length in ms). */
    val setDurations: List<Pair<String, Long>>,
    val avgPointMs: Long?,
    val charts: List<Chart>,
)

object Stats {
    fun of(setup: Setup, events: List<Ev>, board: Board, durationMs: Long): MatchStats {
        val sport = setup.sport
        val acc = Array(2) { Acc() }
        var runSide = -1
        var run = 0
        val charts = linkedMapOf<Int, MutableList<Int>>()
        events.forEachIndexed { i, e ->
            val line = board.lines.getOrNull(i) ?: return@forEachIndexed
            if (!line.counted || e.side !in 0..1) return@forEachIndexed
            val a = acc[e.side]
            when (e.kind) {
                Kind.POINT, Kind.GOAL, Kind.OWN_GOAL -> {
                    a.points++
                    if (line.serverBefore >= 0) {
                        acc[line.serverBefore].servePlayed++
                        if (line.serverBefore == e.side) a.serveWon++ else a.receiveWon++
                    }
                    if (runSide == e.side) run++ else { runSide = e.side; run = 1 }
                    if (run > a.longestRun) a.longestRun = run
                    val lead = if (e.side == 0) line.diff else -line.diff
                    if (lead > a.biggestLead) a.biggestLead = lead
                    val key = if (sport == Sport.TENNIS || sport == Sport.FOOTBALL) 0 else line.setNo
                    charts.getOrPut(key) { mutableListOf(0) } += line.diff
                    if (e.kind == Kind.GOAL && e.note.isNotBlank()) a.scorers.merge(e.note.trim(), 1, Int::plus)
                }
                Kind.YELLOW -> a.yellow++
                Kind.RED -> a.red++
                Kind.SUB -> a.subs++
                Kind.TIMEOUT -> a.timeouts++
            }
        }
        val total = acc.sumOf { it.points }
        val pointsOnly = sport != Sport.FOOTBALL
        val setDurations = if (pointsOnly) board.history.mapIndexed { i, s ->
            val prev = board.history.getOrNull(i - 1)?.endMs ?: 0
            "${sport.setWord} ${i + 1}" to (s.endMs - prev)
        } else emptyList()
        return MatchStats(
            sides = acc.map { it.done() },
            totalPoints = total,
            durationMs = durationMs,
            setDurations = setDurations,
            avgPointMs = if (pointsOnly && total > 0 && durationMs > 0) durationMs / total else null,
            charts = charts.map { (k, d) ->
                Chart(if (k == 0) (if (sport == Sport.FOOTBALL) "Hiệu số bàn thắng" else "Chênh lệch điểm cả trận")
                else "${sport.setWord} $k", d)
            },
        )
    }

    /** Largest |diff| of a chart, at least 1 (for scaling). */
    fun range(c: Chart) = c.diffs.maxOfOrNull { abs(it) }?.coerceAtLeast(1) ?: 1

    private class Acc {
        var points = 0; var servePlayed = 0; var serveWon = 0; var receiveWon = 0
        var longestRun = 0; var biggestLead = 0; var yellow = 0; var red = 0; var subs = 0; var timeouts = 0
        val scorers = mutableMapOf<String, Int>()
        fun done() = SideStats(points, servePlayed, serveWon, receiveWon, longestRun, biggestLead, yellow, red, subs,
            timeouts, scorers.toMap())
    }
}

/** A finished match as the standings and player statistics see it. */
data class Result(
    val sport: Sport,
    val nameA: String,
    val nameB: String,
    /** -1 = draw (or stopped without a winner). */
    val winner: Int,
    /** Sets (rally sports, tennis) or goals (football). */
    val forA: Int,
    val forB: Int,
    /** Points inside sets (rally sports), games (tennis), 0 for football. */
    val smallA: Int = 0,
    val smallB: Int = 0,
)

/** One row of a standings table or of the player list. */
data class Record(
    val name: String,
    val played: Int = 0,
    val won: Int = 0,
    val drawn: Int = 0,
    val lost: Int = 0,
    val forBig: Int = 0,
    val againstBig: Int = 0,
    val forSmall: Int = 0,
    val againstSmall: Int = 0,
) {
    /** Football 3 / 1 / 0; other sports 1 per win (ties in the table break on sets, then points). */
    fun points(sport: Sport) = if (sport == Sport.FOOTBALL) won * 3 + drawn else won
    val winPercent get() = if (played == 0) 0 else won * 100 / played

    fun add(r: Result, side: Int): Record {
        val mine = if (side == 0) r.forA else r.forB
        val theirs = if (side == 0) r.forB else r.forA
        val ms = if (side == 0) r.smallA else r.smallB
        val ts = if (side == 0) r.smallB else r.smallA
        return copy(
            played = played + 1,
            won = won + if (r.winner == side) 1 else 0,
            drawn = drawn + if (r.winner < 0) 1 else 0,
            lost = lost + if (r.winner == 1 - side) 1 else 0,
            forBig = forBig + mine, againstBig = againstBig + theirs,
            forSmall = forSmall + ms, againstSmall = againstSmall + ts,
        )
    }
}

object Standings {
    /** Table of [names] from [results] (only results between listed names count), best first. */
    fun table(sport: Sport, names: List<String>, results: List<Result>): List<Record> {
        val rows = names.associateWith { Record(it) }.toMutableMap()
        results.forEach { r ->
            rows[r.nameA]?.let { rows[r.nameA] = it.add(r, 0) }
            rows[r.nameB]?.let { rows[r.nameB] = it.add(r, 1) }
        }
        return rows.values.sortedWith(
            compareByDescending<Record> { it.points(sport) }
                .thenByDescending { it.forBig - it.againstBig }
                .thenByDescending { it.forBig }
                .thenByDescending { it.forSmall - it.againstSmall }
                .thenBy { names.indexOf(it.name) },
        )
    }

    /** Every name (team names, and the players of "A / B" pairs) with its record over [results]. */
    fun players(results: List<Result>): List<Record> {
        val rows = mutableMapOf<String, Record>()
        results.forEach { r ->
            for (side in 0..1) {
                val team = if (side == 0) r.nameA else r.nameB
                val names = (listOf(team) + splitPlayers(team).takeIf { it.size > 1 }.orEmpty()).distinct()
                names.forEach { n -> rows[n] = (rows[n] ?: Record(n)).add(r, side) }
            }
        }
        return rows.values.sortedWith(compareByDescending<Record> { it.played }.thenByDescending { it.won })
    }
}

/** Round-robin and knockout fixtures. Entrants are numbered 0..n-1 in the order they were typed (seeds). */
object Fixtures {
    /** A fixture's key: round-robin "rr:i:j", knockout "ko:round:slot". */
    data class Fixture(val key: String, val round: Int, val a: Int, val b: Int)

    /** Every pair once, arranged in rounds (circle method) so nobody plays twice in a round. */
    fun roundRobin(n: Int): List<Fixture> {
        if (n < 2) return emptyList()
        val ids = (0 until n).toMutableList<Int?>()
        if (n % 2 == 1) ids += null // a bye
        val m = ids.size
        val out = mutableListOf<Fixture>()
        val rot = ids.toMutableList()
        for (round in 0 until m - 1) {
            for (i in 0 until m / 2) {
                val x = rot[i]
                val y = rot[m - 1 - i]
                if (x != null && y != null) {
                    val (a, b) = if (x < y) x to y else y to x
                    out += Fixture("rr:$a:$b", round, a, b)
                }
            }
            // Keep the first fixed, rotate the rest.
            val last = rot.removeAt(m - 1)
            rot.add(1, last)
        }
        return out
    }

    /** Number of knockout rounds for [n] entrants (bracket of the next power of two). */
    fun rounds(n: Int): Int {
        var size = 1
        var r = 0
        while (size < n) { size *= 2; r++ }
        return r
    }

    /**
     * The knockout bracket from the results known so far: [winnerOf] gives the winning entrant of a fixture
     * key (null = not played / no winner yet). Returns all rounds; an unknown entrant is -1, a bye is -2.
     */
    fun knockout(n: Int, winnerOf: (String) -> Int?): List<List<Fixture>> {
        val rounds = rounds(n)
        if (rounds == 0) return emptyList()
        val size = 1 shl rounds
        val seeds = seedOrder(size)
        var slots: List<Int> = seeds.map { if (it < n) it else -2 }
        val out = mutableListOf<List<Fixture>>()
        for (round in 0 until rounds) {
            val fixtures = (0 until slots.size / 2).map { s ->
                Fixture("ko:$round:$s", round, slots[2 * s], slots[2 * s + 1])
            }
            out += fixtures
            slots = fixtures.map { f ->
                when {
                    f.a == -2 && f.b == -2 -> -2
                    f.b == -2 -> f.a
                    f.a == -2 -> f.b
                    f.a < 0 || f.b < 0 -> -1
                    else -> winnerOf(f.key) ?: -1
                }
            }
        }
        return out
    }

    /** Standard seeding: 1 v 8, 4 v 5, 2 v 7, 3 v 6… so top seeds meet last (0-based). */
    fun seedOrder(size: Int): List<Int> {
        var order = listOf(0)
        while (order.size < size) {
            val n = order.size * 2
            order = order.flatMap { listOf(it, n - 1 - it) }
        }
        return order
    }
}
