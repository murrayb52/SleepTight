package io.github.murrayb52.sleeptight.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.alarm.AlarmSound
import io.github.murrayb52.sleeptight.alarm.alarmConfig
import io.github.murrayb52.sleeptight.alarm.snoozeCurveOrDefault
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.metrics.AlarmConfig
import io.github.murrayb52.sleeptight.metrics.SnoozeCurve
import io.github.murrayb52.sleeptight.metrics.SnoozeLadder
import io.github.murrayb52.sleeptight.ui.components.SectionLabel
import io.github.murrayb52.sleeptight.ui.theme.Palette

/** Snoozes the alarm allows: none up to the most the setting takes. */
private val SNOOZES_ALLOWED = 0f..AlarmConfig().maxSnoozesSetting.toFloat()

/** How long the first snooze can be set to run. */
private val FIRST_SNOOZE = AlarmConfig().minFirstSnoozeMinutes.toFloat()..AlarmConfig().maxFirstSnoozeMinutes.toFloat()

private enum class AlarmPicker { WINDOW, SOUND }

@Composable
fun AlarmSettingsRoute(onBack: () -> Unit, viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlarmSettingsScreen(
        state.settings,
        onBack = onBack,
        onWindow = viewModel::onWindow,
        onSound = viewModel::onSound,
        onSnoozesAllowed = viewModel::onSnoozesAllowed,
        onSnoozeCurve = viewModel::onSnoozeCurve,
        onFirstSnooze = viewModel::onFirstSnooze,
    )
}

/** The wake window, the sound, and how many snoozes there are and how they shorten. */
@Composable
fun AlarmSettingsScreen(
    settings: Settings,
    onBack: () -> Unit = {},
    onWindow: (Int) -> Unit = {},
    onSound: (String?) -> Unit = {},
    onSnoozesAllowed: (Int) -> Unit = {},
    onSnoozeCurve: (SnoozeCurve) -> Unit = {},
    onFirstSnooze: (Int) -> Unit = {},
) {
    val window = Math.floorMod(settings.alarmWindowEndMinutes - settings.alarmWindowStartMinutes, 24 * 60)
    var picker by rememberSaveable { mutableStateOf<AlarmPicker?>(null) }
    // Follows the slider while it's dragged, so the charts change as you go.
    var snoozes by remember(settings.snoozesAllowed) { mutableIntStateOf(settings.snoozesAllowed) }
    var firstSnooze by remember(settings.firstSnoozeMinutes) { mutableIntStateOf(settings.firstSnoozeMinutes) }
    val curve = settings.snoozeCurveOrDefault()

    SettingsPage(stringResource(R.string.settings_alarm), onBack) {
        Section(null) {
            ValueRow(stringResource(R.string.alarm_window_row), stringResource(R.string.window_option, window)) { picker = AlarmPicker.WINDOW }
            ValueRow(stringResource(R.string.alarm_sound_row), stringResource(AlarmSound.of(settings.alarmSound).label), last = true) {
                picker = AlarmPicker.SOUND
            }
        }
        Section(stringResource(R.string.section_snoozes)) {
            SliderRow(
                label = stringResource(R.string.snoozes_allowed_row),
                value = settings.snoozesAllowed.toFloat(),
                range = SNOOZES_ALLOWED,
                valueText = { n -> if (Math.round(n) == 0) stringResource(R.string.snoozes_none) else Math.round(n).toString() },
                onDone = { onSnoozesAllowed(Math.round(it)) },
                onChange = { snoozes = Math.round(it) },
                tag = "snoozesAllowed",
            )
            SliderRow(
                label = stringResource(R.string.first_snooze_row),
                value = settings.firstSnoozeMinutes.toFloat(),
                range = FIRST_SNOOZE,
                valueText = { m -> stringResource(R.string.minutes_short, Math.round(m)) },
                onDone = { onFirstSnooze(Math.round(it)) },
                onChange = { firstSnooze = Math.round(it) },
                tag = "firstSnooze",
                last = true,
            )
            SnoozeCurvePicker(snoozes, curve, settings.alarmConfig().copy(firstSnoozeMinutes = firstSnooze), onSnoozeCurve)
        }
    }

    when (picker) {
        AlarmPicker.WINDOW -> ChoiceDialog(
            stringResource(R.string.window_title),
            listOf(15, 20, 30, 45, 60),
            window,
            label = { stringResource(R.string.window_option, it) },
            onPick = { onWindow(it); picker = null },
            onDismiss = { picker = null },
        )
        AlarmPicker.SOUND -> SoundPickerDialog(AlarmSound.of(settings.alarmSound), onPick = onSound, onDismiss = { picker = null })
        null -> Unit
    }
}

/**
 * The three snooze curves side by side, each drawn as bars (one per snooze, as tall as it is long),
 * with the chosen one highlighted, then the total time they add up to. With fewer than two snoozes
 * the curves are all the same, so only the total is shown.
 */
@Composable
private fun SnoozeCurvePicker(snoozes: Int, current: SnoozeCurve, config: AlarmConfig, onPick: (SnoozeCurve) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (snoozes >= 2) {
            SectionLabel(stringResource(R.string.snooze_curve_row))
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SnoozeCurve.entries.forEach { curve ->
                    CurveCard(
                        curve,
                        SnoozeLadder.lengths(snoozes, curve, config),
                        selected = curve == current,
                        onClick = { onPick(curve) },
                        Modifier.weight(1f),
                    )
                }
            }
        }
        val total = SnoozeLadder.lengths(snoozes, current, config).sum()
        Text(
            if (snoozes == 0) stringResource(R.string.snooze_total_none) else pluralStringResource(R.plurals.snooze_total, total, total),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
            modifier = Modifier.testTag("snoozeTotal"),
        )
    }
}

@Composable
private fun CurveCard(curve: SnoozeCurve, lengths: List<Int>, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val name = stringResource(curve.label)
    // Screen readers get the minutes the picture shows.
    val description = stringResource(R.string.snooze_curve_a11y, name, lengths.joinToString(", "))
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Palette.Raised else Palette.CardDim)
            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Ember else Palette.Line, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag("curve_${curve.name}")
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Only the bars are inset: the centred label below needs the card's full width, or
        // "Exponential" doesn't fit on one line in a third of a 360dp screen.
        SnoozeBars(
            lengths,
            if (selected) Palette.Ember else Palette.Moon.copy(alpha = 0.55f),
            Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(56.dp),
        )
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Palette.Text else Palette.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One bar per snooze, left to right, scaled so the first (longest possible) snooze fills the height. */
@Composable
private fun SnoozeBars(lengths: List<Int>, colour: Color, modifier: Modifier = Modifier) {
    // Scaled against the longest a first snooze can be, so the bars keep their meaning
    // (and stay inside the card) whatever the first snooze is set to.
    val tallest = AlarmConfig().maxFirstSnoozeMinutes.toFloat()
    val baseline = Palette.Line
    Canvas(modifier) {
        if (lengths.isEmpty()) return@Canvas
        val gap = (size.width / lengths.size) * 0.25f
        val barWidth = (size.width - gap * (lengths.size - 1)) / lengths.size
        lengths.forEachIndexed { i, minutes ->
            val h = size.height * (minutes / tallest).coerceIn(0.06f, 1f)
            drawRoundRect(
                colour,
                topLeft = Offset(i * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth * 0.3f),
            )
        }
        drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
    }
}

internal val SnoozeCurve.label: Int
    get() = when (this) {
        SnoozeCurve.CONSTANT -> R.string.snooze_curve_constant
        SnoozeCurve.LINEAR -> R.string.snooze_curve_linear
        SnoozeCurve.EXPONENTIAL -> R.string.snooze_curve_exponential
    }
