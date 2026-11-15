package com.samuelcruz.ghidiem.data

import com.samuelcruz.ghidiem.core.Board
import com.samuelcruz.ghidiem.core.Kind
import com.samuelcruz.ghidiem.core.Rules
import com.samuelcruz.ghidiem.core.Scoring
import com.samuelcruz.ghidiem.core.Sport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * All writes (like a Rails service object). Every change to events re-replays the score and refreshes the cache.
 * [onChange] runs after each write (widget refresh, Drive sync later).
 */
class Repo(private val dao: MatchDao, private val onChange: () -> Unit = {}) {
    /** Fast double taps must not interleave read-modify-write of the clock. */
    private val lock = Mutex()
    private fun now() = System.currentTimeMillis()

    private suspend fun board(m: MatchRow): Board = Scoring.replay(m.setup, dao.eventsOnce(m.id).map { it.ev() })

    suspend fun create(
        sport: Sport, rules: Rules, nameA: String, nameB: String, firstServer: Int, doubles: Boolean = false,
        tournamentId: Long = 0, fixture: String = "",
    ): Long = dao.insert(
        MatchRow(sport = sport.name, rules = rules.toJson(), nameA = nameA, nameB = nameB, firstServer = firstServer,
            createdAt = now(), doubles = doubles, tournamentId = tournamentId, fixture = fixture),
    ).also { onChange() }

    /** Same players and rules, fresh score; the loser of the match serves first. Not part of a tournament. */
    suspend fun rematch(id: Long): Long? {
        val m = dao.get(id) ?: return null
        return dao.insert(
            m.copy(id = 0, createdAt = now(), startedAt = 0, finishedAt = 0, clockBaseMs = 0, runningSince = 0,
                score = "0-0", detail = "", winner = -1, manualEnd = false, tournamentId = 0, fixture = "",
                firstServer = if (m.winner >= 0) 1 - m.winner else m.firstServer),
        ).also { onChange() }
    }

    /**
     * Something [side] did: a point (rally sports, tennis), goal, own goal, card, substitution, time-out or
     * shoot-out kick. Points and goals start the clock when the match starts and resume it after a pause.
     * Football goals only count inside a period (the first one opens itself). Returns false when nothing was
     * recorded (with the reason in [lastRefusal]).
     */
    suspend fun score(id: Long, side: Int, kind: Int, note: String = ""): Boolean = lock.withLock {
        var m = dao.get(id) ?: return false
        lastRefusal = ""
        if (m.finished) return refuse("Trận đã kết thúc")
        val t = now()
        val b = board(m)
        val startsClock: Boolean
        when (kind) {
            Kind.POINT -> startsClock = true
            Kind.GOAL, Kind.OWN_GOAL -> {
                if (b.shootout != null) return refuse("Đang đá luân lưu")
                if (!b.periodOpen) {
                    if (b.period > 0) return refuse("Đang nghỉ giữa hiệp: bấm “Bắt đầu hiệp” trước")
                    m = m.startClock(t)
                    dao.insert(EventRow(matchId = id, side = -1, kind = Kind.PERIOD_START, clockMs = m.clockAt(t), at = t))
                }
                startsClock = true
            }
            Kind.YELLOW, Kind.RED, Kind.SUB -> {
                if (b.period == 0) return refuse("Trận chưa bắt đầu")
                startsClock = false
            }
            Kind.TIMEOUT -> {
                if (m.sportEnum.timeouts == 0) return false
                if (b.timeouts[side] >= m.sportEnum.timeouts) return refuse("${m.name(side)} đã hết lượt hội ý")
                startsClock = false
            }
            Kind.PEN_SCORED, Kind.PEN_MISSED -> {
                if (b.shootout == null) return refuse("Chưa bắt đầu đá luân lưu")
                startsClock = false
            }
            else -> return false
        }
        if (startsClock) m = m.startClock(t)
        dao.insert(EventRow(matchId = id, side = side, kind = kind, clockMs = m.clockAt(t), at = t, note = note.trim()))
        refresh(m)
        true
    }

    /** Why the last [score] was refused, for a message. */
    @Volatile var lastRefusal = ""
        private set

    private fun refuse(why: String): Boolean {
        lastRefusal = why
        return false
    }

    /** Football: penalty shoot-out after the last period, [first] kicks first. */
    suspend fun startShootout(id: Long, first: Int) = lock.withLock {
        val m = dao.get(id) ?: return@withLock
        val b = board(m)
        if (m.finished || b.periodOpen || b.period == 0 || b.shootout != null) return@withLock
        val t = now()
        dao.insert(EventRow(matchId = id, side = first, kind = Kind.SHOOTOUT_START, clockMs = m.clockAt(t), at = t))
        refresh(m)
    }

    /** Takes back the last point / goal / card / kick. */
    suspend fun undo(id: Long) = lock.withLock {
        val last = dao.eventsOnce(id).lastOrNull { Kind.undoable(it.kind) } ?: return@withLock
        dao.delete(last)
        refresh(dao.get(id) ?: return@withLock)
    }

    /**
     * Takes back the last point (or goal, own goal counted for it) of [side] only, wherever it is in the match:
     * the long-press on a panel. Returns false when that side has nothing to take back.
     */
    suspend fun undoSide(id: Long, side: Int): Boolean = lock.withLock {
        val m = dao.get(id) ?: return false
        if (m.manualEnd) return false
        val kinds = setOf(Kind.POINT, Kind.GOAL, Kind.OWN_GOAL)
        val last = dao.eventsOnce(id).lastOrNull { it.side == side && it.kind in kinds } ?: return false
        dao.delete(last)
        refresh(m)
        true
    }

    suspend fun deleteEvent(e: EventRow) = lock.withLock {
        dao.delete(e)
        refresh(dao.get(e.matchId) ?: return@withLock)
    }

    suspend fun setNote(e: EventRow, note: String) {
        dao.update(e.copy(note = note.trim()))
        onChange()
    }

    suspend fun toggleClock(id: Long) = lock.withLock {
        val m = dao.get(id) ?: return@withLock
        if (m.finished) return@withLock
        val t = now()
        dao.update(if (m.running) m.stopClock(t) else m.startClock(t))
        onChange()
    }

    /** Football: starts the next period (clock runs) or ends the current one (clock stops). */
    suspend fun period(id: Long, start: Boolean) = lock.withLock {
        var m = dao.get(id) ?: return@withLock
        if (m.finished) return@withLock
        // A double tap must not start (or end) the same period twice.
        val b = board(m)
        if (b.periodOpen == start || b.shootout != null) return@withLock
        val t = now()
        m = if (start) m.startClock(t) else m.stopClock(t)
        dao.insert(EventRow(matchId = id, side = -1, kind = if (start) Kind.PERIOD_START else Kind.PERIOD_END,
            clockMs = m.clockAt(t), at = t))
        refresh(m)
    }

    /** Ends the match now (football, or a match stopped early). */
    suspend fun finish(id: Long) = lock.withLock {
        val m = dao.get(id) ?: return@withLock
        if (m.finished) return@withLock
        val t = now()
        val stopped = m.stopClock(t)
        // Football: close the period that is still running, so the timeline shows where the match ended.
        if (m.sportEnum == Sport.FOOTBALL && board(m).periodOpen) {
            dao.insert(EventRow(matchId = id, side = -1, kind = Kind.PERIOD_END, clockMs = stopped.clockAt(t), at = t))
        }
        refresh(stopped.copy(finishedAt = t, manualEnd = true))
    }

    /** Plays on after "Kết thúc" was pressed by mistake. */
    suspend fun reopen(id: Long) = lock.withLock {
        val m = dao.get(id) ?: return@withLock
        dao.update(m.copy(finishedAt = 0, manualEnd = false))
        onChange()
    }

    // These read the row again instead of trusting the screen's copy, which may be older than a point just added.
    suspend fun swap(id: Long) = lock.withLock {
        val m = dao.get(id) ?: return@withLock
        dao.update(m.copy(swapped = !m.swapped))
    }

    suspend fun rename(id: Long, a: String, b: String) = lock.withLock {
        val m = dao.get(id) ?: return@withLock
        dao.update(m.copy(nameA = a.trim(), nameB = b.trim()))
        onChange()
    }

    suspend fun delete(id: Long) = lock.withLock {
        dao.get(id)?.let { dao.delete(it) }
        onChange()
    }

    // ---- Tournaments

    suspend fun createTournament(t: TournamentRow): Long = dao.insert(t).also { onChange() }

    suspend fun deleteTournament(id: Long) = lock.withLock {
        dao.deleteTournamentMatches(id)
        dao.deleteTournament(id)
        onChange()
    }

    /**
     * The match of a fixture: the existing one, or a new one with the tournament's rules. Under the lock, so two
     * quick taps cannot create two matches. A knockout match not started yet takes the current names (an earlier
     * result may have changed who goes through).
     */
    suspend fun fixtureMatch(t: TournamentRow, fixture: String, a: String, b: String): Long = lock.withLock {
        val existing = dao.byFixture(t.id, fixture)
        if (existing != null) {
            if (existing.startedAt == 0L && (existing.nameA != a || existing.nameB != b)) {
                dao.update(existing.copy(nameA = a, nameB = b))
            }
            return@withLock existing.id
        }
        dao.insert(
            MatchRow(sport = t.sport, rules = t.rules, nameA = a, nameB = b, firstServer = 0, createdAt = now(),
                doubles = t.doubles, tournamentId = t.id, fixture = fixture),
        ).also { onChange() }
    }

    /** Replays the events of [m] (already holding the new clock) and saves the cached score and finish. */
    private suspend fun refresh(m: MatchRow) {
        val b = board(m)
        val t = now()
        var u = m.copy(score = b.score, detail = b.detail, winner = b.winner)
        // Won by the rules (or by the shoot-out): stop. The winning point taken back: play on, clock paused.
        if (b.finished && !m.finished) u = u.stopClock(t).copy(finishedAt = t, manualEnd = false)
        if (!b.finished && m.finished && !m.manualEnd) u = u.copy(finishedAt = 0)
        dao.update(u)
        onChange()
    }
}
