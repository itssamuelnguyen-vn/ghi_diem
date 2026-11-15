package com.samuelcruz.ghidiem.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samuelcruz.ghidiem.core.Record
import com.samuelcruz.ghidiem.core.Result
import com.samuelcruz.ghidiem.core.Sport
import com.samuelcruz.ghidiem.core.Standings
import com.samuelcruz.ghidiem.core.splitPlayers
import com.samuelcruz.ghidiem.data.decided
import com.samuelcruz.ghidiem.data.result

/** Every name that played: matches, wins, win rate; tap one to see who they beat and lost to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayersScreen(onBack: () -> Unit) {
    val app = app()
    val finished by app.db.matches().finished().collectAsState(initial = emptyList())
    var sport by rememberSaveable { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val results = remember(finished, sport) {
        finished.filter { it.decided && (sport == null || it.sport == sport) }.map { it.result() }
    }
    val players = remember(results) { Standings.players(results) }
    val sports = finished.map { it.sport }.distinct()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Người chơi") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (sports.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(sport == null, { sport = null }, label = { Text("Tất cả") }) }
            items(sports) { s ->
                val sp = Sport.of(s)
                FilterChip(sport == s, { sport = if (sport == s) null else s }, label = { Text("${sp.emoji} ${sp.title}") })
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)) {
            if (players.isEmpty()) item {
                Text("Chưa có trận nào kết thúc. Thống kê thắng / thua theo tên sẽ hiện ở đây.",
                    Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(players, key = { it.name }) { r ->
                PlayerCard(r, open == r.name, results) { open = if (open == r.name) null else r.name }
            }
        }
    }
}

@Composable
private fun PlayerCard(r: Record, expanded: Boolean, results: List<Result>, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center) {
                    Text(r.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${r.played} trận · ${r.won} thắng · ${r.lost} thua" + if (r.drawn > 0) " · ${r.drawn} hoà" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${r.winPercent}%", style = MaterialTheme.typography.titleLarge,
                    color = if (r.winPercent >= 50) Team.color(1) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth(r.winPercent / 100f).height(6.dp).background(Team.light(1)))
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    HorizontalDivider()
                    Text("Đối đầu", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    headToHead(r.name, results).forEach { (opp, rec) ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(opp, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${rec.won} – ${rec.lost}" + if (rec.drawn > 0) " (${rec.drawn} hoà)" else "",
                                fontWeight = FontWeight.SemiBold,
                                color = if (rec.won > rec.lost) Team.color(1) else if (rec.won < rec.lost) Team.color(0)
                                else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

/** Opponents of [name] (team names) with [name]'s wins / losses against each. */
private fun headToHead(name: String, results: List<Result>): List<Pair<String, Record>> {
    val rows = mutableMapOf<String, Record>()
    results.forEach { r ->
        for (side in 0..1) {
            val team = if (side == 0) r.nameA else r.nameB
            if (team == name || name in splitPlayers(team)) {
                val opp = if (side == 0) r.nameB else r.nameA
                rows[opp] = (rows[opp] ?: Record(opp)).add(r, side)
            }
        }
    }
    return rows.entries.sortedByDescending { it.value.played }.map { it.key to it.value }
}
