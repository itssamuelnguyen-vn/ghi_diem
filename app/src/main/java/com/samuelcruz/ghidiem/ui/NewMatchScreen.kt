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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
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

/** Keeps a [Rules] across rotation (as JSON). */
val RulesSaver = Saver<MutableState<Rules>, String>(save = { it.value.toJson() }, restore = { mutableStateOf(Rules.fromJson(it)) })

/** The rules as they will be stored: fields the form hides get their consistent value. */
fun Rules.normalized(sport: Sport) = copy(
    decidingPoints = if (sport == Sport.VOLLEYBALL && bestOf > 1) decidingPoints else points,
    // Badminton's cap is 9 points above the target (21 → 30, 15 → 24, 11 → 20).
    cap = if (sport == Sport.BADMINTON && cap > 0) points + 9 else 0,
    // The final-set choice is hidden for a 1-set match: that set always ends in a normal tie-break.
    finalSet = if (bestOf > 1) finalSet else FinalSet.TIEBREAK,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewMatchScreen(sport: Sport, onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val team = sport == Sport.FOOTBALL || sport == Sport.VOLLEYBALL
    var nameA by rememberSaveable { mutableStateOf("") }
    var nameB by rememberSaveable { mutableStateOf("") }
    var a2 by rememberSaveable { mutableStateOf("") }
    var b2 by rememberSaveable { mutableStateOf("") }
    var doubles by rememberSaveable { mutableStateOf(false) }
    var rules by rememberSaveable(saver = RulesSaver) { mutableStateOf(sport.defaultRules()) }
    var firstServer by rememberSaveable { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    val recent by app.db.matches().recentNames().collectAsState(initial = emptyList())

    val defA = if (team) "Đội A" else if (doubles) "A1" else "Người chơi A"
    val defB = if (team) "Đội B" else if (doubles) "B1" else "Người chơi B"
    fun teamName(p1: String, p2: String, def1: String, def2: String) =
        if (doubles) "${p1.trim().ifBlank { def1 }} / ${p2.trim().ifBlank { def2 }}" else p1.trim().ifBlank { def1 }

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
            FormCard("Hai bên") {
                if (sport.hasDoubles) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Đánh đôi", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(doubles, { doubles = it })
                }
                NameField(0, nameA, defA) { nameA = it }
                if (doubles) NameField(0, a2, "A2") { a2 = it }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IconButton({
                        val t = nameA; nameA = nameB; nameB = t
                        val u = a2; a2 = b2; b2 = u
                    }) { Icon(Icons.Filled.SwapVert, "Đổi chỗ hai bên") }
                }
                NameField(1, nameB, defB) { nameB = it }
                if (doubles) NameField(1, b2, "B2") { b2 = it }
                val picks = recent.filter { it.isNotBlank() && it != nameA && it != nameB && it !in defaultNames && "/" !in it }
                if (picks.isNotEmpty()) {
                    Text("Tên đã dùng (chạm để điền vào ô trống)", Modifier.padding(top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        picks.forEach { n ->
                            FilterChip(false, {
                                when {
                                    nameA.isBlank() -> nameA = n
                                    doubles && a2.isBlank() -> a2 = n
                                    nameB.isBlank() -> nameB = n
                                    doubles && b2.isBlank() -> b2 = n
                                    else -> nameB = n
                                }
                            }, label = { Text(n) })
                        }
                    }
                }
                if (doubles) Hint(
                    when (sport) {
                        Sport.BADMINTON -> "Người đầu tiên của mỗi đội đứng ô phải lúc bắt đầu. App theo dõi ai giao, ai đỡ và đổi ô."
                        Sport.TABLE_TENNIS -> "Thứ tự giao: A1 → B1 → A2 → B2. Người đầu tiên của mỗi đội giao trước."
                        else -> "Thứ tự giao theo game: A1, B1, A2, B2. Người 1 của bên đỡ đứng bên phải (deuce)."
                    },
                )
            }

            RulesForm(sport, rules) { rules = it }

            if (sport.hasServe) FormCard(if (sport == Sport.FOOTBALL) "Giao bóng" else "Giao trước") {
                Chips(listOf(0, 1), firstServer, { if (it == 0) teamName(nameA, a2, defA, "A2") else teamName(nameB, b2, defB, "B2") }) {
                    firstServer = it
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Button(
                enabled = !creating,
                onClick = {
                    creating = true // a double tap must not create two matches
                    scope.launch {
                        val id = app.repo.create(sport, rules.normalized(sport), teamName(nameA, a2, defA, "A2"),
                            teamName(nameB, b2, defB, "B2"), firstServer, doubles && sport.hasDoubles)
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

/** The rule choices of [sport] (also used when creating a tournament). */
@Composable
fun RulesForm(sport: Sport, rules: Rules, onChange: (Rules) -> Unit) {
    val r = rules
    when (sport) {
        Sport.FOOTBALL -> FormCard("Thời gian") {
            Label("Mỗi hiệp")
            Chips(listOf(10, 15, 20, 25, 30, 35, 40, 45), r.periodMinutes, { "$it phút" }) { onChange(r.copy(periodMinutes = it)) }
            Label("Số hiệp")
            Chips(listOf(1, 2), r.periods, { "$it hiệp" }) { onChange(r.copy(periods = it)) }
            Label("Hiệp phụ (nếu đá thêm)")
            Chips(listOf(5, 10, 15), r.extraMinutes, { "$it phút" }) { onChange(r.copy(extraMinutes = it)) }
            Hint("Đồng hồ chạy theo từng hiệp; hết giờ chính thức thì app kêu, quá giờ thì hiện bù giờ (45+2'). Hoà thì có thể đá hiệp phụ hoặc luân lưu.")
        }
        Sport.TENNIS -> FormCard("Luật") {
            Label("Số set")
            Chips(listOf(1, 3, 5), r.bestOf, { if (it == 1) "1 set" else "Thắng ${it / 2 + 1}/$it set" }) { onChange(r.copy(bestOf = it)) }
            Label("Game mỗi set")
            Chips(listOf(6, 4), r.games, { "$it game" }) { onChange(r.copy(games = it)) }
            Label("Khi 40-40")
            Chips(listOf(false, true), r.noAd, { if (it) "No-ad (1 điểm quyết định)" else "Deuce / AD" }) { onChange(r.copy(noAd = it)) }
            if (r.bestOf > 1) {
                Label("Set cuối")
                Chips(FinalSet.entries, r.finalSet, { it.title }) { onChange(r.copy(finalSet = it)) }
            }
            Hint("Hoà ${r.games}-${r.games} thì đánh tie-break 7 điểm. Tự nhắc đổi sân sau game lẻ.")
        }
        else -> FormCard("Luật") {
            val word = sport.setWord.lowercase()
            Label("Số $word")
            val options = if (sport == Sport.TABLE_TENNIS) listOf(1, 3, 5, 7) else listOf(1, 3, 5)
            Chips(options, r.bestOf, { if (it == 1) "1 $word" else "Thắng ${it / 2 + 1}/$it" }) { onChange(r.copy(bestOf = it)) }
            Label("Điểm mỗi $word")
            val pts = when (sport) {
                Sport.BADMINTON -> listOf(21, 15, 11)
                Sport.VOLLEYBALL -> listOf(25, 21, 15)
                else -> listOf(11, 21)
            }
            Chips(pts, r.points, { "$it điểm" }) { onChange(r.copy(points = it)) }
            if (sport == Sport.VOLLEYBALL && r.bestOf > 1) {
                Label("Set quyết định")
                Chips(listOf(15, 25), r.decidingPoints, { "$it điểm" }) { onChange(r.copy(decidingPoints = it)) }
            }
            if (sport == Sport.BADMINTON) {
                Label("Khi hoà ${r.points - 1}-${r.points - 1}")
                Chips(listOf(true, false), r.cap > 0, { if (it) "Ai chạm ${r.points + 9} trước thắng" else "Hơn 2 điểm mới thắng" }) {
                    onChange(r.copy(cap = if (it) r.points + 9 else 0))
                }
            }
            Hint(
                when (sport) {
                    Sport.BADMINTON -> "Bên thắng pha cầu được giao. Nhắc nghỉ khi chạm ${(r.points + 1) / 2} và đổi sân ở ván quyết định."
                    Sport.VOLLEYBALL -> "Đội thắng pha bóng được giao. Mỗi đội 2 lần hội ý (30 giây) mỗi set. Đổi sân ở set quyết định khi một đội chạm ${(r.decidingPoints + 1) / 2}."
                    else -> "Đổi giao mỗi 2 điểm, từ ${r.points - 1}-${r.points - 1} đổi mỗi điểm. Mỗi bên 1 lần hội ý (1 phút). Đổi sân ở ván quyết định khi chạm ${r.points / 2}."
                },
            )
        }
    }
}

/** Placeholder names, not worth suggesting. */
private val defaultNames = setOf("Đội A", "Đội B", "Người chơi A", "Người chơi B", "A1", "A2", "B1", "B2")

@Composable
fun FormCard(title: String, content: @Composable () -> Unit) {
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
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
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
fun Label(text: String) {
    Text(text, Modifier.padding(top = 10.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Hint(text: String) {
    Text(text, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(o == selected, { onPick(o) }, label = { Text(label(o)) }) }
    }
}
