package com.samuelcruz.ghidiem.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.samuelcruz.ghidiem.core.Kind
import com.samuelcruz.ghidiem.core.Sport
import kotlinx.coroutines.launch

/**
 * Big landscape scoreboard for spectators (or a phone on a stand by the court): full screen, no system bars.
 * Tapping a half still scores, so the referee can use it too.
 */
@Composable
fun BoardScreen(id: Long, onBack: () -> Unit) {
    val app = app()
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        val window = activity?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            bars?.show(WindowInsetsCompat.Type.systemBars())
            // Back to "follow the phone" (the app never locks it otherwise). Not the value read at start: the
            // rotation recreates the activity, so that would already be landscape.
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    val ui = rememberMatch(id)
    Box(Modifier.fillMaxSize().background(Color(0xFF07080D))) {
        if (ui == null) {
            MissingMatch(onBack)
            return@Box
        }
        fun tap(side: Int) {
            scope.launch {
                app.tapScore(ui.m, side, if (ui.sport == Sport.FOOTBALL) Kind.GOAL else Kind.POINT,
                    { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }) { }
            }
        }
        MatchFeedback(ui, onSide = ::tap)
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            val c = clockText(ui)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onBack) { Icon(Icons.Filled.Close, "Thoát toàn màn hình") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(c.main, color = Color.White, fontSize = 44.sp, style = MaterialTheme.typography.displaySmall)
                        c.extra?.let { Text("  $it", color = Amber, fontSize = 24.sp, style = MaterialTheme.typography.titleLarge) }
                    }
                    val status = when {
                        ui.done -> resultText(ui)
                        ui.board.callouts.isNotEmpty() ->
                            ui.board.callouts.joinToString("  ·  ") { "${it.text.uppercase()} ${ui.m.name(it.side)}" }
                        else -> ui.board.alert ?: c.sub
                    }
                    Text(status, color = if (ui.board.callouts.isNotEmpty() && !ui.done) Amber else Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalIconButton({ scope.launch { app.repo.undo(ui.m.id) } }, enabled = !ui.m.manualEnd) {
                    Icon(Icons.AutoMirrored.Filled.Undo, "Hoàn tác")
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ui.order.forEach { side -> Panel(side, ui, Modifier.weight(1f).fillMaxHeight(), huge = true) { tap(side) } }
            }
            if (ui.board.history.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                ui.board.history.forEach { s ->
                    val (l, r) = ui.order.map { if (it == 0) s.a else s.b }
                    Text(if (s.superTb) "[$l-$r]" else "$l-$r", Modifier.padding(horizontal = 6.dp).clip(CircleShape)
                        .background(Team.color(s.winner)).padding(horizontal = 14.dp, vertical = 4.dp),
                        color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
