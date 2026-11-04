package io.github.murrayb52.sleeptight.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.alarm.AlarmSound
import io.github.murrayb52.sleeptight.health.HealthConnectGarminSource
import io.github.murrayb52.sleeptight.health.SourceStatus
import io.github.murrayb52.sleeptight.ui.components.SectionLabel
import io.github.murrayb52.sleeptight.ui.components.StandardPickerType
import io.github.murrayb52.sleeptight.ui.history.Tile
import io.github.murrayb52.sleeptight.ui.home.formatDuration
import io.github.murrayb52.sleeptight.ui.home.rememberTimeFormatter
import io.github.murrayb52.sleeptight.ui.theme.Palette
import java.time.Duration
import java.time.LocalTime

private enum class Picker { BEDTIME, WINDOW, SOUND }

/** Sleep goal: whole hours (the notches). */
private val SLEEP_GOAL_HOURS = 5f..10f

/** Snooze goal: at most 0 to 5, the most the alarm allows. */
private val SNOOZE_GOAL = 0f..5f

@Composable
fun ProfileRoute(onOpenCredits: () -> Unit, viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val askHealthConnect = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.onGarminPermissions(it)
    }
    // Back from Health Connect (or anywhere else): the connection may have changed.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshGarmin()
        onPauseOrDispose {}
    }
    ProfileScreen(
        state,
        onBedtime = viewModel::onBedtime,
        onSleepGoal = viewModel::onSleepGoal,
        onSnoozeGoal = viewModel::onSnoozeGoal,
        onWindow = viewModel::onWindow,
        onReminder = viewModel::onReminder,
        onBedsideCheck = viewModel::onBedsideCheck,
        onSound = viewModel::onSound,
        onOpenCredits = onOpenCredits,
        onGarmin = {
            when (state.garmin.status) {
                SourceStatus.UPDATE_REQUIRED -> context.startActivity(healthConnectInstallIntent())
                // After repeated refusals Health Connect stops asking, so send people to its settings instead.
                SourceStatus.NEEDS_PERMISSION ->
                    if (state.garmin.denied) {
                        context.startActivity(HealthConnectClient.getHealthConnectManageDataIntent(context))
                    } else {
                        askHealthConnect.launch(HealthConnectGarminSource.PERMISSIONS)
                    }
                SourceStatus.READY -> context.startActivity(HealthConnectClient.getHealthConnectManageDataIntent(context))
                SourceStatus.UNAVAILABLE, null -> Unit
            }
        },
    )
}

/** The Play Store page for Health Connect (needed on Android 13 and earlier, where it's a separate app). */
private fun healthConnectInstallIntent() = Intent(
    Intent.ACTION_VIEW,
    Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"),
).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
fun ProfileScreen(
    state: ProfileUi,
    onBedtime: (LocalTime) -> Unit = {},
    onSleepGoal: (Int) -> Unit = {},
    onSnoozeGoal: (Int) -> Unit = {},
    onWindow: (Int) -> Unit = {},
    onReminder: (Boolean) -> Unit = {},
    onBedsideCheck: (Boolean) -> Unit = {},
    onSound: (String?) -> Unit = {},
    onOpenCredits: () -> Unit = {},
    onGarmin: () -> Unit = {},
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
            SliderRow(
                label = stringResource(R.string.goal_sleep),
                value = s.targetSleepMinutes / 60f,
                range = SLEEP_GOAL_HOURS,
                valueText = { formatDuration(Duration.ofMinutes(Math.round(it * 60).toLong())) },
                onDone = { onSleepGoal(Math.round(it) * 60) },
                tag = "sleepGoal",
            )
            SliderRow(
                label = stringResource(R.string.goal_snoozes),
                value = s.maxSnoozes.toFloat(),
                range = SNOOZE_GOAL,
                valueText = { stringResource(R.string.at_most, Math.round(it)) },
                onDone = { onSnoozeGoal(Math.round(it)) },
                tag = "snoozeGoal",
                last = true,
            )
        }
        Section(stringResource(R.string.section_alarm)) {
            ValueRow(stringResource(R.string.alarm_window_row), stringResource(R.string.window_option, window)) { picker = Picker.WINDOW }
            ValueRow(stringResource(R.string.alarm_sound_row), stringResource(AlarmSound.of(s.alarmSound).label), last = true) { picker = Picker.SOUND }
        }
        Section(stringResource(R.string.section_reminders)) {
            ToggleRow(stringResource(R.string.reminder_row), s.bedtimeReminderEnabled, onReminder)
            ToggleRow(stringResource(R.string.bedside_row), s.bedsideCheckEnabled, onBedsideCheck, last = true)
        }
        Section(stringResource(R.string.section_data)) {
            val g = state.garmin
            ValueRow(
                stringResource(R.string.data_garmin),
                stringResource(
                    when (g.status) {
                        null -> R.string.garmin_checking
                        SourceStatus.UNAVAILABLE -> R.string.garmin_unavailable
                        SourceStatus.UPDATE_REQUIRED -> R.string.garmin_install
                        SourceStatus.NEEDS_PERMISSION -> if (g.denied) R.string.garmin_open_settings else R.string.garmin_connect
                        SourceStatus.READY -> R.string.garmin_connected
                    },
                ),
                enabled = g.status != null && g.status != SourceStatus.UNAVAILABLE,
                onClick = onGarmin,
            )
            ValueRow(stringResource(R.string.data_export), stringResource(R.string.coming_soon), enabled = false, last = true) {}
        }
        Text(
            stringResource(
                when {
                    state.garmin.denied && state.garmin.status == SourceStatus.NEEDS_PERMISSION -> R.string.garmin_denied_note
                    state.garmin.status == SourceStatus.READY -> R.string.garmin_connected_note
                    else -> R.string.garmin_note
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (state.garmin.denied && state.garmin.status == SourceStatus.NEEDS_PERMISSION) Palette.Poor else Palette.Muted,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
        )
        Section(stringResource(R.string.section_about)) {
            ValueRow(stringResource(R.string.credits_row), "", last = true, onClick = onOpenCredits)
        }
        Text(stringResource(R.string.version, version), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(start = 4.dp))
    }

    when (picker) {
        Picker.BEDTIME -> BedtimeDialog(bedtime, onPick = { onBedtime(it); picker = null }, onDismiss = { picker = null })
        Picker.WINDOW -> ChoiceDialog(
            stringResource(R.string.window_title),
            listOf(15, 20, 30, 45, 60),
            window,
            label = { stringResource(R.string.window_option, it) },
            onPick = { onWindow(it); picker = null },
            onDismiss = { picker = null },
        )
        Picker.SOUND -> SoundPickerDialog(AlarmSound.of(s.alarmSound), onPick = onSound, onDismiss = { picker = null })
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

/**
 * A setting with a notched slider (one notch per whole unit). The value shown follows the thumb;
 * [onDone] saves it when you let go, so moving it doesn't save (and rescore) on every notch.
 */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: @Composable (Float) -> String,
    onDone: (Float) -> Unit,
    tag: String,
    last: Boolean = false,
) {
    var current by remember(value) { mutableFloatStateOf(value.coerceIn(range)) }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(valueText(current), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSoft)
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onDone(current) },
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = Palette.Ember,
                activeTrackColor = Palette.Ember,
                inactiveTrackColor = Palette.Outline,
                activeTickColor = Palette.OnEmber,
                inactiveTickColor = Palette.Muted,
            ),
            modifier = Modifier.testTag(tag),
        )
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
        text = { StandardPickerType { TimePicker(state) } },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = Palette.Card,
    )
}
