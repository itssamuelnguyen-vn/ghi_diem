package com.samuelcruz.ghidiem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuelcruz.ghidiem.core.Fixtures
import com.samuelcruz.ghidiem.core.Record
import com.samuelcruz.ghidiem.core.Sport
import com.samuelcruz.ghidiem.core.Standings
import com.samuelcruz.ghidiem.data.MatchRow
import com.samuelcruz.ghidiem.data.TournamentRow
import com.samuelcruz.ghidiem.data.result
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentsScreen(onBack: () -> Unit, onNew: () -> Unit, onOpen: (Long) -> Unit) {
    val app = app()
    val list by app.db.matches().tournaments().collectAsState(initial = null)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Giải đấu") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Giải mới") })
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 96.dp)) {
            if (list?.isEmpty() == true) item {
                Text(
                    "Chưa có giải nào. Tạo giải vòng tròn (ai cũng gặp nhau, có bảng xếp hạng) hoặc loại trực tiếp (nhánh đấu, thua là dừng).",
                    Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(list.orEmpty(), key = { it.id }) { t ->
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(18.dp))
                        .clickable { onOpen(t.id) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center) { Text(t.sportEnum.emoji, fontSize = 22.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (t.format == TournamentRow.FORMAT_KNOCKOUT) "Loại trực tiếp" else "Vòng tròn") +
                                    " · ${t.names.size} ${if (t.doubles) "đôi" else "đội / người"} · ${t.sportEnum.title}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTournamentScreen(onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var sport by rememberSaveable { mutableStateOf(Sport.BADMINTON) }
    var format by rememberSaveable { mutableIntStateOf(TournamentRow.FORMAT_ROUND_ROBIN) }
    var doubles by rememberSaveable { mutableStateOf(false) }
    var entrants by rememberSaveable { mutableStateOf("") }
    var rules by rememberSaveable(saver = RulesSaver) { mutableStateOf(Sport.BADMINTON.defaultRules()) }
    var creating by remember { mutableStateOf(false) }
    val names = entrants.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val ok = names.size >= 2 && !creating

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Giải mới") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp)) {
            FormCard("Giải") {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Giải cầu lông cuối tuần") }, label = { Text("Tên giải") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Label("Môn")
                Chips(Sport.entries, sport, { "${it.emoji} ${it.title}" }) {
                    sport = it
                    rules = it.defaultRules()
                    if (!it.hasDoubles) doubles = false
                }
                Label("Thể thức")
                Chips(listOf(TournamentRow.FORMAT_ROUND_ROBIN, TournamentRow.FORMAT_KNOCKOUT), format,
                    { if (it == TournamentRow.FORMAT_KNOCKOUT) "Loại trực tiếp" else "Vòng tròn" }) { format = it }
                if (sport.hasDoubles) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Đánh đôi", Modifier.weight(1f))
                    Switch(doubles, { doubles = it })
                }
            }
            FormCard(if (doubles) "Các đôi (mỗi dòng một đôi)" else "Đội / người chơi (mỗi dòng một)") {
                OutlinedTextField(entrants, { entrants = it }, Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text(if (doubles) "Nam / Hùng\nLan / Mai\nTuấn / Phong" else "Nam\nLan\nTuấn\nPhong") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Hint(
                    "${names.size} ${if (doubles) "đôi" else "người / đội"}. " + when {
                        names.size < 2 -> "Cần ít nhất 2."
                        format == TournamentRow.FORMAT_ROUND_ROBIN -> "${names.size * (names.size - 1) / 2} trận, ai cũng gặp nhau một lần."
                        else -> "${Fixtures.rounds(names.size)} vòng. Thứ tự dòng là hạt giống: người đầu gặp người cuối; nếu lẻ, hạt giống cao được miễn vòng 1."
                    },
                )
            }
            RulesForm(sport, rules) { rules = it }
            Spacer(Modifier.height(16.dp))
        }
        Button(
            enabled = ok,
            onClick = {
                creating = true
                scope.launch {
                    val id = app.repo.createTournament(
                        TournamentRow(name = name.trim().ifBlank { "Giải ${sport.title.lowercase()}" }, sport = sport.name,
                            rules = rules.normalized(sport).toJson(), format = format, entrants = names.joinToString("\n"),
                            doubles = doubles && sport.hasDoubles, createdAt = System.currentTimeMillis()),
                    )
                    onCreated(id)
                }
            },
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp).height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text("Tạo giải", fontSize = 18.sp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentScreen(id: Long, onBack: () -> Unit, onMatch: (Long) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val dao = app.db.matches()
    val t by remember(id) { dao.watchTournament(id) }.collectAsState(initial = null)
    val matches by remember(id) { dao.ofTournament(id) }.collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val tour = t
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = {
                Column {
                    Text(tour?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (tour != null) Text(
                        "${tour.sportEnum.emoji} ${tour.sportEnum.title} · " +
                            if (tour.format == TournamentRow.FORMAT_KNOCKOUT) "Loại trực tiếp" else "Vòng tròn",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
            actions = {
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "Thêm") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Xoá giải", color = MaterialTheme.colorScheme.error) }, { menu = false; confirmDelete = true })
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (tour == null) return
        val names = tour.names
        val byFixture = matches.associateBy { it.fixture }
        fun open(key: String, a: Int, b: Int) {
            if (a < 0 || b < 0) return
            scope.launch { onMatch(app.repo.fixtureMatch(tour, key, names[a], names[b])) }
        }
        if (tour.format == TournamentRow.FORMAT_ROUND_ROBIN) {
            PrimaryTabRow(tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Bảng xếp hạng") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Lịch đấu") })
            }
            val fixtures = remember(names.size) { Fixtures.roundRobin(names.size) }
            val results = matches.filter { it.finished }.mapNotNull { m ->
                val (a, b) = Fixtures.roundRobinPair(m.fixture) ?: return@mapNotNull null
                m.result(names.getOrNull(a) ?: return@mapNotNull null, names.getOrNull(b) ?: return@mapNotNull null)
            }
            val table = Standings.table(tour.sportEnum, names, results)
            if (tab == 0) StandingsTable(tour, table, done = results.size == fixtures.size && fixtures.isNotEmpty())
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                fixtures.groupBy { it.round }.forEach { (round, list) ->
                    item(key = "r$round") { RoundTitle("Lượt ${round + 1}") }
                    items(list, key = { it.key }) { f ->
                        FixtureRow(names[f.a], names[f.b], byFixture[f.key]) { open(f.key, f.a, f.b) }
                    }
                }
            }
        } else {
            val rounds = Fixtures.knockout(names.size) { key ->
                byFixture[key]?.takeIf { it.finished && it.winner >= 0 }?.winner
            }
            val champion = rounds.lastOrNull()?.singleOrNull()?.let { f ->
                byFixture[f.key]?.takeIf { it.finished && it.winner >= 0 }?.let { if (it.winner == 0) f.a else f.b }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (champion != null) item { ChampionCard(names[champion]) }
                rounds.forEachIndexed { r, list ->
                    item(key = "k$r") { RoundTitle(roundName(r, rounds.size)) }
                    items(list, key = { it.key }) { f ->
                        fun label(i: Int) = when (i) {
                            -2 -> "Miễn đấu"
                            -1 -> "Chờ kết quả"
                            else -> names[i]
                        }
                        val bye = f.a == -2 || f.b == -2
                        FixtureRow(label(f.a), label(f.b), byFixture[f.key], enabled = f.a >= 0 && f.b >= 0,
                            note = if (bye && (f.a >= 0 || f.b >= 0)) "Đi thẳng vào vòng sau" else null) { open(f.key, f.a, f.b) }
                    }
                }
                item {
                    Hint("Trận hoà không có người đi tiếp: với bóng đá hãy đá luân lưu trong trận để phân thắng thua.")
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Xoá giải?") },
        text = { Text("Giải và tất cả trận đã đấu trong giải sẽ bị xoá.") },
        confirmButton = {
            TextButton({
                confirmDelete = false
                app.scope.launch { app.repo.deleteTournament(id) }
                onBack()
            }) { Text("Xoá", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Thôi") } },
    )
}

private fun roundName(r: Int, total: Int) = when (total - r) {
    1 -> "Chung kết"
    2 -> "Bán kết"
    3 -> "Tứ kết"
    else -> "Vòng ${r + 1}"
}

@Composable
private fun RoundTitle(text: String) {
    Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ChampionCard(name: String) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp)).background(HeroBrush).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🏆", fontSize = 40.sp)
        Text("Vô địch", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
        Text(name, color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    }
}

/** One fixture: both names, its score once played, tap to play (or open) it. */
@Composable
private fun FixtureRow(a: String, b: String, m: MatchRow?, enabled: Boolean = true, note: String? = null, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                listOf(a, b).forEachIndexed { side, n ->
                    val win = m?.finished == true && m.winner == side
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Team.light(side)))
                        Spacer(Modifier.width(6.dp))
                        Text(n + if (win) "  🏆" else "", fontWeight = if (win) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val sub = note ?: when {
                    m == null -> if (enabled) "Chạm để đấu" else null
                    !m.finished -> "Đang đấu · ${m.detail.ifEmpty { m.score }}"
                    else -> m.detail.takeIf { it.isNotEmpty() }
                }
                sub?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (m != null && m.startedAt > 0) Text(m.score.replace("-", " – "), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun StandingsTable(t: TournamentRow, rows: List<Record>, done: Boolean) {
    val sport = t.sportEnum
    val football = sport == Sport.FOOTBALL
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (done && rows.isNotEmpty()) ChampionCard(rows.first().name)
        Surface(Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                val cols = if (football) listOf("Tr", "T", "H", "B", "BT", "HS", "Đ") else listOf("Tr", "T", "B", sport.setWord, "Điểm", "Đ")
                // Same width per column on every row, so the numbers line up.
                val widths = if (football) listOf(34, 34, 34, 34, 40, 40, 40) else listOf(34, 34, 34, 56, 72, 40)
                TableRow("#", "Tên", cols, widths, header = true)
                rows.forEachIndexed { i, r ->
                    val values = if (football) listOf(r.played, r.won, r.drawn, r.lost, r.forBig, r.forBig - r.againstBig, r.points(sport))
                        .map { it.toString() }
                    else listOf(r.played.toString(), r.won.toString(), r.lost.toString(), "${r.forBig}-${r.againstBig}",
                        "${r.forSmall}-${r.againstSmall}", r.points(sport).toString())
                    TableRow((i + 1).toString(), r.name, values, widths, highlight = i == 0 && r.played > 0)
                }
            }
        }
        Hint(
            if (football) "  Tr: trận · T/H/B: thắng/hoà/bại · BT: bàn thắng · HS: hiệu số · Đ: điểm (thắng 3, hoà 1)."
            else "  Tr: trận · T/B: thắng/bại · ${sport.setWord}: thắng-thua · Đ: số trận thắng. Bằng nhau xét hiệu ${sport.setWord.lowercase()}, rồi hiệu điểm.",
        )
    }
}

@Composable
private fun TableRow(rank: String, name: String, values: List<String>, widths: List<Int>, header: Boolean = false, highlight: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rank, Modifier.width(24.dp), style = style, color = color)
        Text(name, Modifier.width(140.dp), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (highlight) FontWeight.Bold else null)
        values.forEachIndexed { i, v ->
            Text(v, Modifier.width(widths[i].dp), style = style, color = color,
                textAlign = TextAlign.Center, fontWeight = if (i == values.lastIndex && !header) FontWeight.Bold else null)
        }
    }
}
