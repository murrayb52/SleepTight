package io.github.murrayb52.sleeptight.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.murrayb52.sleeptight.R

/**
 * SleepTight's palette: a deep-ink night sky, moonlight for information, and ember amber kept for
 * the one main action on each screen. Day colours are only used for the morning summary.
 */
object Palette {
    val Sky = Color(0xFF0A0F24) // screens before bed
    val DeepNight = Color(0xFF04061A) // in bed: very dark blue, almost black
    val Card = Color(0xFF131A36)
    val CardDim = Color(0xFF0B1028)
    val Line = Color(0xFF1E2748)
    val Raised = Color(0xFF1B2347)
    val Outline = Color(0xFF2E3A66)
    val Text = Color(0xFFE8ECF8)
    val TextSoft = Color(0xFFC9D0E6)
    val Muted = Color(0xFFA3ACC8)
    val Faint = Color(0xFF4A557A)
    val Moon = Color(0xFF9FB4FF)
    val Ember = Color(0xFFF4A653)
    val OnEmber = Color(0xFF1A1206)
    val Good = Color(0xFF7FD1B9)
    val Poor = Color(0xFFF28C6B)
    val Star = Color(0xFFDDE5FF)

    // Morning ("day mode")
    val Dawn = Color(0xFFFFF4E4)
    val DayText = Color(0xFF2B2118)
    val DayMuted = Color(0xFF6B5A48)
    val DayCard = Color(0xFFFFFFFF)
}

private val Outfit = FontFamily(
    listOf(300, 400, 500, 600).map { w ->
        Font(R.font.outfit, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

private val base = TextStyle(fontFamily = Outfit)

private val SleepTightTypography = Typography(
    displayLarge = base.copy(fontSize = 96.sp, fontWeight = FontWeight.Light, lineHeight = 100.sp),
    displayMedium = base.copy(fontSize = 64.sp, fontWeight = FontWeight.Light, lineHeight = 72.sp),
    displaySmall = base.copy(fontSize = 44.sp, fontWeight = FontWeight.Light, lineHeight = 52.sp),
    headlineLarge = base.copy(fontSize = 30.sp, fontWeight = FontWeight.Medium, lineHeight = 38.sp),
    headlineMedium = base.copy(fontSize = 26.sp, fontWeight = FontWeight.Medium, lineHeight = 34.sp),
    headlineSmall = base.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 30.sp),
    titleLarge = base.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
    titleMedium = base.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    titleSmall = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = base.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    labelMedium = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelSmall = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
)

private val NightColours = darkColorScheme(
    primary = Palette.Ember,
    onPrimary = Palette.OnEmber,
    secondary = Palette.Moon,
    onSecondary = Palette.Sky,
    tertiary = Palette.Good,
    background = Palette.Sky,
    onBackground = Palette.Text,
    surface = Palette.Sky,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Card,
    onSurfaceVariant = Palette.Muted,
    surfaceContainer = Palette.Card,
    surfaceContainerHigh = Palette.Raised,
    surfaceContainerHighest = Palette.Raised,
    outline = Palette.Outline,
    outlineVariant = Palette.Line,
    error = Palette.Poor,
    onError = Palette.OnEmber,
    errorContainer = Color(0xFF3A1E24),
    onErrorContainer = Color(0xFFFFD9CF),
)

private val DayColours = lightColorScheme(
    primary = Palette.Ember,
    onPrimary = Palette.OnEmber,
    background = Palette.Dawn,
    onBackground = Palette.DayText,
    surface = Palette.Dawn,
    onSurface = Palette.DayText,
    surfaceVariant = Palette.DayCard,
    onSurfaceVariant = Palette.DayMuted,
)

/** The app is designed for the dark (it's used at night), so the night scheme is the default. */
@Composable
fun SleepTightTheme(day: Boolean = false, content: @Composable () -> Unit) {
    val colours = if (day) DayColours else NightColours
    MaterialTheme(colorScheme = colours, typography = SleepTightTypography) {
        // Text outside a Surface or Scaffold would otherwise default to black.
        CompositionLocalProvider(LocalContentColor provides colours.onBackground, content = content)
    }
}
