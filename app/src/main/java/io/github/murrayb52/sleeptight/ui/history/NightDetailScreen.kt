package io.github.murrayb52.sleeptight.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.ui.components.Panel
import io.github.murrayb52.sleeptight.ui.components.ScoreDial
import io.github.murrayb52.sleeptight.ui.home.formatDuration
import io.github.murrayb52.sleeptight.ui.home.rememberTimeFormatter
import io.github.murrayb52.sleeptight.ui.theme.Palette
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun NightDetailRoute(nightId: Long, onBack: () -> Unit) {
    val viewModel: NightDetailViewModel = viewModel(key = "night-$nightId", factory = NightDetailViewModel.factory(nightId))
    val state by viewModel.state.collectAsStateWithLifecycle()
    NightDetailScreen(state, onBack)
}

@Composable
fun NightDetailScreen(state: NightDetailUi, onBack: () -> Unit) {
    val time = rememberTimeFormatter()
    val night = state.night
    Column(
        Modifier.fillMaxSize().background(Palette.Sky).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledIconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.Card, contentColor = Palette.Text),
            ) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
            if (night != null) {
                Column {
                    Text(
                        stringResource(
                            R.string.night_title,
                            DateTimeFormatter.ofPattern("EEE d").format(night.date),
                            DateTimeFormatter.ofPattern("EEE d MMM").format(night.date.plusDays(1)),
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.night_times, time.format(night.bedTapAt.atZone(night.zone)), time.format(night.wakeTapAt.atZone(night.zone))),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
            }
        }
        if (night == null) return@Column

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ScoreDial(night.score, size = 84.dp, stroke = 6.dp, textStyle = MaterialTheme.typography.headlineMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.time_in_bed, formatDuration(night.timeInBed)), style = MaterialTheme.typography.titleMedium)
                    state.alarmFiredAt?.let {
                        Text(
                            stringResource(
                                R.string.night_alarm,
                                time.format(Instant.ofEpochMilli(it).atZone(night.zone)),
                                pluralStringResource(R.plurals.snooze_count, night.snoozes, night.snoozes),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Muted,
                        )
                    }
                    if (night.score == null) {
                        Text(stringResource(R.string.score_pending), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                }
            }
        }

        Panel {
            Text(stringResource(R.string.sleep_stages), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.sleep_stages_pending), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }

        Panel {
            Text(stringResource(R.string.noise_events_title, state.events.size), style = MaterialTheme.typography.titleSmall)
            val total = night.timeInBed.toMillis().coerceAtLeast(1)
            Canvas(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF121935))) {
                state.events.forEach { e ->
                    val x = ((e.start - night.bedTapAt.toEpochMilli()).toFloat() / total).coerceIn(0f, 1f) * size.width
                    drawLine(Palette.Moon, Offset(x, size.height * 0.2f), Offset(x, size.height * 0.8f), 3.dp.toPx())
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(time.format(night.bedTapAt.atZone(night.zone)), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                Text(time.format(night.wakeTapAt.atZone(night.zone)), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
            if (state.events.isEmpty()) {
                Text(stringResource(R.string.noise_events_quiet), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            state.events.sortedByDescending { it.peakDbRelative }.take(10).sortedBy { it.start }.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.size(6.dp).clip(CircleShape).background(Palette.Moon)) {}
                    Text(
                        stringResource(
                            R.string.noise_event_line,
                            time.format(Instant.ofEpochMilli(e.start).atZone(night.zone)),
                            Duration.ofMillis(e.end - e.start).toSeconds(),
                            Math.round(e.peakDbRelative),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextSoft,
                    )
                }
            }
        }
    }
}
