package com.samuelcruz.ghidiem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.samuelcruz.ghidiem.core.Kind
import com.samuelcruz.ghidiem.core.Line
import com.samuelcruz.ghidiem.core.Sport
import com.samuelcruz.ghidiem.core.fmtDuration
import com.samuelcruz.ghidiem.core.periodName
import com.samuelcruz.ghidiem.data.EventRow
import com.samuelcruz.ghidiem.data.Repo
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MatchScreen(
    id: Long,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onStats: (Long) -> Unit,
    onBoard: (Long) -> Unit,
) {
    val app = app()
    val ui = rememberMatch(id)
    if (ui == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val m = ui.m
    val evs = ui.evs
    val board = ui.board
    val sport = ui.sport
    val done = ui.done
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Writes run in the app scope so a quick "back" does not cancel them.
    fun act(f: suspend Repo.() -> Unit) {
        app.scope.launch { app.repo.f() }
    }

    var editing by remember { mutableStateOf<EventRow?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var subFor by remember { mutableStateOf<Int?>(null) }
    var askShootout by remember { mutableStateOf(false) }
    /** Id of the newest event whose alert the user closed: only alerts of newer events are shown. */
    var dismissedId by rememberSaveable { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }

    fun score(side: Int, kind: Int) {
        if (done) return
        uiScope.launch {
            val ok = app.tapScore(m, side, kind, { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }) {
                snackbar.showSnackbar(it)
            }
            if (ok && Kind.named(kind) && kind != Kind.SUB) {
                val ev = app.scope.async { app.db.matches().eventsOnce(m.id).lastOrNull { it.kind == kind } }.await() ?: return@launch
                val what = Kind.title(kind)
                val r = snackbar.showSnackbar("$what · ${m.name(side)}", "Ghi tên", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) editing = ev
            }
        }
    }

    fun tap(side: Int) = score(side, if (sport == Sport.FOOTBALL) Kind.GOAL else Kind.POINT)

    MatchFeedback(ui, onSide = ::tap)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            val wide = maxWidth > maxHeight
            val panelHeight = min(maxHeight * 0.42f, 360.dp)
            val top: @Composable ColumnScope.() -> Unit = {
                TopBar(ui, onBack,
                    onSwap = { act { swap(m.id) } },
                    onStats = { onStats(m.id) },
                    onBoard = { onBoard(m.id) },
                    onRename = { renaming = true },
                    onFinish = { confirmFinish = true },
                    onReopen = { act { reopen(m.id) } },
                    onShareText = { shareText(context, ui) },
                    onShareImage = { shareImage(context, ui) },
                    onDelete = { confirmDelete = true })
                ClockBar(ui, onToggle = { act { toggleClock(m.id) } })
                Banner(ui, showAlert = ui.lastId > dismissedId,
                    onDismiss = { dismissedId = ui.lastId },
                    onSwap = { act { swap(m.id) }; dismissedId = ui.lastId },
                    onRematch = {
                        if (!busy) {
                            busy = true // one tap = one new match
                            uiScope.launch { app.scope.async { app.repo.rematch(m.id) }.await()?.let(onOpen) }
                        }
                    },
                    onShare = { shareImage(context, ui) })
            }
            val panels: @Composable (Modifier) -> Unit = { mod ->
                Row(mod.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ui.order.forEach { side -> Panel(side, ui, Modifier.weight(1f).fillMaxHeight()) { tap(side) } }
                }
            }
            val controls: @Composable () -> Unit = {
                Controls(ui,
                    // A match ended by hand is closed; one won by the rules can take back its last point.
                    canUndo = evs.any { Kind.undoable(it.kind) } && !m.manualEnd,
                    onUndo = {
                        if (app.prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        act { undo(m.id) }
                    },
                    onEvent = ::score,
                    onSub = { subFor = it },
                    onPeriod = { start -> act { period(m.id, start) } },
                    onShootout = { askShootout = true },
                    onFinish = { confirmFinish = true })
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1.5f).fillMaxHeight()) {
                        top()
                        panels(Modifier.weight(1f).fillMaxWidth())
                        controls()
                    }
                    Timeline(ui, Modifier.weight(1f).fillMaxHeight()) { editing = it }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    top()
                    panels(Modifier.height(panelHeight).fillMaxWidth())
                    controls()
                    Timeline(ui, Modifier.weight(1f).fillMaxWidth()) { editing = it }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
    }

    editing?.let { e ->
        EventDialog(e, ui, evs.indexOf(e), onDismiss = { editing = null },
            onSave = { note -> act { setNote(e, note) }; editing = null },
            onDelete = { act { deleteEvent(e) }; editing = null })
    }
    subFor?.let { side ->
        SubDialog(m.name(side), onDismiss = { subFor = null }) { out, inn ->
            subFor = null
            app.scope.launch { app.repo.score(m.id, side, Kind.SUB, listOf(out, inn).filter { it.isNotBlank() }.joinToString(" → ")) }
        }
    }
    if (askShootout) AlertDialog(
        onDismissRequest = { askShootout = false },
        title = { Text("Đá luân lưu") },
        text = { Text("Mỗi đội 5 lượt, sau đó đá từng lượt đến khi phân thắng thua. Đội nào đá trước?") },
        confirmButton = { TextButton({ askShootout = false; act { startShootout(m.id, 0) } }) { Text(m.nameA) } },
        dismissButton = { TextButton({ askShootout = false; act { startShootout(m.id, 1) } }) { Text(m.nameB) } },
    )
    if (renaming) RenameDialog(m.nameA, m.nameB, m.doubles, onDismiss = { renaming = false }) { a, b ->
        act { rename(m.id, a, b) }; renaming = false
    }
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
    ui: MatchUi, onBack: () -> Unit, onSwap: () -> Unit, onStats: () -> Unit, onBoard: () -> Unit,
    onRename: () -> Unit, onFinish: () -> Unit, onReopen: () -> Unit, onShareText: () -> Unit,
    onShareImage: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val m = ui.m
    val board = ui.board
    val sport = ui.sport
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
        Column(Modifier.weight(1f)) {
            Text("${sport.emoji}  ${sport.title}" + if (m.doubles) " · đôi" else "", style = MaterialTheme.typography.titleMedium,
                maxLines = 1)
            val sub = when {
                ui.done -> "Đã kết thúc"
                board.shootout != null -> "Đá luân lưu"
                sport == Sport.FOOTBALL -> if (board.period == 0) "Chưa bắt đầu"
                else periodName(m.rulesObj, board.period).replaceFirstChar { it.uppercase() } + if (board.periodOpen) "" else " · đã hết"
                else -> "${sport.setWord} ${board.setNo}" + (board.info?.let { " · $it" } ?: "")
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        IconButton(onStats) { Icon(Icons.Filled.BarChart, "Thống kê trận") }
        IconButton(onBoard) { Icon(Icons.Filled.Fullscreen, "Bảng điểm toàn màn hình") }
        IconButton(onSwap) { Icon(Icons.Filled.SwapHoriz, "Đổi bên trái / phải") }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "Thêm") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Đổi tên") }, { menu = false; onRename() })
                DropdownMenuItem({ Text("Chia sẻ ảnh kết quả") }, { menu = false; onShareImage() })
                DropdownMenuItem({ Text("Chia sẻ dạng chữ") }, { menu = false; onShareText() })
                if (!ui.done) DropdownMenuItem({ Text(if (sport == Sport.FOOTBALL) "Kết thúc trận" else "Dừng trận ở đây") },
                    { menu = false; onFinish() })
                if (m.manualEnd) DropdownMenuItem({ Text("Chơi tiếp trận này") }, { menu = false; onReopen() })
                DropdownMenuItem({ Text("Xoá trận", color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete() })
            }
        }
    }
}

/** The stopwatch: whole match for rally sports (+ this set), match minute for football. */
@Composable
private fun ClockBar(ui: MatchUi, onToggle: () -> Unit) {
    val m = ui.m
    val c = clockText(ui)
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(c.main, style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp),
                        color = if (m.running || ui.done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    c.extra?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, Modifier.padding(bottom = 4.dp), color = Amber, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(c.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val canToggle = !ui.done && ui.board.shootout == null && (ui.sport != Sport.FOOTBALL || ui.board.periodOpen)
            if (canToggle) FilledTonalIconButton(onToggle, Modifier.size(52.dp)) {
                Icon(if (m.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (m.running) "Tạm dừng đồng hồ" else "Chạy đồng hồ", Modifier.size(28.dp))
            }
        }
    }
}

/** One line under the clock: the result, a time-out countdown, what just happened, or who is at match point. */
@Composable
private fun Banner(
    ui: MatchUi, showAlert: Boolean,
    onDismiss: () -> Unit, onSwap: () -> Unit, onRematch: () -> Unit, onShare: () -> Unit,
) {
    val m = ui.m
    val board = ui.board
    val alert = board.alert?.takeIf { showAlert }
    // Time-out countdown (ticks on its own, the match clock does not run during a time-out).
    val last = ui.evs.lastOrNull()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val timeoutEnd = last?.takeIf { it.kind == Kind.TIMEOUT }?.let { it.at + ui.sport.timeoutSeconds * 1000L }
    LaunchedEffect(timeoutEnd) {
        while (timeoutEnd != null && System.currentTimeMillis() < timeoutEnd) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    when {
        ui.done -> {
            val w = board.winner.takeIf { it >= 0 } ?: m.winner
            BannerBox(if (w >= 0) Team.brush(w) else HeroBrush) {
                Text(resultText(ui), Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onShare) { Text("Chia sẻ", color = Color.White) }
                if (m.tournamentId == 0L) FilledTonalButton(onRematch) { Text("Đấu lại") }
            }
        }
        timeoutEnd != null && now < timeoutEnd -> BannerBox(SolidColor(MaterialTheme.colorScheme.primaryContainer)) {
            val left = ((timeoutEnd - now + 999) / 1000).toInt()
            Text("⏸  Hội ý · ${m.name(last.side)}", Modifier.weight(1f).padding(vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("0:%02d".format(left), Modifier.padding(end = 12.dp), style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        alert != null -> BannerBox(SolidColor(Amber)) {
            Text(alert, Modifier.weight(1f), color = Color(0xFF451A03), style = MaterialTheme.typography.titleMedium)
            if ("Đổi sân" in alert) TextButton(onSwap) { Text("Đổi bên", color = Color(0xFF451A03)) }
            TextButton(onDismiss) { Text("OK", color = Color(0xFF451A03)) }
        }
        board.callouts.isNotEmpty() -> BannerBox(SolidColor(MaterialTheme.colorScheme.secondaryContainer)) {
            Text(board.callouts.joinToString("  ·  ") { "${it.text.uppercase()} ${m.name(it.side)}" },
                Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BannerBox(brush: Brush, content: @Composable RowScope.() -> Unit) {
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

@Composable
private fun Controls(
    ui: MatchUi, canUndo: Boolean, onUndo: () -> Unit, onEvent: (Int, Int) -> Unit, onSub: (Int) -> Unit,
    onPeriod: (Boolean) -> Unit, onShootout: () -> Unit, onFinish: () -> Unit,
) {
    val m = ui.m
    val board = ui.board
    val sport = ui.sport
    val r = m.rulesObj
    val done = ui.done
    val order = ui.order
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        // Per-side extras under each panel: cards / substitution / own goal (football), time-outs.
        val footballExtras = sport == Sport.FOOTBALL && !done && board.period > 0 && board.shootout == null
        val timeouts = sport.timeouts > 0 && !done
        if (footballExtras || timeouts) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                order.forEach { side ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (footballExtras) {
                            OutlinedButton({ onEvent(side, Kind.YELLOW) }, Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("🟨") }
                            OutlinedButton({ onEvent(side, Kind.RED) }, Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("🟥") }
                            var more by remember { mutableStateOf(false) }
                            Box(Modifier.weight(1f)) {
                                OutlinedButton({ more = true }, Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) { Text("⋯") }
                                DropdownMenu(more, { more = false }) {
                                    DropdownMenuItem({ Text("🔁  Thay người") }, { more = false; onSub(side) })
                                    // An own goal by this team counts for the other one.
                                    DropdownMenuItem({ Text("🥅  Phản lưới nhà (bàn cho ${m.name(1 - side)})") },
                                        { more = false; onEvent(1 - side, Kind.OWN_GOAL) })
                                }
                            }
                        } else {
                            val left = sport.timeouts - board.timeouts[side]
                            OutlinedButton({ onEvent(side, Kind.TIMEOUT) }, Modifier.weight(1f), enabled = left > 0,
                                contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text("⏸ Hội ý ($left)", maxLines = 1)
                            }
                        }
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
            when {
                sport == Sport.FOOTBALL && !done && board.shootout != null -> {
                    val next = board.shootoutNext.coerceAtLeast(0)
                    Text("Lượt: ${m.name(next)}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Button({ onEvent(next, Kind.PEN_SCORED) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("✅ Vào") }
                    OutlinedButton({ onEvent(next, Kind.PEN_MISSED) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("❌ Trượt") }
                }
                sport == Sport.FOOTBALL && !done -> {
                    val p = board.period
                    when {
                        board.periodOpen -> Button({ onPeriod(false) }, Modifier.weight(1f)) { Text("Kết thúc ${periodName(r, p)}") }
                        p < r.periods -> Button({ onPeriod(true) }, Modifier.weight(1f)) { Text("Bắt đầu ${periodName(r, p + 1)}") }
                        else -> {
                            var more by remember { mutableStateOf(false) }
                            Box(Modifier.weight(1f)) {
                                OutlinedButton({ more = true }, Modifier.fillMaxWidth()) { Text("Đá tiếp…") }
                                DropdownMenu(more, { more = false }) {
                                    if (p < r.periods + 2) DropdownMenuItem(
                                        { Text(if (p == r.periods) "Đá hiệp phụ 1" else "Đá hiệp phụ 2") },
                                        { more = false; onPeriod(true) },
                                    )
                                    DropdownMenuItem({ Text("Đá luân lưu 11m") }, { more = false; onShootout() })
                                }
                            }
                            Button(onFinish, Modifier.weight(1f)) { Text("Kết thúc trận") }
                        }
                    }
                }
                else -> {
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
}

/** Every point / goal with its time, newest first, grouped by set. Tennis hides single points by default. */
@Composable
private fun Timeline(ui: MatchUi, modifier: Modifier, onEdit: (EventRow) -> Unit) {
    val board = ui.board
    val evs = ui.evs
    val sport = ui.sport
    var allPoints by rememberSaveable { mutableStateOf(sport != Sport.TENNIS) }
    val rows = evs.indices.reversed().filter { i ->
        val l = board.lines.getOrNull(i)
        l != null && (l.counted || Kind.divider(evs[i].kind)) && (allPoints || l.major)
    }
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
            item(key = e.id) { EventLine(ui, e, line, onClick = { if (!Kind.divider(e.kind)) onEdit(e) }) }
        }
        item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun EventLine(ui: MatchUi, e: EventRow, line: Line, onClick: () -> Unit) {
    val m = ui.m
    if (Kind.divider(e.kind)) {
        val text = if (e.kind == Kind.SHOOTOUT_START) "  Đá luân lưu · ${m.name(e.side)} đá trước  "
        else "  ${line.time}  ${line.label}  ${line.score}  "
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(Kind.icon(e.kind) + m.name(e.side) + if (e.note.isNotBlank()) " · ${e.note}" else "",
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (line.major) FontWeight.SemiBold else FontWeight.Normal)
            when {
                line.label.isNotEmpty() && e.kind == Kind.POINT -> Text(line.label, style = MaterialTheme.typography.labelSmall,
                    color = Team.color(e.side))
                e.kind != Kind.POINT && e.kind != Kind.GOAL -> Text(line.label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(line.score, style = MaterialTheme.typography.titleSmall,
            fontWeight = if (line.major) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun EventDialog(
    e: EventRow, ui: MatchUi, index: Int,
    onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: () -> Unit,
) {
    val m = ui.m
    var note by remember(e.id) { mutableStateOf(e.note) }
    val line = ui.board.lines.getOrNull(index)
    val what = Kind.title(e.kind)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$what · ${m.name(e.side)}") },
        text = {
            Column {
                Text("Lúc ${line?.time ?: ""}" + (line?.score?.takeIf { it.isNotEmpty() }?.let { " · tỉ số $it" } ?: ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                val label = when {
                    e.kind == Kind.OWN_GOAL -> "Cầu thủ phản lưới (đội ${m.name(1 - e.side)})"
                    ui.sport == Sport.FOOTBALL -> "Cầu thủ"
                    else -> "Ghi chú"
                }
                OutlinedTextField(note, { note = it }, label = { Text(label) },
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
private fun SubDialog(team: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var out by remember { mutableStateOf("") }
    var inn by remember { mutableStateOf("") }
    val caps = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thay người · $team") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(out, { out = it }, singleLine = true, label = { Text("Ra sân") }, keyboardOptions = caps)
                OutlinedTextField(inn, { inn = it }, singleLine = true, label = { Text("Vào sân") }, keyboardOptions = caps)
            }
        },
        confirmButton = { TextButton({ onSave(out.trim(), inn.trim()) }) { Text("Ghi") } },
        dismissButton = { TextButton(onDismiss) { Text("Thôi") } },
    )
}

@Composable
fun RenameDialog(nameA: String, nameB: String, doubles: Boolean, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var a by remember { mutableStateOf(nameA) }
    var b by remember { mutableStateOf(nameB) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đổi tên") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a, { a = it }, singleLine = true, label = { Text("Bên đỏ") })
                OutlinedTextField(b, { b = it }, singleLine = true, label = { Text("Bên xanh") })
                if (doubles) Text("Đánh đôi: giữ dạng “Người 1 / Người 2” để app biết ai giao.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton({ if (a.isNotBlank() && b.isNotBlank()) onSave(a, b) }) { Text("Lưu") } },
        dismissButton = { TextButton(onDismiss) { Text("Thôi") } },
    )
}
