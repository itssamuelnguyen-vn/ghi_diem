package com.samuelcruz.ghidiem.data

import com.samuelcruz.ghidiem.core.Kind
import com.samuelcruz.ghidiem.core.Rules
import com.samuelcruz.ghidiem.core.Scoring
import com.samuelcruz.ghidiem.core.Sport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** All writes (like a Rails service object). Every change to events re-replays the score and refreshes the cache. */
class Repo(private val dao: MatchDao) {
    /** Fast double taps must not interleave read-modify-write of the clock. */
    private val lock = Mutex()
    private fun now() = System.currentTimeMillis()

    suspend fun create(sport: Sport, rules: Rules, nameA: String, nameB: String, firstServer: Int): Long =
        dao.insert(MatchRow(sport = sport.name, rules = rules.toJson(), nameA = nameA, nameB = nameB,
            firstServer = firstServer, createdAt = now()))

    /** Same players and rules, fresh score; the loser of [m] serves first. */
    suspend fun rematch(id: Long): Long? {
        val m = dao.get(id) ?: return null
        return rematch(m)
    }

    private suspend fun rematch(m: MatchRow): Long = dao.insert(
        m.copy(id = 0, createdAt = now(), startedAt = 0, finishedAt = 0, clockBaseMs = 0, runningSince = 0,
            score = "0-0", detail = "", winner = -1, manualEnd = false, firstServer = if (m.winner >= 0) 1 - m.winner else m.firstServer),
    )

    /**
     * A point (rally sports, tennis), goal or card for [side]. Starts the clock when the match starts, and
     * resumes it when play goes on after a pause. Football: only inside a period (the first one opens itself).
     * Returns false when nothing was recorded.
     */
    suspend fun score(id: Long, side: Int, kind: Int): Boolean = lock.withLock {
        var m = dao.get(id) ?: return false
        if (m.finished) return false
        val t = now()
        if (m.sportEnum == Sport.FOOTBALL) {
            val b = Scoring.replay(m.setup, dao.eventsOnce(id).map { it.ev() })
            if (!b.periodOpen) {
                if (b.period > 0) return false
                m = m.startClock(t)
                dao.insert(EventRow(matchId = id, side = -1, kind = Kind.PERIOD_START, clockMs = m.clockAt(t), at = t))
            }
        }
        m = m.startClock(t)
        dao.insert(EventRow(matchId = id, side = side, kind = kind, clockMs = m.clockAt(t), at = t))
        refresh(m)
        true
    }

    /** Takes back the last point / goal / card. */
    suspend fun undo(id: Long) = lock.withLock {
        val last = dao.eventsOnce(id).lastOrNull { Kind.undoable(it.kind) } ?: return@withLock
        dao.delete(last)
        refresh(dao.get(id) ?: return@withLock)
    }

    suspend fun deleteEvent(e: EventRow) = lock.withLock {
        dao.delete(e)
        refresh(dao.get(e.matchId) ?: return@withLock)
    }

    suspend fun setNote(e: EventRow, note: String) = dao.update(e.copy(note = note.trim()))

    suspend fun toggleClock(id: Long) = lock.withLock {
        val m = dao.get(id) ?: return@withLock
        if (m.finished) return@withLock
        val t = now()
        dao.update(if (m.running) m.stopClock(t) else m.startClock(t))
    }

    /** Football: starts the next period (clock runs) or ends the current one (clock stops). */
    suspend fun period(id: Long, start: Boolean) = lock.withLock {
        var m = dao.get(id) ?: return@withLock
        if (m.finished) return@withLock
        // A double tap must not start (or end) the same period twice.
        val open = Scoring.replay(m.setup, dao.eventsOnce(id).map { it.ev() }).periodOpen
        if (open == start) return@withLock
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
        if (m.sportEnum == Sport.FOOTBALL && Scoring.replay(m.setup, dao.eventsOnce(id).map { it.ev() }).periodOpen) {
            dao.insert(EventRow(matchId = id, side = -1, kind = Kind.PERIOD_END, clockMs = stopped.clockAt(t), at = t))
        }
        refresh(stopped.copy(finishedAt = t, manualEnd = true))
    }

    /** Plays on after "Kết thúc" was pressed by mistake. */
    suspend fun reopen(id: Long) = lock.withLock {
        val m = dao.get(id) ?: return@withLock
        dao.update(m.copy(finishedAt = 0, manualEnd = false))
    }

    // These read the row again instead of trusting the screen's copy, which may be older than a point just added.
    suspend fun swap(id: Long) = lock.withLock {
        val m = dao.get(id) ?: return@withLock
        dao.update(m.copy(swapped = !m.swapped))
    }

    suspend fun rename(id: Long, a: String, b: String) = lock.withLock {
        val m = dao.get(id) ?: return@withLock
        dao.update(m.copy(nameA = a.trim(), nameB = b.trim()))
    }

    suspend fun delete(id: Long) = lock.withLock {
        dao.get(id)?.let { dao.delete(it) }
    }

    /** Replays the events of [m] (already holding the new clock) and saves the cached score and finish. */
    private suspend fun refresh(m: MatchRow) {
        val b = Scoring.replay(m.setup, dao.eventsOnce(m.id).map { it.ev() })
        val t = now()
        var u = m.copy(score = b.score, detail = b.detail, winner = b.winner)
        if (m.sportEnum != Sport.FOOTBALL) {
            if (b.finished && !m.finished) u = u.stopClock(t).copy(finishedAt = t, manualEnd = false)
            // The winning point was taken back: play on (clock stays paused until the next point).
            if (!b.finished && m.finished && !m.manualEnd) u = u.copy(finishedAt = 0)
        }
        dao.update(u)
    }
}
