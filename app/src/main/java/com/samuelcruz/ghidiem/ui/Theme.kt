package com.samuelcruz.ghidiem.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.samuelcruz.ghidiem.R

/** Be Vietnam Pro: an open-source typeface drawn for Vietnamese (licenses/BeVietnamPro-OFL.txt). */
val BeVietnamPro = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
    Font(R.font.be_vietnam_pro_extrabold, FontWeight.ExtraBold),
)

// "tnum": all digits the same width, so a running clock and scores do not jiggle.
private fun TextStyle.be(weight: FontWeight? = null) =
    copy(fontFamily = BeVietnamPro, fontWeight = weight ?: fontWeight, fontFeatureSettings = "tnum")

private val typography = Typography().run {
    copy(
        displayLarge = displayLarge.be(FontWeight.ExtraBold),
        displayMedium = displayMedium.be(FontWeight.ExtraBold),
        displaySmall = displaySmall.be(FontWeight.ExtraBold),
        headlineLarge = headlineLarge.be(FontWeight.Bold),
        headlineMedium = headlineMedium.be(FontWeight.Bold),
        headlineSmall = headlineSmall.be(FontWeight.Bold),
        titleLarge = titleLarge.be(FontWeight.Bold),
        titleMedium = titleMedium.be(FontWeight.SemiBold),
        titleSmall = titleSmall.be(FontWeight.SemiBold),
        bodyLarge = bodyLarge.be(),
        bodyMedium = bodyMedium.be(),
        bodySmall = bodySmall.be(),
        labelLarge = labelLarge.be(FontWeight.SemiBold),
        labelMedium = labelMedium.be(FontWeight.SemiBold),
        labelSmall = labelSmall.be(FontWeight.Medium),
    )
}

/** Brand: deep indigo (a stadium at night). */
val Brand = Color(0xFF4338CA)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFFD97706),
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF451A03),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF141726),
    surface = Color(0xFFF5F6FA),
    onSurface = Color(0xFF141726),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFECEEF5),
    surfaceContainerHighest = Color(0xFFE2E5EF),
    onSurfaceVariant = Color(0xFF5E6478),
    outline = Color(0xFFC3C8D8),
    outlineVariant = Color(0xFFE3E6EF),
    error = Color(0xFFDC2626),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFFBBF24),
    secondaryContainer = Color(0xFF4A3410),
    onSecondaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF0C0E16),
    onBackground = Color(0xFFE7E9F2),
    surface = Color(0xFF0C0E16),
    onSurface = Color(0xFFE7E9F2),
    surfaceContainerLowest = Color(0xFF080A10),
    surfaceContainerLow = Color(0xFF141724),
    surfaceContainer = Color(0xFF171A28),
    surfaceContainerHigh = Color(0xFF1F2333),
    surfaceContainerHighest = Color(0xFF282C3E),
    onSurfaceVariant = Color(0xFF9EA4BA),
    outline = Color(0xFF454B62),
    outlineVariant = Color(0xFF262A3A),
    error = Color(0xFFF87171),
)

/** Side A is red, side B is blue, like a referee's scoreboard. */
object Team {
    private val a = listOf(Color(0xFFF43F5E), Color(0xFFBE123C))
    private val b = listOf(Color(0xFF0EA5E9), Color(0xFF0369A1))
    fun color(side: Int) = if (side == 0) a[1] else b[1]
    fun light(side: Int) = if (side == 0) a[0] else b[0]
    fun brush(side: Int) = Brush.linearGradient(if (side == 0) a else b)
}

val HeroBrush = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF4338CA), Color(0xFF312E81)))
val Amber = Color(0xFFF59E0B)

@Composable
fun GhiDiemTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = typography) {
        // Text outside a Surface would otherwise be black, even in dark mode.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
    }
}
