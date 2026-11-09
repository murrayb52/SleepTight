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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import io.github.murrayb52.sleeptight.SleepTightApplication
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.data.SleepStageSegment
import io.github.murrayb52.sleeptight.health.GarminImporter
import io.github.murrayb52.sleeptight.metrics.Goals
import io.github.murrayb52.sleeptight.metrics.RangeVerdict
import io.github.murrayb52.sleeptight.metrics.ReferenceRanges
import io.github.murrayb52.sleeptight.scoring.ScoreCache
import io.github.murrayb52.sleeptight.metrics.ScoreComponent
import io.github.murrayb52.sleeptight.ui.components.InfoButton
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
    val container = (LocalContext.current.applicationContext as SleepTightApplication).container
    NightDetailScreen(
        state,
        onBack,
        onCheckGarmin = viewModel::onCheckGarmin,
        onDelete = { viewModel.onDelete(onBack) },
        onAnswerSurvey = { container.surveyRequested.value = nightId },
    )
}

@Composable
fun NightDetailScreen(
    state: NightDetailUi,
    onBack: () -> Unit,
    onCheckGarmin: () -> Unit = {},
    onDelete: () -> Unit = {},
    onAnswerSurvey: () -> Unit = {},
) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_night_title)) },
            text = { Text(stringResource(R.string.delete_night_body)) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) { Text(stringResource(R.string.delete), color = Palette.Poor) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = Palette.Card,
            titleContentColor = Palette.Text,
            textContentColor = Palette.TextSoft,
        )
    }
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

        val score = state.score
        val m = score?.metrics
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ScoreDial(night.score, size = 84.dp, stroke = 6.dp, textStyle = MaterialTheme.typography.headlineMedium)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val asleep = m?.totalSleepMinutes
                    Text(
                        if (asleep != null) {
                            stringResource(R.string.night_asleep, formatDuration(Duration.ofMinutes(asleep)))
                        } else {
                            stringResource(R.string.time_in_bed, formatDuration(night.timeInBed))
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (m?.efficiency != null) {
                        Text(
                            m.timeToFallAsleepMinutes?.let {
                                stringResource(R.string.night_onset_efficiency, formatDuration(Duration.ofMinutes(it)), percent(m.efficiency))
                            } ?: stringResource(R.string.night_efficiency, percent(m.efficiency)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Muted,
                        )
                    }
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
                    Text(
                        stringResource(
                            when (score?.source) {
                                "GARMIN" -> R.string.source_garmin
                                "ESTIMATED" -> R.string.source_estimated
                                else -> if (score == null) R.string.score_calculating else R.string.score_no_data
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
                InfoButton(stringResource(R.string.info_score_title), stringResource(R.string.info_score_body), Modifier.testTag("info-score"))
            }
        }

        Panel {
            Text(stringResource(R.string.score_breakdown), style = MaterialTheme.typography.titleSmall)
            ScoreComponent.entries.forEach { c ->
                val part = score?.parts?.firstOrNull { it.component == c.name }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(componentName(c), style = MaterialTheme.typography.bodyLarge)
                            rawValue(c, score, night)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted) }
                        }
                        Text(
                            stringResource(
                                R.string.points_of_text,
                                part?.let { Math.round(it.points).toString() } ?: "–",
                                part?.let { Math.round(it.maxPoints).toString() } ?: maxPoints(c).toString(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (part != null) Palette.Text else Palette.Muted,
                        )
                        InfoButton(componentName(c), componentExplainer(c, state.goals), Modifier.testTag("info-${c.name}"))
                    }
                    if (part != null) {
                        val fraction = if (part.maxPoints > 0) (part.points / part.maxPoints).toFloat().coerceIn(0f, 1f) else 0f
                        Canvas(Modifier.fillMaxWidth().padding(end = 48.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Line)) {
                            drawRect(Palette.Ember, size = size.copy(width = size.width * fraction))
                        }
                    }
                }
            }
        }

        Panel {
            Text(stringResource(R.string.sleep_stages), style = MaterialTheme.typography.titleSmall)
            if (state.stages.isEmpty()) {
                Text(stringResource(R.string.sleep_stages_pending), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            } else {
                Hypnogram(state.stages, night, estimated = score?.source != "GARMIN")
            }
            if (state.canCheckGarmin) {
                OutlinedButton(
                    onClick = onCheckGarmin,
                    enabled = !state.checkingGarmin,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Moon),
                ) {
                    Text(stringResource(if (state.checkingGarmin) R.string.garmin_checking_night else R.string.garmin_check_night))
                }
                state.garminCheck?.let { result ->
                    Text(
                        stringResource(
                            when (result) {
                                GarminImporter.Result.IMPORTED, GarminImporter.Result.UPDATED -> R.string.garmin_check_imported
                                GarminImporter.Result.UNCHANGED -> R.string.garmin_check_unchanged
                                GarminImporter.Result.NO_DATA -> R.string.garmin_check_no_data
                                GarminImporter.Result.NOT_CONNECTED -> R.string.garmin_check_not_connected
                                GarminImporter.Result.FAILED -> R.string.garmin_check_failed
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result == GarminImporter.Result.IMPORTED) Palette.Good else Palette.Muted,
                    )
                }
            }
        }

        val phone = score?.phone
        if (phone != null && state.phoneStages.isNotEmpty()) PhoneRecord(phone, state.phoneStages, night)

        if (m != null && score.total != null) HowYouSlept(m, state.heartRate)
        score?.let { if (it.total != null) YourGoals(it.regularity, state.goals, night) }

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

        DiaryPanel(state.diary, night.zone, onAnswerSurvey)

        TextButton(onClick = { confirmingDelete = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("deleteNight")) {
            Text(stringResource(R.string.delete_night), color = Palette.Poor)
        }
    }
}

private fun percent(x: Double) = Math.round(x * 100).toInt()

/** Each measurement next to its healthy reference range (for context; the score has its own thresholds). */
@Composable
private fun HowYouSlept(m: ScoreCache.Metrics, heartRate: HeartRateUi?) {
    Panel {
        Text(stringResource(R.string.how_you_slept), style = MaterialTheme.typography.titleSmall)
        m.totalSleepMinutes?.let {
            val d = Duration.ofMinutes(it)
            MetricRow(stringResource(R.string.metric_total_sleep), formatDuration(d), stringResource(R.string.range_total_sleep), ReferenceRanges.totalSleep(d))
        }
        m.efficiency?.let {
            MetricRow(stringResource(R.string.metric_efficiency), stringResource(R.string.raw_percent, percent(it)), stringResource(R.string.range_efficiency), ReferenceRanges.efficiency(it))
        }
        m.timeToFallAsleepMinutes?.let {
            val d = Duration.ofMinutes(it)
            MetricRow(stringResource(R.string.metric_fall_asleep), formatDuration(d), stringResource(R.string.range_fall_asleep), ReferenceRanges.timeToFallAsleep(d))
        }
        m.deepShare?.let {
            MetricRow(stringResource(R.string.metric_deep), stringResource(R.string.raw_percent, percent(it)), stringResource(R.string.range_deep), ReferenceRanges.deep(it))
        }
        m.remShare?.let {
            MetricRow(stringResource(R.string.metric_rem), stringResource(R.string.raw_percent, percent(it)), stringResource(R.string.range_rem), ReferenceRanges.rem(it))
        }
        heartRate?.let {
            MetricRow(stringResource(R.string.metric_heart_rate), stringResource(R.string.heart_rate_value, it.lowest, it.average), stringResource(R.string.range_heart_rate), null)
        }
    }
}

@Composable
private fun MetricRow(name: String, value: String, range: String, verdict: RangeVerdict?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(range, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            verdict?.let {
                Text(
                    stringResource(
                        when (it) {
                            RangeVerdict.WITHIN -> R.string.verdict_within
                            RangeVerdict.BELOW -> R.string.verdict_below
                            RangeVerdict.ABOVE -> R.string.verdict_above
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (it == RangeVerdict.WITHIN) Palette.Good else Palette.Ember,
                )
            }
        }
    }
}

/**
 * What the phone recorded on its own, from overnight sound, on a night scored from Garmin's stages:
 * its asleep/awake chart and measurements, so they can be compared with Garmin's.
 */
@Composable
private fun PhoneRecord(m: ScoreCache.Metrics, stages: List<SleepStageSegment>, night: NightRowUi) {
    Panel(Modifier.testTag("phoneRecord")) {
        Text(stringResource(R.string.phone_record_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.phone_record_note), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        Hypnogram(stages, night, estimated = true)
        m.totalSleepMinutes?.let { PhoneRow(stringResource(R.string.metric_total_sleep), formatDuration(Duration.ofMinutes(it))) }
        m.timeToFallAsleepMinutes?.let { PhoneRow(stringResource(R.string.metric_fall_asleep), formatDuration(Duration.ofMinutes(it))) }
        m.efficiency?.let { PhoneRow(stringResource(R.string.metric_efficiency), stringResource(R.string.raw_percent, percent(it))) }
        m.wakeUps?.let { PhoneRow(stringResource(R.string.metric_wake_ups), it.toString()) }
    }
}

@Composable
private fun PhoneRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** How the night did against each goal (the regularity factors, 0 to 1). */
@Composable
private fun YourGoals(f: ScoreCache.Factors, goals: Goals, night: NightRowUi) {
    val time = rememberTimeFormatter()
    Panel {
        Text(stringResource(R.string.your_goals), style = MaterialTheme.typography.titleSmall)
        GoalRow(stringResource(R.string.goal_row_bedtime, time.format(goals.bedtime)), stringResource(R.string.raw_bed_at, time.format(night.bedTapAt.atZone(night.zone))), f.bedtime)
        GoalRow(stringResource(R.string.goal_row_sleep, formatDuration(goals.sleep)), null, f.duration)
        GoalRow(
            pluralStringResource(R.plurals.goal_row_snoozes, goals.maxSnoozes, goals.maxSnoozes),
            pluralStringResource(R.plurals.snooze_count, night.snoozes, night.snoozes),
            f.snoozes,
        )
        GoalRow(stringResource(R.string.goal_row_consistency), if (f.consistency == null) stringResource(R.string.goal_needs_nights) else null, f.consistency)
    }
}

@Composable
private fun GoalRow(goal: String, detail: String?, factor: Double?) {
    val (mark, colour, label) = when {
        factor == null -> Triple("–", Palette.Muted, R.string.goal_unknown)
        factor >= 0.999 -> Triple("✓", Palette.Good, R.string.goal_met)
        factor > 0.0 -> Triple("◐", Palette.Ember, R.string.goal_partly)
        else -> Triple("✕", Palette.Poor, R.string.goal_missed)
    }
    val labelText = stringResource(label)
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = labelText }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(mark, style = MaterialTheme.typography.titleMedium, color = colour, modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(goal, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted) }
        }
        Text(labelText, style = MaterialTheme.typography.labelMedium, color = colour)
    }
}

/** The measurement behind a score part, shown under its name. */
@Composable
private fun rawValue(c: ScoreComponent, score: ScoreCache?, night: NightRowUi): String? {
    val m = score?.metrics ?: return null
    if (score.total == null) return null
    return when (c) {
        ScoreComponent.DURATION -> m.totalSleepMinutes?.let { formatDuration(Duration.ofMinutes(it)) }
        ScoreComponent.EFFICIENCY -> m.efficiency?.let { stringResource(R.string.raw_percent, percent(it)) }
        ScoreComponent.DEEP_REM -> m.deepRemShare?.let { stringResource(R.string.raw_percent, percent(it)) }
            ?: stringResource(R.string.raw_needs_garmin)
        ScoreComponent.INTERRUPTIONS -> stringResource(
            R.string.raw_pair,
            pluralStringResource(R.plurals.wake_up_count, m.wakeUps ?: 0, m.wakeUps ?: 0),
            pluralStringResource(R.plurals.cluster_count, m.noiseClusters, m.noiseClusters),
        )
        ScoreComponent.REGULARITY -> stringResource(
            R.string.raw_pair,
            stringResource(R.string.raw_bed_at, rememberTimeFormatter().format(night.bedTapAt.atZone(night.zone))),
            pluralStringResource(R.plurals.snooze_count, night.snoozes, night.snoozes),
        )
    }
}
