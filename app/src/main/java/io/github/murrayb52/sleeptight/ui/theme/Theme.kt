package io.github.murrayb52.sleeptight.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Night = Color(0xFF0F1426)
private val Moon = Color(0xFFB9C3FF)
private val Dusk = Color(0xFF3A4A8C)

private val DarkColours = darkColorScheme(
    primary = Moon,
    onPrimary = Night,
    secondary = Color(0xFFC4C6DD),
    background = Night,
    surface = Night,
)

private val LightColours = lightColorScheme(
    primary = Dusk,
    onPrimary = Color.White,
    secondary = Color(0xFF5A5D72),
)

/**
 * App theme. Uses Material You dynamic colour on Android 12+, otherwise a night-blue palette.
 * A sleep app is mostly used in the dark, so the dark scheme is the one to get right.
 */
@Composable
fun SleepTightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColour: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colourScheme = when {
        dynamicColour && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColours
        else -> LightColours
    }
    MaterialTheme(colorScheme = colourScheme, content = content)
}
