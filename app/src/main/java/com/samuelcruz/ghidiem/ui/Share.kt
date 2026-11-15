package com.samuelcruz.ghidiem.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.samuelcruz.ghidiem.R
import com.samuelcruz.ghidiem.core.Kind
import com.samuelcruz.ghidiem.core.Sport
import com.samuelcruz.ghidiem.core.fmtDuration
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val shareDate = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi"))

private fun MatchUi.startedText() = shareDate.format(Date(if (m.startedAt > 0) m.startedAt else m.createdAt))

/** Goal scorers etc. for football: "23' ⚽ Đội A (Nam)". */
private fun MatchUi.footballLines(): List<String> = evs.mapIndexedNotNull { i, e ->
    if (Kind.divider(e.kind) || e.kind == Kind.SUB || !board.lines[i].counted) return@mapIndexedNotNull null
    if (e.kind == Kind.PEN_SCORED || e.kind == Kind.PEN_MISSED) return@mapIndexedNotNull null
    "${board.lines[i].time} ${Kind.icon(e.kind).trim()} ${m.name(e.side)}" + if (e.note.isNotBlank()) " (${e.note})" else ""
}

/** Plain-text result for Zalo / Messenger. */
fun shareText(context: Context, ui: MatchUi) {
    val m = ui.m
    val board = ui.board
    val sport = ui.sport
    val (a, b) = board.score.split("-")
    val text = buildString {
        appendLine("${sport.emoji} ${sport.title} · ${ui.startedText()}")
        appendLine("${m.nameA} $a – $b ${m.nameB}")
        board.shootoutScore?.let { appendLine("Luân lưu: $it") }
        if (sport != Sport.FOOTBALL && board.history.isNotEmpty()) {
            appendLine("${sport.setWord}: " + board.history.joinToString(" · ") { it.text() })
        }
        if (ui.clock > 0) appendLine("Thời gian: ${fmtDuration(ui.clock)}")
        if (sport == Sport.FOOTBALL) ui.footballLines().forEach { appendLine(it) }
        if (!ui.done) append("(đang diễn ra)")
    }.trim()
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Chia sẻ kết quả"))
}

/** A 1080×1350 result card (team colours, big score, sets or scorers), shared as a PNG. */
fun shareImage(context: Context, ui: MatchUi) {
    val bmp = resultCard(context, ui)
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "ket-qua-${ui.m.id}.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Chia sẻ ảnh kết quả"))
}

fun resultCard(context: Context, ui: MatchUi): Bitmap {
    val w = 1080
    val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val bold = ResourcesCompat.getFont(context, R.font.be_vietnam_pro_extrabold) ?: Typeface.DEFAULT_BOLD
    val semi = ResourcesCompat.getFont(context, R.font.be_vietnam_pro_semibold) ?: Typeface.DEFAULT
    val regular = ResourcesCompat.getFont(context, R.font.be_vietnam_pro_regular) ?: Typeface.DEFAULT
    val m = ui.m
    val b = ui.board
    val sport = ui.sport

    // Background: indigo night gradient.
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
        shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF312E81.toInt(), 0xFF0C0E16.toInt(), Shader.TileMode.CLAMP)
    })
    fun text(s: String, x: Float, y: Float, size: Float, face: Typeface, color: Int = 0xFFFFFFFF.toInt(), align: Paint.Align = Paint.Align.CENTER, maxWidth: Float = 960f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = face; this.color = color; textAlign = align }
        var t = s
        while (p.measureText(t) > maxWidth && t.length > 2) t = t.dropLast(2) + "…"
        c.drawText(t, x, y, p)
    }
    val muted = 0xB3FFFFFF.toInt()
    text("${sport.emoji}  ${sport.title.uppercase()}" + if (m.doubles) " · ĐÔI" else "", w / 2f, 130f, 44f, semi, muted)
    text(ui.startedText(), w / 2f, 190f, 34f, regular, muted)

    // Two team cards with the big numbers.
    val (x, y) = b.score.split("-")
    val cardTop = 260f
    val cardH = 520f
    for (side in 0..1) {
        val left = if (side == 0) 60f else 560f
        val rect = RectF(left, cardTop, left + 460f, cardTop + cardH)
        val colors = if (side == 0) intArrayOf(0xFFF43F5E.toInt(), 0xFFBE123C.toInt()) else intArrayOf(0xFF0EA5E9.toInt(), 0xFF0369A1.toInt())
        c.drawRoundRect(rect, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, colors[0], colors[1], Shader.TileMode.CLAMP)
        })
        text(m.name(side), rect.centerX(), rect.top + 90f, 46f, semi, maxWidth = 400f)
        text(if (side == 0) x else y, rect.centerX(), rect.top + 390f, 260f, bold)
        val winner = (b.winner.takeIf { it >= 0 } ?: m.winner) == side && ui.done
        if (winner) text("🏆 THẮNG", rect.centerX(), rect.bottom - 50f, 40f, semi)
    }

    var lineY = cardTop + cardH + 110f
    b.shootoutScore?.let {
        text("Luân lưu $it", w / 2f, lineY, 52f, semi)
        lineY += 80f
    }
    if (sport != Sport.FOOTBALL && b.history.isNotEmpty()) {
        text(b.history.joinToString("   ") { it.text() }, w / 2f, lineY, 60f, semi)
        lineY += 90f
    }
    if (sport == Sport.FOOTBALL) {
        ui.footballLines().take(6).forEach {
            text(it, w / 2f, lineY, 38f, regular, muted)
            lineY += 56f
        }
    }
    if (ui.clock > 0) text("⏱  ${fmtDuration(ui.clock)}", w / 2f, h - 150f, 40f, regular, muted)
    text("Ghi Điểm", w / 2f, h - 70f, 34f, semi, 0x80FFFFFF.toInt())
    if (!ui.done) text("ĐANG DIỄN RA", w / 2f, 250f - 10f, 30f, semi, 0xFFFACC15.toInt())
    return bmp
}
