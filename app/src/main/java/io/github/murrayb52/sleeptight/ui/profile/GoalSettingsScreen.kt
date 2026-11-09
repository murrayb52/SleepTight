package io.github.murrayb52.sleeptight.ui.profile

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.ui.components.StandardPickerType
import io.github.murrayb52.sleeptight.ui.home.formatDuration
import io.github.murrayb52.sleeptight.ui.home.rememberTimeFormatter
import io.github.murrayb52.sleeptight.ui.theme.Palette
import java.time.Duration
import java.time.LocalTime

/** Sleep goal: whole hours (the notches). */
private val SLEEP_GOAL_HOURS = 5f..10f

/** Snooze goal: at most 0 to 5 (the alarm itself can allow up to 10). */
private val SNOOZE_GOAL = 0f..5f

@Composable
fun GoalSettingsRoute(onBack: () -> Unit, viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GoalSettingsScreen(
        state.settings,
        onBack = onBack,
        onBedtime = viewModel::onBedtime,
        onSleepGoal = viewModel::onSleepGoal,
        onSnoozeGoal = viewModel::onSnoozeGoal,
    )
}

/** The goals each night is checked against: bedtime, sleep and snoozes. */
@Composable
fun GoalSettingsScreen(
    settings: Settings,
    onBack: () -> Unit = {},
    onBedtime: (LocalTime) -> Unit = {},
    onSleepGoal: (Int) -> Unit = {},
    onSnoozeGoal: (Int) -> Unit = {},
) {
    val time = rememberTimeFormatter()
    val bedtime = LocalTime.MIDNIGHT.plusMinutes(settings.targetBedtimeMinutes.toLong())
    var picking by rememberSaveable { mutableStateOf(false) }

    SettingsPage(stringResource(R.string.settings_goals), onBack) {
        Section(null) {
            ValueRow(stringResource(R.string.goal_bedtime), time.format(bedtime)) { picking = true }
            SliderRow(
                label = stringResource(R.string.goal_sleep),
                value = settings.targetSleepMinutes / 60f,
                range = SLEEP_GOAL_HOURS,
                valueText = { formatDuration(Duration.ofMinutes(Math.round(it * 60).toLong())) },
                onDone = { onSleepGoal(Math.round(it) * 60) },
                tag = "sleepGoal",
            )
            SliderRow(
                label = stringResource(R.string.goal_snoozes),
                value = settings.maxSnoozes.toFloat(),
                range = SNOOZE_GOAL,
                valueText = { stringResource(R.string.at_most, Math.round(it)) },
                onDone = { onSnoozeGoal(Math.round(it)) },
                tag = "snoozeGoal",
                last = true,
            )
        }
    }

    if (picking) BedtimeDialog(bedtime, onPick = { onBedtime(it); picking = false }, onDismiss = { picking = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BedtimeDialog(current: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(current.hour, current.minute, is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_bedtime)) },
        text = { StandardPickerType { TimePicker(state) } },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = Palette.Card,
    )
}
