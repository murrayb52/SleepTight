package io.github.murrayb52.sleeptight.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.data.SleepStage
import io.github.murrayb52.sleeptight.data.SleepStageSegment
import io.github.murrayb52.sleeptight.ui.home.rememberTimeFormatter
import io.github.murrayb52.sleeptight.ui.theme.Palette

private val StageColours = mapOf(
    SleepStage.AWAKE to Color(0xFFF28C6B),
    SleepStage.REM to Color(0xFFB49BFF),
    SleepStage.LIGHT to Color(0xFF6E8BE8),
    SleepStage.DEEP to Color(0xFF2F4CB0),
    SleepStage.UNKNOWN to Color(0xFF6E8BE8),
)

/**
 * The night's stages over time, from the bed tap to the wake tap. Garmin nights get four rows
 * (awake, REM, light, deep); estimated nights two (awake, asleep), since there's no stage estimate.
 */
@Composable
fun Hypnogram(stages: List<SleepStageSegment>, night: NightRowUi, estimated: Boolean) {
    val time = rememberTimeFormatter()
    val rows: List<Pair<Int, Set<SleepStage>>> = if (estimated) {
        listOf(R.string.stage_awake to setOf(SleepStage.AWAKE), R.string.stage_asleep to setOf(SleepStage.UNKNOWN, SleepStage.LIGHT, SleepStage.DEEP, SleepStage.REM))
    } else {
        listOf(
            R.string.stage_awake to setOf(SleepStage.AWAKE),
            R.string.stage_rem to setOf(SleepStage.REM),
            R.string.stage_light to setOf(SleepStage.LIGHT, SleepStage.UNKNOWN),
            R.string.stage_deep to setOf(SleepStage.DEEP),
        )
    }
    val rowHeight = 22.dp
    val description = stringResource(if (estimated) R.string.hypnogram_estimated else R.string.hypnogram_garmin)
    val start = night.bedTapAt.toEpochMilli()
    val total = (night.wakeTapAt.toEpochMilli() - start).coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Column(Modifier.width(52.dp)) {
                rows.forEach { (label, _) ->
                    Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.height(rowHeight).padding(top = 3.dp))
                }
            }
            Canvas(Modifier.weight(1f).height(rowHeight * rows.size).semantics { contentDescription = description }) {
                val rowPx = rowHeight.toPx()
                stages.forEach { s ->
                    val row = rows.indexOfFirst { s.stage in it.second }.takeIf { it >= 0 } ?: return@forEach
                    val x0 = (s.start - start).toFloat() / total * size.width
                    val x1 = (s.end - start).toFloat() / total * size.width
                    drawRoundRect(
                        StageColours.getValue(s.stage),
                        topLeft = Offset(x0, row * rowPx + 3.dp.toPx()),
                        size = Size((x1 - x0).coerceAtLeast(1.dp.toPx()), rowPx - 6.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 52.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(time.format(night.bedTapAt.atZone(night.zone)), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            Text(time.format(night.wakeTapAt.atZone(night.zone)), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
    }
}
