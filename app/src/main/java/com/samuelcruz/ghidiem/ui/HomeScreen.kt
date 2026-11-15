package com.samuelcruz.ghidiem.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuelcruz.ghidiem.core.Sport
import com.samuelcruz.ghidiem.core.fmtDuration
import com.samuelcruz.ghidiem.data.MatchRow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(onNew: (Sport) -> Unit, onOpen: (Long) -> Unit) {
    val app = app()
    val matches by app.db.matches().all().collectAsState(initial = null)
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<MatchRow?>(null) }

    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val old = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = false
        onDispose { if (old != null) bars.isAppearanceLightStatusBars = old }
    }

    val live = matches.orEmpty().filter { !it.finished }
    val done = matches.orEmpty().filter { it.finished && (filter == null || it.sport == filter) }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { Hero(onNew) }
        if (live.isNotEmpty()) {
            item { SectionTitle("Đang diễn ra") }
            items(live, key = { it.id }) { MatchCard(it, onClick = { onOpen(it.id) }, onLongClick = { deleting = it }) }
        }
        item { SectionTitle("Lịch sử") }
        val sportsPlayed = matches.orEmpty().filter { it.finished }.map { it.sport }.distinct()
        if (sportsPlayed.size > 1) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(filter == null, { filter = null }, label = { Text("Tất cả") }) }
                items(sportsPlayed) { s ->
                    val sp = Sport.of(s)
                    FilterChip(filter == s, { filter = if (filter == s) null else s }, label = { Text("${sp.emoji} ${sp.title}") })
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (matches != null && done.isEmpty()) item {
            Text(
                "Chưa có trận nào kết thúc. Chọn một môn ở trên để bắt đầu ghi điểm.",
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(done, key = { it.id }) { MatchCard(it, onClick = { onOpen(it.id) }, onLongClick = { deleting = it }) }
        item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
    }

    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Xoá trận này?") },
            text = { Text("${m.nameA} – ${m.nameB} (${m.sportEnum.title}). Không lấy lại được.") },
            confirmButton = {
                TextButton({
                    app.scope.launch { app.repo.delete(m.id) }
                    deleting = null
                }) { Text("Xoá", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("Thôi") } },
        )
    }
}

@Composable
private fun Hero(onNew: (Sport) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(HeroBrush)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 20.dp, bottom = 22.dp),
    ) {
        Text("Ghi Điểm", Modifier.padding(horizontal = 20.dp), color = Color.White,
            style = MaterialTheme.typography.headlineLarge)
        Text("Chọn môn để bắt đầu trận mới", Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Sport.entries) { s ->
                Column(
                    Modifier
                        .width(92.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable { onNew(s) }
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Text(s.emoji, fontSize = 28.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(s.title, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium)
}

private val dateFmt = SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.forLanguageTag("vi"))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatchCard(m: MatchRow, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(m.sportEnum.emoji, fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                NameLine(m, 0)
                NameLine(m, 1)
                val sub = buildList {
                    if (m.detail.isNotEmpty()) add(m.detail)
                    if (m.doubles) add("đôi")
                    if (!m.finished) add("Đang chơi") else {
                        if (m.clockBaseMs > 60_000) add(fmtDuration(m.clockBaseMs))
                    }
                    add(dateFmt.format(Date(if (m.startedAt > 0) m.startedAt else m.createdAt)))
                }.joinToString(" · ")
                Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(m.score.replace("-", " – "), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun NameLine(m: MatchRow, side: Int) {
    val win = m.finished && m.winner == side
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Team.light(side)))
        Spacer(Modifier.width(6.dp))
        Text(
            m.name(side) + if (win) "  🏆" else "",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (win) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
