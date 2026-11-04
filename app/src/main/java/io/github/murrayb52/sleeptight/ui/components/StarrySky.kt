package io.github.murrayb52.sleeptight.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import io.github.murrayb52.sleeptight.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private class Star(val x: Float, val y: Float, val radius: Float, val brightness: Float, val speed: Float, val phase: Float)

/**
 * A field of stars that twinkle slowly, each at its own pace. [fade] (0..1) dims them all, which
 * the sunrise uses to let them disappear as the sky brightens.
 */
@Composable
fun StarrySky(modifier: Modifier = Modifier, count: Int = 70, twinkle: Boolean = true, fade: Float = 1f, seed: Int = 7) {
    val stars = remember(count, seed) {
        val r = Random(seed)
        List(count) {
            Star(
                x = r.nextFloat(),
                y = r.nextFloat() * 0.85f,
                radius = 0.6f + r.nextFloat() * r.nextFloat() * 1.6f,
                brightness = 0.25f + r.nextFloat() * 0.55f,
                speed = 0.5f + r.nextFloat(),
                phase = r.nextFloat() * 2 * PI.toFloat(),
            )
        }
    }
    val time = if (twinkle) {
        rememberInfiniteTransition(label = "twinkle").animateFloat(
            initialValue = 0f,
            targetValue = 2 * PI.toFloat(),
            animationSpec = infiniteRepeatable(tween(durationMillis = 9_000, easing = LinearEasing), RepeatMode.Restart),
            label = "time",
        ).value
    } else {
        0f
    }
    Canvas(modifier) {
        stars.forEach { s ->
            // Slow sine twinkle between 45 % and 100 % of each star's own brightness.
            val pulse = 0.725f + 0.275f * sin(time * s.speed + s.phase)
            val alpha = (s.brightness * pulse * fade).coerceIn(0f, 1f)
            drawCircle(
                color = Palette.Star.copy(alpha = alpha),
                radius = s.radius * density,
                center = Offset(s.x * size.width, s.y * size.height),
            )
        }
    }
}

/** Twinkle-free stand-in used in previews and tests. */
@Composable
fun StillStars(modifier: Modifier = Modifier) = StarrySky(modifier, twinkle = false)

