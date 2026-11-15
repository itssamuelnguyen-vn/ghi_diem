package com.samuelcruz.ghidiem

import com.samuelcruz.ghidiem.core.Board
import com.samuelcruz.ghidiem.core.Ev
import com.samuelcruz.ghidiem.core.FinalSet
import com.samuelcruz.ghidiem.core.FootballClock
import com.samuelcruz.ghidiem.core.Kind
import com.samuelcruz.ghidiem.core.Rules
import com.samuelcruz.ghidiem.core.Scoring
import com.samuelcruz.ghidiem.core.Setup
import com.samuelcruz.ghidiem.core.Sport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {
    /** "AABAB" → points for A (0) and B (1). */
    private fun pts(s: String) = s.map { Ev(if (it == 'A') 0 else 1, Kind.POINT, 0) }
    private fun rep(n: Int, c: Char) = c.toString().repeat(n)
    private fun play(sport: Sport, s: String, rules: Rules = sport.defaultRules(), first: Int = 0): Board =
        Scoring.replay(Setup(sport, rules, first), pts(s))

    // ---- Badminton

    @Test fun badmintonGameTo21AndServer() {
        val b = play(Sport.BADMINTON, rep(20, 'A') + "B")
        assertEquals(listOf("20", "1"), b.main)
        assertEquals(1, b.server) // rally winner serves
        assertEquals("Giao ô trái", b.serveHint) // 1 is odd
        assertEquals("Game point", b.callouts.single().text)
        val g = play(Sport.BADMINTON, rep(21, 'A'))
        assertEquals(listOf(1, 0), g.setsWon)
        assertEquals("21-0", g.history.single().text())
        assertEquals(0, g.server) // winner of the game serves first
    }

    @Test fun badmintonDeuceAndCap() {
        // 20-20 → needs 2 clear; 29-29 → next point wins (30 cap).
        val deuce = play(Sport.BADMINTON, "AB".repeat(20) + "A")
        assertEquals(listOf("21", "20"), deuce.main)
        assertEquals(0, deuce.history.size)
        val cap = play(Sport.BADMINTON, "AB".repeat(29) + "B")
        assertEquals("29-30", cap.history.single().text())
    }

    @Test fun badmintonIntervalAt11AndMatch() {
        val b = play(Sport.BADMINTON, rep(11, 'A'))
        assertEquals("Nghỉ 60 giây", b.alert)
        val m = play(Sport.BADMINTON, rep(21, 'A') + rep(21, 'B') + rep(11, 'A'))
        assertEquals("Nghỉ 60 giây · Đổi sân", m.alert) // deciding game
        val done = play(Sport.BADMINTON, rep(21, 'A') + rep(21, 'A'))
        assertTrue(done.finished)
        assertEquals(0, done.winner)
        assertEquals("2-0", done.score)
        assertEquals(-1, done.server)
        // Points after the end are ignored.
        assertEquals("2-0", play(Sport.BADMINTON, rep(21, 'A') + rep(21, 'A') + "BBB").score)
    }

    @Test fun oneGameMatchIsTheDecidingGame() {
        val r = Sport.BADMINTON.defaultRules().copy(bestOf = 1)
        assertEquals("Nghỉ 60 giây · Đổi sân", play(Sport.BADMINTON, rep(11, 'A'), r).alert)
        assertTrue(play(Sport.BADMINTON, rep(21, 'A'), r).finished)
        // Volleyball 1 set: played to 25, not to the deciding 15.
        val v = Sport.VOLLEYBALL.defaultRules().copy(bestOf = 1)
        assertFalse(play(Sport.VOLLEYBALL, rep(15, 'A'), v).finished)
        assertTrue(play(Sport.VOLLEYBALL, rep(25, 'A'), v).finished)
    }

    @Test fun matchPointCallout() {
        val b = play(Sport.BADMINTON, rep(21, 'A') + rep(20, 'A'))
        assertEquals("Match point", b.callouts.single().text)
        assertEquals(0, b.callouts.single().side)
    }

    // ---- Volleyball

    @Test fun volleyballSetsTo25AndDecidingTo15() {
        val r = Sport.VOLLEYBALL.defaultRules()
        val four = rep(25, 'A') + rep(25, 'B') + rep(25, 'A') + rep(25, 'B')
        val b = play(Sport.VOLLEYBALL, four + rep(8, 'A'))
        assertEquals("Đổi sân", b.alert) // at 8 in the deciding set
        assertEquals(5, b.setNo)
        val done = play(Sport.VOLLEYBALL, four + rep(15, 'B'), r)
        assertTrue(done.finished)
        assertEquals(1, done.winner)
        assertEquals("2-3", done.score)
    }

    // ---- Table tennis

    @Test fun tableTennisServiceEveryTwoThenEveryPoint() {
        assertEquals(0, play(Sport.TABLE_TENNIS, "").server)
        assertEquals(0, play(Sport.TABLE_TENNIS, "A").server)
        assertEquals(1, play(Sport.TABLE_TENNIS, "AB").server)
        assertEquals(1, play(Sport.TABLE_TENNIS, "ABA").server)
        assertEquals(0, play(Sport.TABLE_TENNIS, "ABAB").server)
        val ten = "AB".repeat(10) // 10-10, 20 points → back to the first server
        assertEquals(0, play(Sport.TABLE_TENNIS, ten).server)
        assertEquals(1, play(Sport.TABLE_TENNIS, ten + "A").server) // every point now
        assertEquals(0, play(Sport.TABLE_TENNIS, ten + "AB").server)
        // Game 2: the other player serves first.
        assertEquals(1, play(Sport.TABLE_TENNIS, rep(11, 'A')).server)
        assertEquals("11-0", play(Sport.TABLE_TENNIS, rep(11, 'A')).history.single().text())
    }

    // ---- Tennis

    @Test fun tennisPointsAndDeuce() {
        assertEquals(listOf("15", "0"), play(Sport.TENNIS, "A").main)
        assertEquals(listOf("40", "30"), play(Sport.TENNIS, "AAABB").main)
        val deuce = play(Sport.TENNIS, "AAABBB")
        assertEquals(listOf("40", "40"), deuce.main)
        assertEquals("Deuce", deuce.info)
        assertEquals(listOf("AD", "40"), play(Sport.TENNIS, "AAABBBA").main)
        assertEquals("Deuce", play(Sport.TENNIS, "AAABBBAB").info)
        val game = play(Sport.TENNIS, "AAABBBAA")
        assertEquals(listOf(1, 0), game.games)
        assertEquals(1, game.server)
        assertEquals("Đổi sân", game.alert) // after game 1
    }

    @Test fun tennisBreakPointAndNoAd() {
        val b = play(Sport.TENNIS, "BBB") // A serves, 0-40
        assertEquals("Break point", b.callouts.single().text)
        assertEquals(1, b.callouts.single().side)
        val r = Sport.TENNIS.defaultRules().copy(noAd = true)
        assertEquals("Điểm quyết định", play(Sport.TENNIS, "AAABBB", r).info)
        assertEquals(listOf(0, 1), play(Sport.TENNIS, "AAABBBB", r).games)
    }

    @Test fun tennisTiebreakAndSet() {
        val game = "AAAA"
        val gameB = "BBBB"
        // 6-6 by alternating games, then a 7-0 tie-break for A.
        val sixAll = (game + gameB).repeat(6)
        val tb = play(Sport.TENNIS, sixAll)
        assertEquals("Tie-break", tb.info)
        assertEquals(listOf(6, 6), tb.games)
        val set = play(Sport.TENNIS, sixAll + rep(7, 'A'))
        assertEquals("7-6(0)", set.history.single().text())
        assertEquals(listOf(1, 0), set.setsWon)
        // 6-0 set.
        assertEquals("6-0", play(Sport.TENNIS, game.repeat(6)).history.single().text())
    }

    @Test fun tennisTiebreakServerAndEnds() {
        val sixAll = ("AAAA" + "BBBB").repeat(6) // 12 games, A served first → A serves the tie-break
        assertEquals(0, play(Sport.TENNIS, sixAll).server)
        assertEquals(1, play(Sport.TENNIS, sixAll + "A").server)
        assertEquals(1, play(Sport.TENNIS, sixAll + "AB").server)
        assertEquals(0, play(Sport.TENNIS, sixAll + "ABA").server)
        assertEquals("Đổi sân", play(Sport.TENNIS, sixAll + "ABABAB").alert) // every 6 points
    }

    @Test fun tennisSuperTiebreakDecider() {
        val r = Sport.TENNIS.defaultRules().copy(finalSet = FinalSet.SUPER_TIEBREAK)
        val setA = "AAAA".repeat(6)
        val setB = "BBBB".repeat(6)
        val b = play(Sport.TENNIS, setA + setB, r)
        assertEquals("Super tie-break", b.info)
        val done = play(Sport.TENNIS, setA + setB + rep(10, 'B'), r)
        assertTrue(done.finished)
        assertEquals("[0-10]", done.history.last().text())
        assertEquals(1, done.winner)
    }

    @Test fun tennisAdvantageFinalSetHasNoTiebreak() {
        val r = Sport.TENNIS.defaultRules().copy(finalSet = FinalSet.ADVANTAGE)
        val setA = "AAAA".repeat(6)
        val setB = "BBBB".repeat(6)
        val b = play(Sport.TENNIS, setA + setB + ("AAAA" + "BBBB").repeat(6), r)
        assertNull(b.info)
        assertEquals(listOf(6, 6), b.games)
        val done = play(Sport.TENNIS, setA + setB + ("AAAA" + "BBBB").repeat(6) + "AAAA" + "AAAA", r)
        assertEquals("8-6", done.history.last().text())
        assertTrue(done.finished)
    }

    // ---- Football

    @Test fun footballGoalsAndMinutes() {
        val r = Rules(periodMinutes = 45, periods = 2)
        val min = 60_000L
        val evs = listOf(
            Ev(-1, Kind.PERIOD_START, 0),
            Ev(0, Kind.GOAL, 22 * min + 5_000), // 23'
            Ev(1, Kind.YELLOW, 30 * min),
            Ev(1, Kind.GOAL, 46 * min + 30_000), // 45+2'
            Ev(-1, Kind.PERIOD_END, 47 * min),
            Ev(-1, Kind.PERIOD_START, 47 * min),
            Ev(0, Kind.GOAL, 47 * min + 10 * min), // 56'
        )
        val b = Scoring.replay(Setup(Sport.FOOTBALL, r), evs)
        assertEquals(listOf("2", "1"), b.main)
        assertEquals("23'", b.lines[1].time)
        assertEquals("45+2'", b.lines[3].time)
        assertEquals("45'", b.lines[5].time)
        assertEquals("56'", b.lines[6].time)
        assertEquals(listOf(1, 0), b.cards[1])
        assertEquals(2, b.period)
        assertTrue(b.periodOpen)
        assertEquals(0, b.winner)
        assertFalse(b.finished)
        assertEquals("45:30" to null, FootballClock.display(r, 1, 30_000))
        assertEquals("45:00" to "+01:00", FootballClock.display(r, 0, 46 * min))
    }
}
