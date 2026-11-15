package com.samuelcruz.ghidiem.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.samuelcruz.ghidiem.core.Board
import com.samuelcruz.ghidiem.core.FootballClock
import com.samuelcruz.ghidiem.core.Kind
import com.samuelcruz.ghidiem.core.Scoring
import com.samuelcruz.ghidiem.core.Sport
import com.samuelcruz.ghidiem.core.fmtClock
import com.samuelcruz.ghidiem.core.fmtDuration
import com.samuelcruz.ghidiem.core.periodName
import com.samuelcruz.ghidiem.data.EventRow
import com.samuelcruz.ghidiem.data.MatchRow
import com.samuelcruz.ghidiem.data.Repo
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MatchScreen(id: Long, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val app = app()
    val dao = app.db.matches()
    val row by remember(id) { dao.watch(id) }.collectAsState(initial = null)
    val events by remember(id) { dao.events(id) }.collectAsState(initial = null)
    val m = row
    val evs = events
    if (m == null || evs == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val board = remember(m.sport, m.rules, m.firstServer, evs) { Scoring.replay(m.setup, evs.map { it.ev() }) }
    val sport = m.sportEnum
    val done = m.finished || board.finished
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Writes run in the app scope so a quick "back" does not cancel them.
    fun act(f: suspend Repo.() -> Unit) {
        app.scope.launch { app.repo.f() }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(m.running) {
        while (m.running) {
            now = System.currentTimeMillis()
            delay(200)
        }
        now = System.currentTimeMillis()
    }
    val clock = m.clockAt(now)

    // The screen stays on while the match is being played.
    val view = LocalView.current
    DisposableEffect(done) {
        view.keepScreenOn = !done
        onDispose { view.keepScreenOn = false }
    }

    var editing by remember { mutableStateOf<EventRow?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    /** Id of the newest event whose alert the user closed: only alerts of newer events are shown. */
    var dismissedId by rememberSaveable { mutableLongStateOf(0L) }
    val lastId = evs.lastOrNull()?.id ?: 0L
    var busy by remember { mutableStateOf(false) }

    fun tap(side: Int) {
        if (done) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val kind = if (sport == Sport.FOOTBALL) Kind.GOAL else Kind.POINT
        uiScope.launch {
            val ok = app.scope.async { app.repo.score(m.id, side, kind) }.await()
            if (!ok && sport == Sport.FOOTBALL) {
                snackbar.showSnackbar("Đang nghỉ giữa hiệp: bấm “Bắt đầu hiệp” trước")
            } else if (ok && sport == Sport.FOOTBALL) {
                val goal = app.scope.async { dao.eventsOnce(m.id).lastOrNull { it.kind == Kind.GOAL } }.await() ?: return@launch
                val r = snackbar.showSnackbar("Bàn thắng cho ${m.name(side)}", "Ai ghi?", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) editing = goal
            }
        }
    }

    val order = if (m.swapped) listOf(1, 0) else listOf(0, 1)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            val wide = maxWidth > maxHeight
            val panelHeight = min(maxHeight * 0.42f, 360.dp)
            val top: @Composable ColumnScope.() -> Unit = {
                TopBar(m, board, done, onBack,
                    onSwap = { act { swap(m.id) } },
                    onRename = { renaming = true },
                    onFinish = { confirmFinish = true },
                    onReopen = { act { reopen(m.id) } },
                    onShare = { share(context, m, board, evs) },
                    onDelete = { confirmDelete = true })
                ClockBar(m, board, clock, done, onToggle = { act { toggleClock(m.id) } })
                Banner(m, board, done, showAlert = lastId > dismissedId,
                    onDismiss = { dismissedId = lastId },
                    onSwap = { act { swap(m.id) }; dismissedId = lastId },
                    onRematch = {
                        if (!busy) {
                            busy = true // one tap = one new match
                            uiScope.launch { app.scope.async { app.repo.rematch(m.id) }.await()?.let(onOpen) }
                        }
                    },
                    onShare = { share(context, m, board, evs) })
            }
            val panels: @Composable (Modifier) -> Unit = { mod ->
                Row(mod.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    order.forEach { side ->
                        Panel(side, m, board, done, Modifier.weight(1f).fillMaxHeight()) { tap(side) }
                    }
                }
            }
            val controls: @Composable () -> Unit = {
                // A match ended by hand is closed; one won by the rules can take back its last point.
                Controls(m, board, done, order, evs.any { Kind.undoable(it.kind) } && !m.manualEnd,
                    onUndo = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); act { undo(m.id) } },
                    onCard = { side, kind -> act { score(m.id, side, kind) } },
                    onPeriod = { start -> act { period(m.id, start) } },
                    onFinish = { act { finish(m.id) } })
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1.5f).fillMaxHeight()) {
                        top()
                        panels(Modifier.weight(1f).fillMaxWidth())
                        controls()
                    }
                    Timeline(m, board, evs, Modifier.weight(1f).fillMaxHeight()) { editing = it }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    top()
                    panels(Modifier.height(panelHeight).fillMaxWidth())
                    controls()
                    Timeline(m, board, evs, Modifier.weight(1f).fillMaxWidth()) { editing = it }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
    }

    editing?.let { e ->
        EventDialog(e, m, board, evs.indexOf(e), onDismiss = { editing = null },
            onSave = { note -> act { setNote(e, note) }; editing = null },
            onDelete = { act { deleteEvent(e) }; editing = null })
    }
    if (renaming) RenameDialog(m, onDismiss = { renaming = false }) { a, b -> act { rename(m.id, a, b) }; renaming = false }
    if (confirmFinish) AlertDialog(
        onDismissRequest = { confirmFinish = false },
        title = { Text(if (sport == Sport.FOOTBALL) "Kết thúc trận?" else "Dừng trận ở đây?") },
        text = {
            Text(if (sport == Sport.FOOTBALL) "Đồng hồ dừng, trận được lưu vào lịch sử với tỉ số ${board.score}."
            else "Trận chưa phân thắng thua theo luật. Vẫn lưu tỉ số hiện tại vào lịch sử.")
        },
        confirmButton = { TextButton({ act { finish(m.id) }; confirmFinish = false }) { Text("Kết thúc") } },
        dismissButton = { TextButton({ confirmFinish = false }) { Text("Thôi") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Xoá trận này?") },
        text = { Text("Toàn bộ điểm và thời gian của trận sẽ mất.") },
        confirmButton = {
            TextButton({
                confirmDelete = false
                app.scope.launch { app.repo.delete(m.id) }
                onBack()
            }) { Text("Xoá", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Thôi") } },
    )
}

@Composable
private fun TopBar(
    m: MatchRow, board: Board, done: Boolean, onBack: () -> Unit, onSwap: () -> Unit, onRename: () -> Unit,
    onFinish: () -> Unit, onReopen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val sport = m.sportEnum
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
        Column(Modifier.weight(1f)) {
            Text("${sport.emoji}  ${sport.title}", style = MaterialTheme.typography.titleMedium)
            val sub = when {
                done -> "Đã kết thúc"
                sport == Sport.FOOTBALL -> if (board.period == 0) "Chưa bắt đầu"
                else periodName(m.rulesObj, board.period).replaceFirstChar { it.uppercase() } + if (board.periodOpen) "" else " · đã hết"
                else -> "${sport.setWord} ${board.setNo}" + (board.info?.let { " · $it" } ?: "")
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onSwap) { Icon(Icons.Filled.SwapHoriz, "Đổi bên trái / phải") }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "Thêm") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Đổi tên hai bên") }, { menu = false; onRename() })
                DropdownMenuItem({ Text("Chia sẻ kết quả") }, { menu = false; onShare() })
                if (!done) DropdownMenuItem({ Text(if (sport == Sport.FOOTBALL) "Kết thúc trận" else "Dừng trận ở đây") },
                    { menu = false; onFinish() })
                if (m.manualEnd) DropdownMenuItem({ Text("Chơi tiếp trận này") }, { menu = false; onReopen() })
                DropdownMenuItem({ Text("Xoá trận", color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete() })
            }
        }
    }
}

/** The stopwatch: whole match for rally sports (+ this set), match minute for football. */
@Composable
private fun ClockBar(m: MatchRow, board: Board, clock: Long, done: Boolean, onToggle: () -> Unit) {
    val sport = m.sportEnum
    val r = m.rulesObj
    var main: String
    var extra: String? = null
    var sub: String
    if (sport == Sport.FOOTBALL) {
        val k = board.period - 1
        if (k < 0) {
            main = "00:00"; sub = "Chạm ô đội ghi bàn hoặc “Bắt đầu hiệp 1”"
        } else {
            val (a, b) = FootballClock.display(r, k, clock - board.periodStarts[k])
            main = a; extra = b
            sub = periodName(r, k + 1).replaceFirstChar { it.uppercase() } + when {
                done -> " · tổng ${fmtDuration(clock)}"
                !board.periodOpen -> " · đã hết"
                !m.running -> " · tạm dừng"
                else -> ""
            }
        }
    } else {
        main = fmtClock(clock)
        val setStart = board.history.lastOrNull()?.endMs ?: 0
        sub = when {
            m.startedAt == 0L -> "Chạm ô của bên thắng điểm để bắt đầu"
            done -> "Tổng thời gian trận"
            else -> "${sport.setWord} ${board.setNo}: ${fmtClock(clock - setStart)}" + if (!m.running) " · tạm dừng" else ""
        }
    }
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(main, style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp),
                        color = if (m.running || done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    extra?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, Modifier.padding(bottom = 4.dp), color = Amber, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val canToggle = !done && (sport != Sport.FOOTBALL || board.periodOpen)
            if (canToggle) FilledTonalIconButton(onToggle, Modifier.size(52.dp)) {
                Icon(if (m.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (m.running) "Tạm dừng đồng hồ" else "Chạy đồng hồ", Modifier.size(28.dp))
            }
        }
    }
}

/** One line under the clock: what just happened (change ends…), who is at match point, or the result. */
@Composable
private fun Banner(
    m: MatchRow, board: Board, done: Boolean, showAlert: Boolean,
    onDismiss: () -> Unit, onSwap: () -> Unit, onRematch: () -> Unit, onShare: () -> Unit,
) {
    val alert = board.alert?.takeIf { showAlert }
    when {
        done -> {
            val w = board.winner.takeIf { it >= 0 } ?: m.winner
            BannerBox(if (w >= 0) Team.brush(w) else HeroBrush) {
                val (a, b) = board.score.split("-")
                Text(when {
                    w >= 0 -> "🏆  ${m.name(w)} thắng ${scoreFor(board, w)}"
                    a == b -> "Hoà ${board.score}"
                    else -> "Dừng trận ở ${board.score}"
                },
                    Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onShare) { Text("Chia sẻ", color = Color.White) }
                FilledTonalButton(onRematch) { Text("Đấu lại") }
            }
        }
        alert != null -> BannerBox(androidx.compose.ui.graphics.SolidColor(Amber)) {
            Text(alert, Modifier.weight(1f), color = Color(0xFF451A03), style = MaterialTheme.typography.titleMedium)
            if ("Đổi sân" in alert) TextButton(onSwap) { Text("Đổi bên", color = Color(0xFF451A03)) }
            TextButton(onDismiss) { Text("OK", color = Color(0xFF451A03)) }
        }
        board.callouts.isNotEmpty() -> BannerBox(androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.secondaryContainer)) {
            Text(board.callouts.joinToString("  ·  ") { "${it.text.uppercase()} ${m.name(it.side)}" },
                Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun scoreFor(board: Board, side: Int): String {
    val (a, b) = board.score.split("-").map { it.toIntOrNull() ?: 0 }
    return if (side == 0) "$a-$b" else "$b-$a"
}

@Composable
private fun BannerBox(brush: androidx.compose.ui.graphics.Brush, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Half of the scoreboard: tap anywhere on it to give this side the point (or goal). */
@Composable
private fun Panel(side: Int, m: MatchRow, board: Board, done: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val sport = m.sportEnum
    val won = done && (board.winner.takeIf { it >= 0 } ?: m.winner) == side
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Team.brush(side))
            .clickable(enabled = !done, onClick = onTap),
    ) {
        val big: Dp = min(maxWidth * 0.62f, maxHeight * 0.48f)
        val bigSp = with(androidx.compose.ui.platform.LocalDensity.current) { big.toSp() }
        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(m.name(side), color = Color.White, style = MaterialTheme.typography.titleMedium,
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
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = board.main[side],
                    transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                    label = "score",
                ) { s ->
                    Text(s, color = Color.White, fontSize = if (s.length > 2) bigSp * 0.7f else bigSp,
                        fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.displayLarge,
                        maxLines = 1)
                }
            }
            when {
                won -> Pill("🏆  Thắng", strong = true)
                sport == Sport.FOOTBALL -> {
                    val (y, r) = board.cards[side]
                    if (y + r > 0) Text("🟨 $y   🟥 $r", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    else Spacer(Modifier.height(20.dp))
                }
                board.server == side && !done -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Pill("${sport.emoji}  Giao", strong = true)
                    board.serveHint?.let {
                        Text(it.removePrefix("Giao ").replaceFirstChar { c -> c.uppercase() }, color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                else -> Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun Pill(text: String, strong: Boolean = false) {
    Text(
        text,
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (strong) 0.95f else 0.22f))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        color = if (strong) Color(0xFF141726) else Color.White,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun Controls(
    m: MatchRow, board: Board, done: Boolean, order: List<Int>, canUndo: Boolean,
    onUndo: () -> Unit, onCard: (Int, Int) -> Unit, onPeriod: (Boolean) -> Unit, onFinish: () -> Unit,
) {
    val sport = m.sportEnum
    val r = m.rulesObj
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (sport == Sport.FOOTBALL && !done && board.periodOpen) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                order.forEach { side ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton({ onCard(side, Kind.YELLOW) }, Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("🟨 Vàng") }
                        OutlinedButton({ onCard(side, Kind.RED) }, Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("🟥 Đỏ") }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hoàn tác")
            }
            if (sport == Sport.FOOTBALL && !done) {
                val p = board.period
                when {
                    board.periodOpen -> Button({ onPeriod(false) }, Modifier.weight(1f)) {
                        Text("Kết thúc ${periodName(r, p)}")
                    }
                    p < r.periods -> Button({ onPeriod(true) }, Modifier.weight(1f)) { Text("Bắt đầu ${periodName(r, p + 1)}") }
                    else -> {
                        if (p < r.periods + 2) OutlinedButton({ onPeriod(true) }, Modifier.weight(1f)) {
                            Text(if (p == r.periods) "Đá hiệp phụ" else "Hiệp phụ 2")
                        }
                        Button({ onFinish() }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) { Text("Kết thúc trận") }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
                if (board.history.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), reverseLayout = true) {
                    items(board.history.reversed()) { s ->
                        val (l, rr) = order.map { if (it == 0) s.a else s.b }
                        val tb = if (s.tbA >= 0) "(${kotlin.math.min(s.tbA, s.tbB)})" else ""
                        Text(
                            if (s.superTb) "[$l-$rr]" else "$l-$rr$tb",
                            Modifier.clip(RoundedCornerShape(10.dp)).background(Team.color(s.winner).copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            color = Team.color(s.winner), style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

/** Every point / goal with its time, newest first, grouped by set. Tennis hides single points by default. */
@Composable
private fun Timeline(m: MatchRow, board: Board, evs: List<EventRow>, modifier: Modifier, onEdit: (EventRow) -> Unit) {
    val sport = m.sportEnum
    var allPoints by rememberSaveable { mutableStateOf(sport != Sport.TENNIS) }
    val rows = evs.indices.reversed().filter { i -> board.lines.getOrNull(i) != null && (allPoints || board.lines[i].major) }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 80.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Diễn biến", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (sport == Sport.TENNIS) FilterChip(!allPoints, { allPoints = !allPoints }, label = { Text("Chỉ game") })
            }
        }
        if (rows.isEmpty()) item {
            Text(
                if (sport == Sport.FOOTBALL) "Chưa có bàn thắng. Chạm ô của đội ghi bàn: phút ghi bàn được lưu lại."
                else "Chưa có điểm nào. Mỗi điểm được lưu kèm thời gian trên đồng hồ.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        var lastSet = -1
        rows.forEach { i ->
            val line = board.lines[i]
            val e = evs[i]
            if (sport != Sport.FOOTBALL && line.setNo != lastSet) {
                lastSet = line.setNo
                val set = board.history.getOrNull(line.setNo - 1)
                item(key = "h$i") {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) {
                        Text("${sport.setWord} ${line.setNo}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        if (set != null) {
                            val prev = board.history.getOrNull(line.setNo - 2)?.endMs ?: 0
                            Text("${set.text()} · ${fmtDuration(set.endMs - prev)}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item(key = e.id) { EventLine(m, e, line, onClick = { if (e.side >= 0) onEdit(e) }) }
        }
        item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun EventLine(m: MatchRow, e: EventRow, line: com.samuelcruz.ghidiem.core.Line, onClick: () -> Unit) {
    if (e.side < 0) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f))
            Text("  ${line.time}  ${line.label}  ${line.score}  ", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.weight(1f))
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(line.time, Modifier.width(60.dp), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.size(10.dp).clip(CircleShape).background(Team.light(e.side)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val icon = when (e.kind) {
                Kind.GOAL -> "⚽ "
                Kind.YELLOW -> "🟨 "
                Kind.RED -> "🟥 "
                else -> ""
            }
            Text(icon + m.name(e.side) + if (e.note.isNotBlank()) " · ${e.note}" else "",
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (line.major) FontWeight.SemiBold else FontWeight.Normal)
            if (line.label.isNotEmpty() && e.kind == Kind.POINT) Text(line.label, style = MaterialTheme.typography.labelSmall,
                color = Team.color(e.side))
            else if (e.kind == Kind.YELLOW || e.kind == Kind.RED) Text(line.label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(line.score, style = MaterialTheme.typography.titleSmall,
            fontWeight = if (line.major) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun EventDialog(
    e: EventRow, m: MatchRow, board: Board, index: Int,
    onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: () -> Unit,
) {
    var note by remember(e.id) { mutableStateOf(e.note) }
    val line = board.lines.getOrNull(index)
    val what = when (e.kind) {
        Kind.GOAL -> "Bàn thắng"
        Kind.YELLOW -> "Thẻ vàng"
        Kind.RED -> "Thẻ đỏ"
        else -> "Điểm"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$what · ${m.name(e.side)}") },
        text = {
            Column {
                Text("Lúc ${line?.time ?: ""}" + (line?.score?.takeIf { it.isNotEmpty() }?.let { " · tỉ số $it" } ?: ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(note, { note = it }, label = { Text(if (m.sportEnum == Sport.FOOTBALL) "Cầu thủ" else "Ghi chú") },
                    singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Spacer(Modifier.height(8.dp))
                TextButton(onDelete) { Text("Xoá ${what.lowercase()} này", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton({ onSave(note) }) { Text("Lưu") } },
        dismissButton = { TextButton(onDismiss) { Text("Đóng") } },
    )
}

@Composable
private fun RenameDialog(m: MatchRow, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var a by remember { mutableStateOf(m.nameA) }
    var b by remember { mutableStateOf(m.nameB) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đổi tên") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a, { a = it }, singleLine = true, label = { Text("Bên đỏ") })
                OutlinedTextField(b, { b = it }, singleLine = true, label = { Text("Bên xanh") })
            }
        },
        confirmButton = { TextButton({ if (a.isNotBlank() && b.isNotBlank()) onSave(a, b) }) { Text("Lưu") } },
        dismissButton = { TextButton(onDismiss) { Text("Thôi") } },
    )
}

private val shareDate = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi"))

/** Plain-text result for Zalo / Messenger. */
private fun share(context: Context, m: MatchRow, board: Board, evs: List<EventRow>) {
    val sport = m.sportEnum
    val (a, b) = board.score.split("-")
    val text = buildString {
        appendLine("${sport.emoji} ${sport.title} · ${shareDate.format(Date(if (m.startedAt > 0) m.startedAt else m.createdAt))}")
        appendLine("${m.nameA} $a – $b ${m.nameB}")
        if (sport != Sport.FOOTBALL && board.history.isNotEmpty()) {
            appendLine("${sport.setWord}: " + board.history.joinToString(" · ") { it.text() })
        }
        val total = m.clockAt(System.currentTimeMillis())
        if (total > 0) appendLine("Thời gian: ${fmtDuration(total)}")
        if (sport == Sport.FOOTBALL) {
            evs.forEachIndexed { i, e ->
                if (e.side < 0) return@forEachIndexed
                val icon = when (e.kind) { Kind.GOAL -> "⚽"; Kind.YELLOW -> "🟨"; Kind.RED -> "🟥"; else -> "" }
                appendLine("${board.lines[i].time} $icon ${m.name(e.side)}" + if (e.note.isNotBlank()) " (${e.note})" else "")
            }
        }
        if (!m.finished && !board.finished) append("(đang diễn ra)")
    }.trim()
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Chia sẻ kết quả"))
}
