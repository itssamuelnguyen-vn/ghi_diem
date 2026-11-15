package com.samuelcruz.ghidiem.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.samuelcruz.ghidiem.GhiDiemApp
import com.samuelcruz.ghidiem.MainActivity
import com.samuelcruz.ghidiem.core.Kind
import com.samuelcruz.ghidiem.core.Sport
import com.samuelcruz.ghidiem.data.MatchRow

/**
 * Home-screen scoreboard: the match being played (or the last one). While it is live, "+1" buttons give a
 * point (or a goal) without opening the app; tapping the rest opens the match.
 */
class ScoreWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as GhiDiemApp
        val dao = app.db.matches()
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = if (dark) Color(0xFF171A28) else Color.White
        val fg = if (dark) Color(0xFFE7E9F2) else Color(0xFF141726)
        val muted = if (dark) Color(0xFF9EA4BA) else Color(0xFF5E6478)
        val red = Color(0xFFE11D48)
        val blue = Color(0xFF0284C7)
        provideContent {
            // Read inside provideContent so update() always shows the fresh score.
            val all by dao.all().collectAsState(initial = emptyList())
            val m: MatchRow? = all.firstOrNull { !it.finished } ?: all.firstOrNull()
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { if (m != null) putExtra(MainActivity.EXTRA_MATCH, m.id) }
            Column(
                GlanceModifier.fillMaxSize().background(ColorProvider(bg)).cornerRadius(24.dp).padding(12.dp)
                    .clickable(actionStartActivity(open)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (m == null) {
                    Text("Chưa có trận nào. Chạm để bắt đầu.", style = TextStyle(color = ColorProvider(fg), fontSize = 14.sp))
                    return@Column
                }
                val sport = m.sportEnum
                Text(
                    "${sport.emoji} ${sport.title}" + if (m.finished) " · đã kết thúc" else " · đang chơi",
                    style = TextStyle(color = ColorProvider(muted), fontSize = 12.sp),
                )
                Spacer(GlanceModifier.height(4.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(m.nameA, maxLines = 1, style = TextStyle(color = ColorProvider(red), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                        Text(m.nameB, maxLines = 1, style = TextStyle(color = ColorProvider(blue), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    }
                    Text(m.score.replace("-", " – "), style = TextStyle(color = ColorProvider(fg), fontSize = 26.sp, fontWeight = FontWeight.Bold))
                }
                if (m.detail.isNotEmpty()) Text(m.detail, maxLines = 1, style = TextStyle(color = ColorProvider(muted), fontSize = 12.sp))
                if (!m.finished) {
                    Spacer(GlanceModifier.height(6.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        PlusButton(m, 0, red)
                        Spacer(GlanceModifier.width(6.dp))
                        // Takes back the last point / goal / card, like "Hoàn tác" in the app.
                        Box(
                            GlanceModifier.width(44.dp).height(36.dp).background(ColorProvider(muted)).cornerRadius(18.dp)
                                .clickable(actionRunCallback<UndoAction>(actionParametersOf(ScoreAction.match to m.id))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("↶", style = TextStyle(color = ColorProvider(Color.White), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                        }
                        Spacer(GlanceModifier.width(6.dp))
                        PlusButton(m, 1, blue)
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun androidx.glance.layout.RowScope.PlusButton(m: MatchRow, side: Int, color: Color) {
        Box(
            GlanceModifier.defaultWeight().height(36.dp).background(ColorProvider(color)).cornerRadius(18.dp)
                .clickable(actionRunCallback<ScoreAction>(actionParametersOf(ScoreAction.match to m.id, ScoreAction.side to side))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (if (m.sportEnum == Sport.FOOTBALL) "⚽ " else "+1 ") + m.name(side),
                maxLines = 1,
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
        }
    }

    companion object {
        suspend fun refresh(context: Context) = ScoreWidget().updateAll(context)
    }
}

/** "+1" on the widget: records the point like a tap in the app (clock starts / resumes the same way). */
class ScoreAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as GhiDiemApp
        val id = parameters[match] ?: return
        val s = parameters[side] ?: return
        val m = app.db.matches().get(id) ?: return
        app.repo.score(id, s, if (m.sportEnum == Sport.FOOTBALL) Kind.GOAL else Kind.POINT)
        ScoreWidget.refresh(context)
    }

    companion object {
        val match = ActionParameters.Key<Long>("match")
        val side = ActionParameters.Key<Int>("side")
    }
}

/** "↶" on the widget: takes back the last point / goal / card. */
class UndoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as GhiDiemApp
        val id = parameters[ScoreAction.match] ?: return
        val m = app.db.matches().get(id) ?: return
        if (m.manualEnd) return
        app.repo.undo(id)
        ScoreWidget.refresh(context)
    }
}

class ScoreWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScoreWidget()
}
