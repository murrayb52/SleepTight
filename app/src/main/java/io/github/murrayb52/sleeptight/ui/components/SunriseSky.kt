package io.github.murrayb52.sleeptight.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.murrayb52.sleeptight.ui.theme.Palette

/**
 * A sky that goes from night to full day as [progress] goes from 0 to 1: deep blue, through
 * violet and ember at dawn, to warm daylight, with the sun rising from below the bottom edge.
 * Stars fade out in the first half.
 */
@Composable
fun SunriseSky(progress: Float, modifier: Modifier = Modifier, twinkle: Boolean = true) {
    val p = progress.coerceIn(0f, 1f)
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val top = skyTop(p)
            val horizon = skyHorizon(p)
            drawRect(Brush.verticalGradient(listOf(top, lerp(top, horizon, 0.55f), horizon)))

            // The sun rises from below the bottom edge towards the top. Its disc softens into a glow
            // as it becomes full day, so it never competes with what's on screen.
            val sunRadius = size.width * 0.15f
            val sunY = size.height + sunRadius * 2f - p * (size.height * 0.9f + sunRadius * 2f)
            val centre = Offset(size.width / 2, sunY)
            val glow = 0.15f + 0.85f * p
            drawCircle(
                Brush.radialGradient(
                    listOf(Palette.Ember.copy(alpha = 0.5f * glow), Color.Transparent),
                    center = centre,
                    radius = sunRadius * 5f,
                ),
                radius = sunRadius * 5f,
                center = centre,
            )
            val discAlpha = if (p < 0.6f) 1f else 1f - (p - 0.6f) / 0.4f * 0.85f
            drawCircle(lerp(Color(0xFFF4A653), Color(0xFFFFF3D6), p).copy(alpha = discAlpha), radius = sunRadius, center = centre)
        }
        StarrySky(Modifier.fillMaxSize(), twinkle = twinkle, fade = (1f - p * 2f).coerceIn(0f, 1f))
    }
}

private val NightTop = Palette.DeepNight
private val NightHorizon = Color(0xFF101634)
private val DawnTop = Color(0xFF2A2350)
private val DawnHorizon = Color(0xFFE07A4F)
private val DayTop = Color(0xFF8CC4F0)
private val DayHorizon = Palette.Dawn

private fun skyTop(p: Float) = if (p < 0.5f) lerp(NightTop, DawnTop, p * 2) else lerp(DawnTop, DayTop, (p - 0.5f) * 2)
private fun skyHorizon(p: Float) = if (p < 0.5f) lerp(NightHorizon, DawnHorizon, p * 2) else lerp(DawnHorizon, DayHorizon, (p - 0.5f) * 2)
