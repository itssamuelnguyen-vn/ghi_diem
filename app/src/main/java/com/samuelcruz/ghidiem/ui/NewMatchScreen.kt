package com.samuelcruz.ghidiem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuelcruz.ghidiem.core.FinalSet
import com.samuelcruz.ghidiem.core.Rules
import com.samuelcruz.ghidiem.core.Sport
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewMatchScreen(sport: Sport, onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val d = sport.defaultRules()
    val team = sport == Sport.FOOTBALL || sport == Sport.VOLLEYBALL
    var nameA by rememberSaveable { mutableStateOf("") }
    var nameB by rememberSaveable { mutableStateOf("") }
    var bestOf by rememberSaveable { mutableIntStateOf(d.bestOf) }
    var points by rememberSaveable { mutableIntStateOf(d.points) }
    var deciding by rememberSaveable { mutableIntStateOf(d.decidingPoints) }
    var capOn by rememberSaveable { mutableStateOf(d.cap > 0) }
    var games by rememberSaveable { mutableIntStateOf(d.games) }
    var noAd by rememberSaveable { mutableStateOf(d.noAd) }
    var finalSet by rememberSaveable { mutableStateOf(d.finalSet) }
    var periodMin by rememberSaveable { mutableIntStateOf(d.periodMinutes) }
    var periods by rememberSaveable { mutableIntStateOf(d.periods) }
    var extraMin by rememberSaveable { mutableIntStateOf(d.extraMinutes) }
    var firstServer by rememberSaveable { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    val recent by app.db.matches().recentNames().collectAsState(initial = emptyList())

    val defA = if (team) "Đội A" else "Người chơi A"
    val defB = if (team) "Đội B" else "Người chơi B"

    fun rules() = Rules(
        bestOf = bestOf,
        points = points,
        decidingPoints = if (sport == Sport.VOLLEYBALL) deciding else points,
        // Badminton's cap is 9 points above the target (21 → 30, 15 → 24, 11 → 20).
        cap = if (sport == Sport.BADMINTON && capOn) points + 9 else 0,
        games = games,
        noAd = noAd,
        // The final-set choice is hidden for a 1-set match: that set always ends in a normal tie-break.
        finalSet = if (bestOf > 1) finalSet else FinalSet.TIEBREAK,
        periodMinutes = periodMin,
        periods = periods,
        extraMinutes = extraMin,
    )

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("${sport.emoji}  ${sport.title} · Trận mới") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Card("Hai bên") {
                NameField(0, nameA, defA) { nameA = it }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IconButton({ val t = nameA; nameA = nameB; nameB = t }) { Icon(Icons.Filled.SwapVert, "Đổi chỗ hai bên") }
                }
                NameField(1, nameB, defB) { nameB = it }
                val picks = recent.filter { it.isNotBlank() && it != nameA && it != nameB && it !in defaultNames }
                if (picks.isNotEmpty()) {
                    Text("Tên đã dùng (chạm để điền vào ô trống)", Modifier.padding(top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        picks.forEach { n ->
                            FilterChip(false, { if (nameA.isBlank()) nameA = n else nameB = n }, label = { Text(n) })
                        }
                    }
                }
            }

            when (sport) {
                Sport.FOOTBALL -> Card("Thời gian") {
                    Label("Mỗi hiệp")
                    Chips(listOf(10, 15, 20, 25, 30, 35, 40, 45), periodMin, { "$it phút" }) { periodMin = it }
                    Label("Số hiệp")
                    Chips(listOf(1, 2), periods, { "$it hiệp" }) { periods = it }
                    Label("Hiệp phụ (nếu đá thêm)")
                    Chips(listOf(5, 10, 15), extraMin, { "$it phút" }) { extraMin = it }
                    Hint("Đồng hồ chạy theo từng hiệp; quá giờ thì hiện phút bù giờ (45+2'). Bạn tự bấm hết hiệp.")
                }
                Sport.TENNIS -> Card("Luật") {
                    Label("Số set")
                    Chips(listOf(1, 3, 5), bestOf, { if (it == 1) "1 set" else "Thắng ${it / 2 + 1}/$it set" }) { bestOf = it }
                    Label("Game mỗi set")
                    Chips(listOf(6, 4), games, { "$it game" }) { games = it }
                    Label("Khi 40-40")
                    Chips(listOf(false, true), noAd, { if (it) "No-ad (1 điểm quyết định)" else "Deuce / AD" }) { noAd = it }
                    if (bestOf > 1) {
                        Label("Set cuối")
                        Chips(FinalSet.entries, finalSet, { it.title }) { finalSet = it }
                    }
                    Hint("Hoà $games-$games thì đánh tie-break 7 điểm. Tự nhắc đổi sân sau game lẻ.")
                }
                else -> Card("Luật") {
                    Label("Số ${sport.setWord.lowercase()}")
                    val options = if (sport == Sport.TABLE_TENNIS) listOf(1, 3, 5, 7) else listOf(1, 3, 5)
                    Chips(options, bestOf, { if (it == 1) "1 ${sport.setWord.lowercase()}" else "Thắng ${it / 2 + 1}/$it" }) { bestOf = it }
                    Label("Điểm mỗi ${sport.setWord.lowercase()}")
                    val pts = when (sport) {
                        Sport.BADMINTON -> listOf(21, 15, 11)
                        Sport.VOLLEYBALL -> listOf(25, 21, 15)
                        else -> listOf(11, 21)
                    }
                    Chips(pts, points, { "$it điểm" }) { points = it }
                    if (sport == Sport.VOLLEYBALL && bestOf > 1) {
                        Label("Set quyết định")
                        Chips(listOf(15, 25), deciding, { "$it điểm" }) { deciding = it }
                    }
                    if (sport == Sport.BADMINTON) {
                        Label("Khi hoà ${points - 1}-${points - 1}")
                        Chips(listOf(true, false), capOn, { if (it) "Ai chạm ${points + 9} trước thắng" else "Hơn 2 điểm mới thắng" }) { capOn = it }
                    }
                    Hint(
                        when (sport) {
                            Sport.BADMINTON -> "Bên thắng pha cầu được giao. Nhắc nghỉ khi chạm ${(points + 1) / 2} và đổi sân ở ván quyết định."
                            Sport.VOLLEYBALL -> "Đội thắng pha bóng được giao. Đổi sân ở set quyết định khi một đội chạm ${(deciding + 1) / 2}."
                            else -> "Đổi giao mỗi 2 điểm, từ ${points - 1}-${points - 1} đổi mỗi điểm. Đổi sân ở ván quyết định khi chạm ${points / 2}."
                        },
                    )
                }
            }

            if (sport.hasServe) Card("Giao trước") {
                Chips(listOf(0, 1), firstServer, { if (it == 0) nameA.ifBlank { defA } else nameB.ifBlank { defB } }) { firstServer = it }
            }
            Spacer(Modifier.height(16.dp))
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Button(
                enabled = !creating,
                onClick = {
                    creating = true // a double tap must not create two matches
                    scope.launch {
                        val id = app.repo.create(sport, rules(), nameA.trim().ifBlank { defA }, nameB.trim().ifBlank { defB }, firstServer)
                        onCreated(id)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Vào trận", fontSize = 18.sp) }
        }
    }
}

/** Placeholder names, not worth suggesting. */
private val defaultNames = setOf("Đội A", "Đội B", "Người chơi A", "Người chơi B")

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun NameField(side: Int, value: String, placeholder: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(Team.brush(side)))
        Spacer(Modifier.width(10.dp))
        OutlinedTextField(
            value, onChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, Modifier.padding(top = 10.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(o == selected, { onPick(o) }, label = { Text(label(o)) }) }
    }
}
