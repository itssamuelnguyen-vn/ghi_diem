package com.samuelcruz.ghidiem.data

import com.samuelcruz.ghidiem.core.Result
import com.samuelcruz.ghidiem.core.Sport
import com.samuelcruz.ghidiem.core.smallScores

/** A finished match as standings / player records count it; [nameA]/[nameB] can be replaced (tournament entrants). */
fun MatchRow.result(nameA: String = this.nameA, nameB: String = this.nameB): Result {
    val (x, y) = score.split("-").map { it.trim().toIntOrNull() ?: 0 }.let { (it.getOrNull(0) ?: 0) to (it.getOrNull(1) ?: 0) }
    val (sa, sb) = if (sportEnum == Sport.FOOTBALL) 0 to 0 else smallScores(detail)
    return Result(sportEnum, nameA, nameB, winner, x, y, sa, sb)
}
