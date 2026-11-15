package com.samuelcruz.ghidiem.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samuelcruz.ghidiem.core.Chart
import com.samuelcruz.ghidiem.core.SideStats
import com.samuelcruz.ghidiem.core.Sport
import com.samuelcruz.ghidiem.core.Stats
import com.samuelcruz.ghidiem.core.fmtClock
import com.samuelcruz.ghidiem.core.fmtDuration

/** Numbers of one match: side by side comparison, set lengths, and how the score moved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(id: Long, onBack: () -> Unit) {
    val ui = rememberMatch(id)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Thống kê trận") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (ui == null) return
        val m = ui.m
        val sport = ui.sport
        val st = remember(ui.evs, ui.board) {
            Stats.of(m.setup, ui.evs.map { it.ev() }, ui.board, m.clockAt(System.currentTimeMillis()))
        }
        val (a, b) = st.sides
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Header: names and score.
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamName(m.nameA, 0, Modifier.weight(1f))
                    Text(ui.board.score.replace("-", " – "), style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 12.dp))
                    TeamName(m.nameB, 1, Modifier.weight(1f), end = true)
                }
                if (ui.board.detail.isNotEmpty()) Text(ui.board.detail, Modifier.fillMaxWidth().padding(top = 6.dp),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Section("So sánh") {
                val pointWord = if (sport == Sport.FOOTBALL) "Bàn thắng" else "Tổng điểm"
                Compare(pointWord, a.points, b.points)
                if (sport.hasServe) {
                    Compare("Thắng điểm khi giao", a.serveWon, b.serveWon,
                        a.servePercent?.let { "$it%" }, b.servePercent?.let { "$it%" })
                    Compare("Thắng điểm khi đỡ giao", a.receiveWon, b.receiveWon)
                    Compare("Chuỗi điểm dài nhất", a.longestRun, b.longestRun)
                }
                Compare(if (sport == Sport.FOOTBALL) "Dẫn cách biệt nhiều nhất" else "Dẫn nhiều nhất trong ${sport.setWord.lowercase()}",
                    a.biggestLead, b.biggestLead)
                if (sport == Sport.FOOTBALL) {
                    Compare("Thẻ vàng", a.yellow, b.yellow)
                    Compare("Thẻ đỏ", a.red, b.red)
                    Compare("Bị đuổi khỏi sân", ui.board.sentOff[0].size, ui.board.sentOff[1].size)
                    Compare("Thay người", a.subs, b.subs)
                }
                if (sport.timeouts > 0) Compare("Hội ý", a.timeouts, b.timeouts)
            }
            if (sport == Sport.FOOTBALL && (a.scorers.isNotEmpty() || b.scorers.isNotEmpty())) Section("Ghi bàn") {
                Row {
                    Scorers(a, Modifier.weight(1f))
                    Scorers(b, Modifier.weight(1f), end = true)
                }
            }
            Section("Thời gian") {
                Info("Cả trận", fmtClock(st.durationMs))
                st.setDurations.forEach { (name, ms) -> Info(name, fmtDuration(ms)) }
                st.avgPointMs?.let { Info("Trung bình mỗi điểm", "${it / 1000} giây") }
                Info(if (sport == Sport.FOOTBALL) "Số bàn" else "Số điểm đã đánh", st.totalPoints.toString())
            }
            st.charts.filter { it.diffs.size > 2 }.forEach { c -> Section("Diễn biến · ${c.title}") { ScoreChart(c, m.nameA, m.nameB) } }
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(16.dp))
        }
    }
}

@Composable
private fun Section(title: String? = null, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
private fun TeamName(name: String, side: Int, modifier: Modifier, end: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (end) Arrangement.End else Arrangement.Start) {
        if (!end) Box(Modifier.size(10.dp).clip(CircleShape).background(Team.light(side)))
        Text(name, Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = if (end) TextAlign.End else TextAlign.Start)
        if (end) Box(Modifier.size(10.dp).clip(CircleShape).background(Team.light(side)))
    }
}

/** "12  ███ Label ██  8" — two bars growing from the middle. */
@Composable
private fun Compare(label: String, a: Int, b: Int, aText: String? = null, bText: String? = null) {
    val total = (a + b).coerceAtLeast(1)
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.toString() + (aText?.let { " · $it" } ?: ""), Modifier.width(80.dp), style = MaterialTheme.typography.titleSmall,
                fontWeight = if (a > b) FontWeight.Bold else FontWeight.Normal)
            Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text((bText?.let { "$it · " } ?: "") + b.toString(), Modifier.width(80.dp), textAlign = TextAlign.End,
                style = MaterialTheme.typography.titleSmall, fontWeight = if (b > a) FontWeight.Bold else FontWeight.Normal)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            if (a > 0) Box(Modifier.weight(a.toFloat() / total).height(6.dp).background(Team.light(0)))
            if (a + b == 0) Spacer(Modifier.weight(1f))
            if (b > 0) Box(Modifier.weight(b.toFloat() / total).height(6.dp).background(Team.light(1)))
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Scorers(s: SideStats, modifier: Modifier, end: Boolean = false) {
    Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        s.scorers.entries.sortedByDescending { it.value }.forEach { (name, n) ->
            Text("⚽ $name" + if (n > 1) " ×$n" else "", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A minus B after each point: above the middle line red leads, below blue leads. */
@Composable
private fun ScoreChart(c: Chart, nameA: String, nameB: String) {
    val range = Stats.range(c).toFloat()
    val red = Team.light(0)
    val blue = Team.light(1)
    val axis = MaterialTheme.colorScheme.outline
    Column {
        Text("▲ $nameA dẫn", style = MaterialTheme.typography.labelSmall, color = red)
        Canvas(Modifier.fillMaxWidth().height(140.dp).padding(vertical = 4.dp)) {
            val mid = size.height / 2
            val step = size.width / (c.diffs.size - 1).coerceAtLeast(1)
            drawLine(axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2f)
            fun y(d: Int) = mid - d / range * (size.height / 2 - 4)
            val line = Path()
            c.diffs.forEachIndexed { i, d -> if (i == 0) line.moveTo(0f, y(d)) else line.lineTo(i * step, y(d)) }
            // Fill each point's segment by who leads.
            for (i in 1 until c.diffs.size) {
                val d = c.diffs[i]
                if (d == 0) continue
                val x0 = (i - 1) * step
                val x1 = i * step
                drawRect(if (d > 0) red.copy(alpha = 0.18f) else blue.copy(alpha = 0.18f),
                    topLeft = Offset(x0, if (d > 0) y(d) else mid), size = androidx.compose.ui.geometry.Size(x1 - x0, kotlin.math.abs(y(d) - mid)))
            }
            drawPath(line, axis.copy(alpha = 0.9f), style = Stroke(width = 4f))
        }
        Text("▼ $nameB dẫn", style = MaterialTheme.typography.labelSmall, color = blue)
    }
}
