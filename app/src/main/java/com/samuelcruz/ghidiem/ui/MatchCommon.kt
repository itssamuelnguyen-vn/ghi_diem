package com.samuelcruz.ghidiem.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.samuelcruz.ghidiem.GhiDiemApp
import com.samuelcruz.ghidiem.core.Board
import com.samuelcruz.ghidiem.core.FootballClock
import com.samuelcruz.ghidiem.core.Kind
import com.samuelcruz.ghidiem.core.Line
import com.samuelcruz.ghidiem.core.Scoring
import com.samuelcruz.ghidiem.core.Sport
import com.samuelcruz.ghidiem.data.EventRow
import com.samuelcruz.ghidiem.data.MatchRow
import kotlinx.coroutines.delay

/** A match as a screen sees it: the row, its events, the replayed board and the ticking clock. */
class MatchUi(val m: MatchRow, val evs: List<EventRow>, val board: Board, val clock: Long) {
    val sport get() = m.sportEnum
    val done get() = m.finished || board.finished
    /** Left side first ("Đổi bên" swaps them on screen). */
    val order get() = if (m.swapped) listOf(1, 0) else listOf(0, 1)
    val lastId get() = evs.lastOrNull()?.id ?: 0L
}

/** Watches match [id]; null while loading or after it was deleted. The clock ticks 5 times a second while it runs. */
@Composable
fun rememberMatch(id: Long): MatchUi? {
    val dao = app().db.matches()
    val row by remember(id) { dao.watch(id) }.collectAsState(initial = null)
    val events by remember(id) { dao.events(id) }.collectAsState(initial = null)
    val m = row
    val evs = events
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val running = m?.running == true
    LaunchedEffect(running) {
        now = System.currentTimeMillis()
        while (running) {
            delay(200)
            now = System.currentTimeMillis()
        }
    }
    if (m == null || evs == null) return null
    val board = remember(m.sport, m.rules, m.firstServer, m.doubles, evs) { Scoring.replay(m.setup, evs.map { it.ev() }) }
    return MatchUi(m, evs, board, m.clockAt(now))
}

/** Loading, or the match no longer exists: a back button, and a line once loading clearly is not the reason. */
@Composable
fun MissingMatch(onBack: () -> Unit) {
    var late by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(800)
        late = true
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {
        IconButton(onBack, Modifier.padding(4.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
        if (late) Text("Không tìm thấy trận này (có thể đã bị xoá).", Modifier.align(Alignment.Center).padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Keeps the screen on, speaks / beeps after events, and takes volume keys while the match is played. */
@Composable
fun MatchFeedback(ui: MatchUi, onSide: (Int) -> Unit) {
    val app = app()
    val view = LocalView.current
    val done = ui.done
    // Counted, because the match screen and the full-screen board overlap while one replaces the other.
    DisposableEffect(done) {
        if (!done) keepOnCount++
        view.keepScreenOn = keepOnCount > 0
        onDispose {
            if (!done) keepOnCount--
            view.keepScreenOn = keepOnCount > 0
        }
    }

    // Volume up = left side, volume down = right side (as the phone is held facing the court).
    val order by rememberUpdatedState(ui.order)
    val side by rememberUpdatedState(onSide)
    DisposableEffect(done) {
        val mine: ((Boolean) -> Unit)? = if (done) null else { up -> side(if (up) order[0] else order[1]) }
        if (mine != null) app.volumeHandler = mine
        // Only clear it if it is still ours (the next screen may have set its own already).
        onDispose { if (mine != null && app.volumeHandler === mine) app.volumeHandler = null }
    }

    // Speak only events added while the screen is open, not the ones already there when it opened.
    var spokenId by rememberSaveable { mutableLongStateOf(-1L) }
    LaunchedEffect(ui.lastId) {
        val last = ui.evs.lastOrNull()
        // Recent only: coming back to the screen must not repeat a point scored elsewhere a while ago.
        if (spokenId >= 0 && ui.lastId > spokenId && app.prefs.voice && last != null && System.currentTimeMillis() - last.at < 5_000) {
            val i = ui.evs.lastIndex
            ui.board.lines.getOrNull(i)?.let { app.speaker.speak(announce(ui, last, it)) }
        }
        spokenId = ui.lastId
    }

    // Time-out: beep when its seconds are over.
    val lastTimeout = ui.evs.lastOrNull()?.takeIf { it.kind == Kind.TIMEOUT }
    LaunchedEffect(lastTimeout?.id) {
        val t = lastTimeout ?: return@LaunchedEffect
        val left = t.at + ui.sport.timeoutSeconds * 1000L - System.currentTimeMillis()
        if (left > 0) {
            delay(left)
            if (app.prefs.beeps) app.sounds.whistle()
            if (app.prefs.voice) app.speaker.speak("Hết giờ hội ý")
        }
    }

    // Football: whistle when the period's regular time is up (once; restarts with the clock).
    val b = ui.board
    val m = ui.m
    LaunchedEffect(b.period, b.periodOpen, m.runningSince, m.clockBaseMs, done) {
        if (ui.sport != Sport.FOOTBALL || !b.periodOpen || !m.running || done) return@LaunchedEffect
        val k = b.period - 1
        val lenMs = FootballClock.length(m.rulesObj, k) * 60_000L
        val left = lenMs - (m.clockAt(System.currentTimeMillis()) - b.periodStarts[k])
        if (left > 0) {
            delay(left)
            if (app.prefs.beeps) app.sounds.whistle()
            if (app.prefs.voice) app.speaker.speak("Hết thời gian chính thức")
        }
    }
}

/** Open screens that want the screen kept on (main thread only). */
private var keepOnCount = 0

/** What the voice says after an event, server's score first like an umpire. */
fun announce(ui: MatchUi, e: EventRow, line: Line): String {
    val m = ui.m
    val b = ui.board
    val name = if (e.side in 0..1) m.name(e.side) else ""
    fun score(a: String, c: String): String {
        val s = b.server
        return if (s == 1) "$c, $a" else "$a, $c"
    }
    val tail = listOfNotNull(
        b.callouts.joinToString(". ") { "${it.text} ${m.name(it.side)}" }.ifEmpty { null },
        line.alert,
    ).joinToString(". ")
    val main = when (e.kind) {
        Kind.POINT -> when {
            line.label == "Thắng trận" -> "$name thắng trận"
            line.label.startsWith("Thắng") -> "$name ${line.label.lowercase()}"
            ui.sport == Sport.TENNIS && (line.label == "Game" || line.label == "Break") -> {
                val g = b.games ?: listOf(0, 0)
                "Game, $name. " + score(g[0].toString(), g[1].toString())
            }
            ui.sport == Sport.TENNIS && b.info == "Deuce" -> "Deuce"
            ui.sport == Sport.TENNIS && "AD" in b.main -> "Lợi thế ${m.name(b.main.indexOf("AD"))}"
            else -> score(b.main[0], b.main[1])
        }
        Kind.GOAL -> "Vào! $name. ${b.main[0]}, ${b.main[1]}"
        Kind.OWN_GOAL -> "Phản lưới nhà. Bàn thắng cho $name. ${b.main[0]}, ${b.main[1]}"
        Kind.YELLOW -> "Thẻ vàng, $name"
        Kind.RED -> "Thẻ đỏ, $name"
        Kind.SUB -> "Thay người, $name"
        Kind.TIMEOUT -> "Hội ý, $name"
        Kind.PEN_SCORED -> "Vào. Luân lưu ${b.shootoutScore}"
        Kind.PEN_MISSED -> "Trượt. Luân lưu ${b.shootoutScore}"
        else -> line.label
    }
    return listOf(main, tail).filter { it.isNotBlank() }.joinToString(". ")
}

/** Long press on a panel: removes [side]'s last point / goal, with a buzz; tells when there is nothing to remove. */
suspend fun GhiDiemApp.takeBack(m: MatchRow, side: Int, haptic: () -> Unit, message: suspend (String) -> Unit) {
    haptic() // always, so the press is felt even with haptics off for points
    val ok = kotlinx.coroutines.withContext(scope.coroutineContext) { repo.undoSide(m.id, side) }
    val what = if (m.sportEnum == Sport.FOOTBALL) "bàn thắng" else "điểm"
    message(if (ok) "Đã trừ 1 $what của ${m.name(side)}" else "${m.name(side)} chưa có $what nào để trừ")
}

/** Point / goal for [side]: haptic, write in the app scope, and say why when refused. */
suspend fun GhiDiemApp.tapScore(m: MatchRow, side: Int, kind: Int, haptic: () -> Unit, message: suspend (String) -> Unit): Boolean {
    if (prefs.haptics) haptic()
    val ok = kotlinx.coroutines.withContext(scope.coroutineContext) { repo.score(m.id, side, kind) }
    if (!ok && repo.lastRefusal.isNotEmpty()) message(repo.lastRefusal)
    return ok
}

/** Half of the scoreboard: tap anywhere on it to give this side the point (or goal), long press to take one back. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Panel(side: Int, ui: MatchUi, modifier: Modifier, huge: Boolean = false, onLongTap: () -> Unit = {}, onTap: () -> Unit) {
    val m = ui.m
    val board = ui.board
    val sport = ui.sport
    val done = ui.done
    val won = done && (board.winner.takeIf { it >= 0 } ?: m.winner) == side
    val players = m.players(side)
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(if (huge) 36.dp else 28.dp))
            .background(Team.brush(side))
            // Tap = point; long press = take back this side's last point (also the winning one of a finished
            // match; not in a match ended by hand).
            .combinedClickable(enabled = !ui.m.manualEnd, onClick = { if (!done) onTap() }, onLongClick = onLongTap),
    ) {
        val big: Dp = min(maxWidth * if (huge) 0.7f else 0.62f, maxHeight * if (huge) 0.62f else 0.48f)
        val bigSp = with(LocalDensity.current) { big.toSp() }
        val labelStyle = if (huge) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(m.name(side), color = Color.White, style = labelStyle,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (sport != Sport.FOOTBALL) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${sport.setWord}: ", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                    Pill(board.setsWon[side].toString())
                    board.games?.let {
                        Spacer(Modifier.width(8.dp))
                        Text("Game: ", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        Pill(it[side].toString())
                    }
                    if (sport.timeouts > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text("⏸ ${board.timeouts[side]}/${sport.timeouts}", color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = board.main[side],
                    transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                    label = "score",
                ) { s ->
                    Text(s, color = Color.White, fontSize = if (s.length > 2) bigSp * 0.7f else bigSp,
                        fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.displayLarge, maxLines = 1)
                }
            }
            board.shootout?.let { kicks ->
                ShootoutDots(kicks[side])
                Spacer(Modifier.height(4.dp))
            }
            when {
                won -> Pill("🏆  Thắng", strong = true)
                sport == Sport.FOOTBALL -> {
                    val (y, r) = board.cards[side]
                    if (y + r > 0) Text("🟨 $y   🟥 $r", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    else Spacer(Modifier.height(20.dp))
                }
                board.server == side && !done -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val who = players.getOrNull(board.serverPlayer)?.let { "$it giao" } ?: "Giao"
                    Pill("${sport.emoji}  $who", strong = true)
                    board.serveHint?.let {
                        Text(it.removePrefix("Giao ").replaceFirstChar { c -> c.uppercase() }, color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                board.server == 1 - side && !done && board.receiverPlayer >= 0 -> {
                    Pill("${players.getOrNull(board.receiverPlayer) ?: ""} nhận")
                }
                else -> Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun ShootoutDots(kicks: List<Boolean>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val shown = maxOf(5, kicks.size)
        for (i in 0 until shown) {
            val k = kicks.getOrNull(i)
            Box(
                Modifier.size(14.dp).clip(CircleShape).background(
                    when (k) {
                        true -> Color.White
                        false -> Color.Black.copy(alpha = 0.35f)
                        null -> Color.White.copy(alpha = 0.25f)
                    },
                ),
            )
        }
    }
}

@Composable
fun Pill(text: String, strong: Boolean = false) {
    Text(
        text,
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (strong) 0.95f else 0.22f))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        color = if (strong) Color(0xFF141726) else Color.White,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "45:12" + stoppage, or the match stopwatch; and a line under it. */
data class ClockText(val main: String, val extra: String?, val sub: String)

fun clockText(ui: MatchUi): ClockText {
    val m = ui.m
    val board = ui.board
    val r = m.rulesObj
    val clock = ui.clock
    if (ui.sport == Sport.FOOTBALL) {
        val k = board.period - 1
        if (k < 0) return ClockText("00:00", null, "Chạm ô đội ghi bàn hoặc “Bắt đầu hiệp 1”")
        val (a, b) = FootballClock.display(r, k, clock - board.periodStarts[k])
        val sub = com.samuelcruz.ghidiem.core.periodName(r, k + 1).replaceFirstChar { it.uppercase() } + when {
            board.shootout != null -> " · luân lưu ${board.shootoutScore}"
            ui.done -> " · tổng ${com.samuelcruz.ghidiem.core.fmtDuration(clock)}"
            !board.periodOpen -> " · đã hết"
            !m.running -> " · tạm dừng"
            else -> ""
        }
        return ClockText(a, b, sub)
    }
    val setStart = board.history.lastOrNull()?.endMs ?: 0
    val sub = when {
        m.startedAt == 0L -> "Chạm ô của bên thắng điểm để bắt đầu"
        ui.done -> "Tổng thời gian trận"
        else -> "${ui.sport.setWord} ${board.setNo}: ${com.samuelcruz.ghidiem.core.fmtClock(clock - setStart)}" +
            if (!m.running) " · tạm dừng" else ""
    }
    return ClockText(com.samuelcruz.ghidiem.core.fmtClock(clock), null, sub)
}

/** Text for the result in the banner: "🏆 A thắng 2-1", "Hoà 1-1", or the shoot-out. */
fun resultText(ui: MatchUi): String {
    val m = ui.m
    val b = ui.board
    val w = b.winner.takeIf { it >= 0 } ?: m.winner
    val (x, y) = b.score.split("-").map { it.toIntOrNull() ?: 0 }
    val pens = b.shootoutScore?.let { s ->
        val (p, q) = s.split("-")
        if (w == 1) " (luân lưu $q-$p)" else " (luân lưu $p-$q)"
    } ?: ""
    return when {
        w == 0 -> "🏆  ${m.nameA} thắng $x-$y$pens"
        w == 1 -> "🏆  ${m.nameB} thắng $y-$x$pens"
        x == y -> "Hoà $x-$y"
        else -> "Dừng trận ở $x-$y"
    }
}
