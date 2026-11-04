package io.github.murrayb52.sleeptight.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.ui.theme.Palette

/** Colour for a score band: 80+ good, 60–79 fair, below 60 poor. Text and arc length carry it too. */
fun scoreColour(score: Int): Color = when {
    score >= 80 -> Palette.Good
    score >= 60 -> Palette.Ember
    else -> Palette.Poor
}

/**
 * A score dial whose arc is the score: 100 is a full circle, 0 none, anything between proportional,
 * starting at 12 o'clock. A null score (no sleep data yet) shows the empty track and a dash.
 */
@Composable
fun ScoreDial(
    score: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    stroke: Dp = 3.dp,
    textStyle: TextStyle,
    track: Color = Palette.Line,
    caption: (@Composable () -> Unit)? = null,
) {
    val description = score?.let { "Sleep score $it out of 100" } ?: "No sleep score yet"
    Box(modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(w))
            if (score != null && score > 0) {
                drawArc(
                    scoreColour(score),
                    startAngle = -90f,
                    sweepAngle = 360f * score.coerceIn(0, 100) / 100f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score?.toString() ?: "–", style = textStyle)
            caption?.invoke()
        }
    }
}
