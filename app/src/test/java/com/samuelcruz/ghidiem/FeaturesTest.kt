package com.samuelcruz.ghidiem

import com.samuelcruz.ghidiem.core.Ev
import com.samuelcruz.ghidiem.core.Fixtures
import com.samuelcruz.ghidiem.core.Kind
import com.samuelcruz.ghidiem.core.Result
import com.samuelcruz.ghidiem.core.Scoring
import com.samuelcruz.ghidiem.core.Setup
import com.samuelcruz.ghidiem.core.Sport
import com.samuelcruz.ghidiem.core.Standings
import com.samuelcruz.ghidiem.core.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {
    private fun pts(s: String) = s.map { Ev(if (it == 'A') 0 else 1, Kind.POINT, 0) }
    private fun doubles(sport: Sport, s: String) = Scoring.replay(Setup(sport, sport.defaultRules(), 0, doubles = true), pts(s))

    // ---- Doubles

    @Test fun badmintonDoublesCourts() {
        // 0-0: A's player 1 serves from the right to B's player 1.
        var b = doubles(Sport.BADMINTON, "")
        assertEquals(0, b.server); assertEquals(0, b.serverPlayer); assertEquals(0, b.receiverPlayer)
        // A wins: A1 and A2 swap, A1 serves again from the left (score 1) to B's left-court player (B2).
        b = doubles(Sport.BADMINTON, "A")
        assertEquals(0, b.serverPlayer); assertEquals(1, b.receiverPlayer); assertEquals("Giao ô trái", b.serveHint)
        // B wins: service to B, score 1 (odd) → B's left-court player = B2 (B never moved).
        b = doubles(Sport.BADMINTON, "AB")
        assertEquals(1, b.server); assertEquals(1, b.serverPlayer)
        // A wins it back at 2-1 (even): A's right-court player is A2 now (they swapped once).
        b = doubles(Sport.BADMINTON, "ABA")
        assertEquals(0, b.server); assertEquals(1, b.serverPlayer)
    }

    @Test fun tableTennisDoublesOrder() {
        // A1 → B1 → A2 → B2 → A1, two serves each.
        val order = listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1, 0 to 0)
        order.forEachIndexed { turn, (side, player) ->
            val b = doubles(Sport.TABLE_TENNIS, "AB".repeat(turn)) // 2 points per turn, level score
            assertEquals("turn $turn side", side, b.server)
            assertEquals("turn $turn player", player, b.serverPlayer)
        }
        // A1 serves to B1.
        assertEquals(0, doubles(Sport.TABLE_TENNIS, "").receiverPlayer)
    }

    @Test fun tennisDoublesRotation() {
        // Games: A1, B1, A2, B2, A1…
        val expected = listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1, 0 to 0)
        expected.forEachIndexed { game, (side, player) ->
            val b = doubles(Sport.TENNIS, "AAAA".repeat(game))
            assertEquals(side, b.server)
            assertEquals(player, b.serverPlayer)
        }
        assertEquals(0, doubles(Sport.TENNIS, "").receiverPlayer) // deuce court
        assertEquals(1, doubles(Sport.TENNIS, "A").receiverPlayer) // ad court
    }

    @Test fun singlesHaveNoPlayers() {
        assertEquals(-1, Scoring.replay(Setup(Sport.BADMINTON, Sport.BADMINTON.defaultRules()), pts("A")).serverPlayer)
    }

    // ---- Time-outs

    @Test fun volleyballTimeoutsPerSet() {
        val r = Sport.VOLLEYBALL.defaultRules()
        val t = Ev(0, Kind.TIMEOUT, 0)
        var b = Scoring.replay(Setup(Sport.VOLLEYBALL, r), listOf(t, t, t))
        assertEquals(listOf(2, 0), b.timeouts)
        assertFalse(b.lines[2].counted) // the third is over the limit
        // A new set gives two more.
        b = Scoring.replay(Setup(Sport.VOLLEYBALL, r), listOf(t, t) + pts("A".repeat(25)))
        assertEquals(listOf(0, 0), b.timeouts)
        assertEquals(listOf("0", "0"), b.main) // time-outs are not points
    }

    // ---- Football extras

    private val min = 60_000L

    @Test fun ownGoalAndShootout() {
        val s = Setup(Sport.FOOTBALL, Sport.FOOTBALL.defaultRules())
        val base = listOf(
            Ev(-1, Kind.PERIOD_START, 0),
            Ev(1, Kind.OWN_GOAL, 10 * min, "Nam"), // B gets it
            Ev(0, Kind.GOAL, 20 * min),
            Ev(-1, Kind.PERIOD_END, 45 * min),
            Ev(0, Kind.SHOOTOUT_START, 45 * min),
        )
        var b = Scoring.replay(s, base)
        assertEquals(listOf("1", "1"), b.main)
        assertEquals(-1, b.winner)
        assertEquals(0, b.shootoutNext)
        assertFalse(b.finished)
        // A scores 3, B misses 3: decided after A's 3rd and B's 3rd (B cannot catch up: 0 + 2 < 3).
        val kicks = listOf(
            Ev(0, Kind.PEN_SCORED, 0), Ev(1, Kind.PEN_MISSED, 0),
            Ev(0, Kind.PEN_SCORED, 0), Ev(1, Kind.PEN_MISSED, 0),
            Ev(0, Kind.PEN_SCORED, 0), Ev(1, Kind.PEN_MISSED, 0),
        )
        b = Scoring.replay(s, base + kicks)
        assertTrue(b.finished)
        assertEquals(0, b.winner)
        assertEquals("3-0", b.shootoutScore)
        assertEquals("Luân lưu 3-0", b.detail)
        assertEquals("1-1", b.score)
        // A kick after it is decided does not count.
        b = Scoring.replay(s, base + kicks + Ev(0, Kind.PEN_SCORED, 0))
        assertFalse(b.lines.last().counted)
    }

    @Test fun shootoutSuddenDeath() {
        val s = Setup(Sport.FOOTBALL, Sport.FOOTBALL.defaultRules())
        val five = (1..5).flatMap { listOf(Ev(0, Kind.PEN_SCORED, 0), Ev(1, Kind.PEN_SCORED, 0)) }
        val base = listOf(Ev(-1, Kind.PERIOD_START, 0), Ev(-1, Kind.PERIOD_END, 0), Ev(1, Kind.SHOOTOUT_START, 0))
        var b = Scoring.replay(s, base + five)
        assertFalse(b.finished)
        assertEquals(1, b.shootoutNext) // B kicked first
        b = Scoring.replay(s, base + five + Ev(1, Kind.PEN_MISSED, 0) + Ev(0, Kind.PEN_SCORED, 0))
        assertTrue(b.finished)
        assertEquals(0, b.winner)
    }

    // ---- Statistics

    @Test fun statsServeRunLead() {
        val s = Setup(Sport.BADMINTON, Sport.BADMINTON.defaultRules())
        val evs = pts("AAABBA")
        val b = Scoring.replay(s, evs)
        val st = Stats.of(s, evs, b, 60_000)
        assertEquals(4, st.sides[0].points)
        assertEquals(3, st.sides[0].longestRun)
        assertEquals(3, st.sides[0].biggestLead)
        // A served points 1-4 (won 3), B served 5-6 (won 1 of 2).
        assertEquals(4, st.sides[0].servePlayed)
        assertEquals(3, st.sides[0].serveWon)
        assertEquals(2, st.sides[1].servePlayed)
        assertEquals(1, st.sides[0].receiveWon)
        assertEquals(listOf(0, 1, 2, 3, 2, 1, 2), st.charts.single().diffs)
        assertEquals(10_000L, st.avgPointMs)
    }

    // ---- Tournaments

    @Test fun roundRobinEveryPairOnce() {
        for (n in 2..7) {
            val f = Fixtures.roundRobin(n)
            assertEquals(n * (n - 1) / 2, f.size)
            assertEquals(f.size, f.map { it.key }.toSet().size)
            // Nobody twice in the same round.
            f.groupBy { it.round }.values.forEach { round ->
                val ids = round.flatMap { listOf(it.a, it.b) }
                assertEquals(ids.size, ids.toSet().size)
            }
        }
    }

    @Test fun knockoutWithByes() {
        assertEquals(listOf(0, 7, 3, 4, 1, 6, 2, 5), Fixtures.seedOrder(8))
        // 5 entrants → bracket of 8, three byes for seeds 0, 1, 2.
        val r = Fixtures.knockout(5) { null }
        assertEquals(3, r.size)
        assertEquals(4, r[0].size)
        assertEquals(0, r[0][0].a); assertEquals(-2, r[0][0].b)
        assertEquals(3, r[0][1].a); assertEquals(4, r[0][1].b)
        // Round 2: seed 0 waits for the winner of 3 v 4.
        assertEquals(0, r[1][0].a); assertEquals(-1, r[1][0].b)
        val played = Fixtures.knockout(5) { key -> if (key == "ko:0:1") 4 else null }
        assertEquals(4, played[1][0].b)
    }

    @Test fun standingsFootballPoints() {
        val names = listOf("X", "Y", "Z")
        val res = listOf(
            Result(Sport.FOOTBALL, "X", "Y", 0, 2, 0),
            Result(Sport.FOOTBALL, "Y", "Z", -1, 1, 1),
            Result(Sport.FOOTBALL, "Z", "X", 0, 3, 1),
        )
        val t = Standings.table(Sport.FOOTBALL, names, res)
        // X: 3 pts GD 0 (GF 3); Z: 4 pts; Y: 1 pt.
        assertEquals(listOf("Z", "X", "Y"), t.map { it.name })
        assertEquals(4, t[0].points(Sport.FOOTBALL))
        assertEquals(1, t[2].drawn)
    }

    @Test fun playerRecordsSplitPairs() {
        val res = listOf(Result(Sport.BADMINTON, "Nam / Hùng", "Lan", 0, 2, 0))
        val p = Standings.players(res).associateBy { it.name }
        assertEquals(1, p["Nam"]?.won)
        assertEquals(1, p["Nam / Hùng"]?.won)
        assertEquals(1, p["Lan"]?.lost)
        assertNull(p["Hùng "])
    }
}
