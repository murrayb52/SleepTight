package io.github.murrayb52.sleeptight.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.ui.components.SectionLabel
import io.github.murrayb52.sleeptight.ui.history.Tile
import io.github.murrayb52.sleeptight.ui.home.formatDuration
import io.github.murrayb52.sleeptight.ui.home.rememberTimeFormatter
import io.github.murrayb52.sleeptight.ui.theme.Palette
import java.time.Duration
import java.time.LocalTime

private enum class Picker { BEDTIME, SLEEP, SNOOZES, WINDOW }

@Composable
fun ProfileRoute(viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(
        state,
        onBedtime = viewModel::onBedtime,
        onSleepGoal = viewModel::onSleepGoal,
        onSnoozeGoal = viewModel::onSnoozeGoal,
        onWindow = viewModel::onWindow,
        onReminder = viewModel::onReminder,
        onBedsideCheck = viewModel::onBedsideCheck,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUi,
    onBedtime: (LocalTime) -> Unit = {},
    onSleepGoal: (Int) -> Unit = {},
    onSnoozeGoal: (Int) -> Unit = {},
    onWindow: (Int) -> Unit = {},
    onReminder: (Boolean) -> Unit = {},
    onBedsideCheck: (Boolean) -> Unit = {},
) {
    val time = rememberTimeFormatter()
    val s = state.settings
    val bedtime = LocalTime.MIDNIGHT.plusMinutes(s.targetBedtimeMinutes.toLong())
    val window = Math.floorMod(s.alarmWindowEndMinutes - s.alarmWindowStartMinutes, 24 * 60)
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    val context = LocalContext.current
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }

    Column(
        Modifier.fillMaxSize().background(Palette.Sky).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.tab_profile), style = MaterialTheme.typography.headlineLarge)
        val avg = state.allTime
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile((avg?.nights ?: 0).toString(), stringResource(R.string.stat_nights), Modifier.weight(1f))
            Tile(avg?.let { "${it.byGoalPercent} %" } ?: "–", stringResource(R.string.stat_by_goal, time.format(bedtime)), Modifier.weight(1f))
            Tile(avg?.let { "%.1f".format(it.snoozesPerNight) } ?: "–", stringResource(R.string.stat_snoozes), Modifier.weight(1f))
        }

        Section(stringResource(R.string.section_goals)) {
            ValueRow(stringResource(R.string.goal_bedtime), time.format(bedtime)) { picker = Picker.BEDTIME }
            ValueRow(stringResource(R.string.goal_sleep), formatDuration(Duration.ofMinutes(s.targetSleepMinutes.toLong()))) { picker = Picker.SLEEP }
            ValueRow(stringResource(R.string.goal_snoozes), stringResource(R.string.at_most, s.maxSnoozes), last = true) { picker = Picker.SNOOZES }
        }
        Section(stringResource(R.string.section_alarm)) {
            ValueRow(stringResource(R.string.alarm_window_row), stringResource(R.string.window_option, window)) { picker = Picker.WINDOW }
            ValueRow(stringResource(R.string.alarm_sound_row), stringResource(R.string.sound_chime), enabled = false, last = true) {}
        }
        Section(stringResource(R.string.section_reminders)) {
            ToggleRow(stringResource(R.string.reminder_row), s.bedtimeReminderEnabled, onReminder)
            ToggleRow(stringResource(R.string.bedside_row), s.bedsideCheckEnabled, onBedsideCheck, last = true)
        }
        Section(stringResource(R.string.section_data)) {
            ValueRow(stringResource(R.string.data_garmin), stringResource(R.string.coming_soon), enabled = false) {}
            ValueRow(stringResource(R.string.data_export), stringResource(R.string.coming_soon), enabled = false, last = true) {}
        }
        Text(stringResource(R.string.version, version), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(start = 4.dp))
    }

    when (picker) {
        Picker.BEDTIME -> BedtimeDialog(bedtime, onPick = { onBedtime(it); picker = null }, onDismiss = { picker = null })
        Picker.SLEEP -> ChoiceDialog(
            stringResource(R.string.goal_sleep),
            (12..20).map { it * 30 },
            s.targetSleepMinutes,
            label = { formatDuration(Duration.ofMinutes(it.toLong())) },
            onPick = { onSleepGoal(it); picker = null },
            onDismiss = { picker = null },
        )
        Picker.SNOOZES -> ChoiceDialog(
            stringResource(R.string.goal_snoozes),
            listOf(0, 1, 2, 3),
            s.maxSnoozes,
            label = { stringResource(R.string.at_most, it) },
            onPick = { onSnoozeGoal(it); picker = null },
            onDismiss = { picker = null },
        )
        Picker.WINDOW -> ChoiceDialog(
            stringResource(R.string.window_title),
            listOf(15, 20, 30, 45, 60),
            window,
            label = { stringResource(R.string.window_option, it) },
            onPick = { onWindow(it); picker = null },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(title)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Card), content = content)
    }
}

@Composable
private fun ValueRow(label: String, value: String, enabled: Boolean = true, last: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = if (enabled) Palette.TextSoft else Palette.Muted)
            if (enabled) Text("›", style = MaterialTheme.typography.titleLarge, color = Palette.Muted)
        }
    }
    if (!last) HorizontalDivider(color = Palette.Line, modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit, last: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = on,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Ember, checkedThumbColor = Palette.OnEmber),
        )
    }
    if (!last) HorizontalDivider(color = Palette.Line, modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<T>, current: T, label: @Composable (T) -> String, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    TextButton(onClick = { onPick(o) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label(o) + if (o == current) "  ✓" else "", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = Palette.Card,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BedtimeDialog(current: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(current.hour, current.minute, is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_bedtime)) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = Palette.Card,
    )
}
