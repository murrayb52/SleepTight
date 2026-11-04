package io.github.murrayb52.sleeptight.ui.home

import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.alarm.AlarmState
import io.github.murrayb52.sleeptight.tracking.TrackingStatus
import io.github.murrayb52.sleeptight.tracking.TrackingStatus.Problem
import io.github.murrayb52.sleeptight.tracking.TrackingStatus.State
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Everything the Home screen can ask for. */
data class HomeActions(
    val onGoingToBed: () -> Unit = {},
    val onImUp: () -> Unit = {},
    val onKeepNightOpen: (nightId: Long) -> Unit = {},
    val onCloseNightAt: (LocalTime) -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onAllowMicrophone: () -> Unit = {},
    val onRestartTracking: () -> Unit = {},
    val onAllowBattery: () -> Unit = {},
    val onOpenAppSettings: () -> Unit = {},
    val onAlarmToggled: (Boolean) -> Unit = {},
    val onSnoozeAlarm: () -> Unit = {},
    val onDismissAlarm: () -> Unit = {},
    val onAllowExactAlarms: () -> Unit = {},
    val onAllowFullScreen: () -> Unit = {},
)

@Composable
fun HomeRoute(viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    var device by remember { mutableStateOf(readDeviceState(context)) }

    // Re-read permissions and battery state every time the app comes back, e.g. from Settings.
    LifecycleResumeEffect(Unit) {
        device = readDeviceState(context)
        if (device.micGranted) viewModel.onEnsureTracking()
        onPauseOrDispose {}
    }

    val bedPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        device = readDeviceState(context)
        // The night starts either way: taps define it. Without the mic it just has no sound data.
        viewModel.onGoingToBed(trackSound = device.micGranted)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        device = readDeviceState(context)
        if (granted) viewModel.onRestartTracking()
    }

    HomeScreen(
        state = state,
        device = device,
        message = message,
        actions = HomeActions(
            onGoingToBed = {
                val missing = bedTimePermissions.filterNot { p ->
                    when (p) {
                        Manifest.permission.RECORD_AUDIO -> device.micGranted
                        else -> device.notificationsGranted
                    }
                }
                if (missing.isEmpty()) viewModel.onGoingToBed(trackSound = true) else bedPermissions.launch(missing.toTypedArray())
            },
            onImUp = viewModel::onImUp,
            onKeepNightOpen = viewModel::onKeepNightOpen,
            onCloseNightAt = viewModel::onCloseNightAt,
            onMessageShown = viewModel::onMessageShown,
            onAllowMicrophone = {
                // After one denial Android still shows the dialog; after two it won't, so go to Settings.
                if (activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true) {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    context.startActivity(appSettingsIntent(context))
                }
            },
            onRestartTracking = viewModel::onRestartTracking,
            onAllowBattery = { context.startActivity(batteryExemptionIntent(context)) },
            onOpenAppSettings = { context.startActivity(appSettingsIntent(context)) },
            onAlarmToggled = viewModel::onAlarmToggled,
            onSnoozeAlarm = viewModel::onSnoozeAlarm,
            onDismissAlarm = viewModel::onDismissAlarm,
            onAllowExactAlarms = { context.startActivity(exactAlarmSettingsIntent(context)) },
            onAllowFullScreen = { context.startActivity(fullScreenSettingsIntent(context)) },
        ),
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    device: DeviceState,
    message: Int?,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            actions.onMessageShown()
        }
    }

    Scaffold(modifier = modifier.fillMaxSize(), snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        // fillMaxSize before verticalScroll keeps a full-screen minimum height, so the content is
        // centred vertically yet still scrolls at large font sizes.
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!state.loading) {
                HomeContent(state, device, actions)
            }
        }
    }

    state.overduePrompt?.let { prompt ->
        OverdueNightDialog(
            prompt,
            state.zone,
            onKeepOpen = { actions.onKeepNightOpen(prompt.nightId) },
            onCloseAt = actions.onCloseNightAt,
        )
    }
}

private val contentWidth = Modifier.fillMaxWidth().widthIn(max = 480.dp)

@Composable
private fun HomeContent(state: HomeUiState, device: DeviceState, actions: HomeActions) {
    val time = rememberTimeFormatter()

    val alarmState = state.alarm.state
    if (alarmState !is AlarmState.Idle) {
        RingingCard(alarmState, time, state.zone, actions)
        Spacer(Modifier.height(24.dp))
    }

    CentredText(stringResource(R.string.app_name), MaterialTheme.typography.headlineLarge, contentWidth)
    Spacer(Modifier.height(8.dp))

    val open = state.openNight
    if (open != null) {
        CentredText(
            stringResource(R.string.in_bed_since, time.format(open.bedTapAt.atZone(state.zone))),
            MaterialTheme.typography.titleMedium,
            contentWidth,
        )
        CentredText(
            stringResource(R.string.in_bed_for, formatDuration(open.inBedFor)),
            MaterialTheme.typography.bodyLarge,
            contentWidth,
        )
    } else {
        CentredText(stringResource(R.string.tagline), MaterialTheme.typography.bodyLarge, contentWidth)
    }

    Spacer(Modifier.height(40.dp))
    BigTapButton(
        text = stringResource(if (open != null) R.string.im_up else R.string.going_to_bed),
        onClick = if (open != null) actions.onImUp else actions.onGoingToBed,
        emphasiseMorning = open != null,
        modifier = contentWidth,
    )
    Spacer(Modifier.height(32.dp))

    AlarmCard(state.alarm, inBed = open != null, time = time, zone = state.zone, device = device, actions = actions)
    Spacer(Modifier.height(16.dp))

    if (!device.batteryUnrestricted) {
        BatteryCard(device.isSamsung, actions)
        Spacer(Modifier.height(16.dp))
    }

    if (open != null) {
        SoundCard(state.tracking, device, actions)
        Spacer(Modifier.height(16.dp))
        NoiseEventsCard(state.noiseEvents, state.zone, time)
        if (!device.notificationsGranted && state.alarm.tonightEnd == null) {
            Spacer(Modifier.height(16.dp))
            CentredText(
                stringResource(R.string.notifications_off),
                MaterialTheme.typography.bodySmall,
                contentWidth,
            )
        }
    } else {
        val last = state.lastNight
        if (last != null) {
            LastNightCard(last, state.zone, time)
        } else {
            CentredText(stringResource(R.string.first_night_hint), MaterialTheme.typography.bodyMedium, contentWidth)
        }
    }
}

@Composable
private fun BigTapButton(text: String, onClick: () -> Unit, emphasiseMorning: Boolean, modifier: Modifier) {
    val colours = if (emphasiseMorning) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    } else {
        ButtonDefaults.buttonColors()
    }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 112.dp),
        shape = RoundedCornerShape(32.dp),
        colors = colours,
    ) {
        Text(text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun InfoCard(warning: Boolean = false, content: @Composable () -> Unit) {
    val colours = if (warning) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        CardDefaults.cardColors()
    }
    Card(modifier = contentWidth, colors = colours) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun SoundCard(tracking: TrackingStatus, device: DeviceState, actions: HomeActions) {
    val problem = when {
        !device.micGranted -> Problem.MIC_PERMISSION
        tracking.state == State.STOPPED_WITH_PROBLEM -> tracking.problem
        else -> null
    }
    when (problem) {
        Problem.MIC_PERMISSION -> InfoCard(warning = true) {
            CentredText(stringResource(R.string.sound_off_title), MaterialTheme.typography.titleMedium)
            CentredText(stringResource(R.string.sound_off_mic_permission), MaterialTheme.typography.bodyMedium)
            Button(onClick = actions.onAllowMicrophone) { Text(stringResource(R.string.allow_microphone)) }
        }
        Problem.MIC_UNAVAILABLE -> InfoCard(warning = true) {
            CentredText(stringResource(R.string.sound_stopped_title), MaterialTheme.typography.titleMedium)
            CentredText(stringResource(R.string.sound_stopped_mic_unavailable), MaterialTheme.typography.bodyMedium)
            Button(onClick = actions.onRestartTracking) { Text(stringResource(R.string.try_again)) }
        }
        Problem.SERVICE_NOT_ALLOWED -> InfoCard(warning = true) {
            CentredText(stringResource(R.string.sound_stopped_title), MaterialTheme.typography.titleMedium)
            CentredText(stringResource(R.string.sound_stopped_killed), MaterialTheme.typography.bodyMedium)
            Button(onClick = actions.onRestartTracking) { Text(stringResource(R.string.resume)) }
        }
        null -> InfoCard {
            val live = tracking.live
            if (tracking.state != State.RECORDING || live == null) {
                CentredText(stringResource(R.string.sound_starting), MaterialTheme.typography.bodyMedium)
            } else {
                CentredText(stringResource(R.string.sound_listening), MaterialTheme.typography.titleMedium)
                val relative = Math.round(live.dbRelative).toInt()
                CentredText(
                    stringResource(R.string.sound_level_now, relative),
                    MaterialTheme.typography.headlineSmall,
                )
                LinearProgressIndicator(
                    progress = { (live.dbRelative / LEVEL_BAR_MAX_DB).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                CentredText(stringResource(R.string.sound_no_audio_stored), MaterialTheme.typography.bodySmall)
                if (live.micSilent) {
                    CentredText(
                        stringResource(R.string.sound_mic_silent),
                        MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error),
                    )
                }
            }
        }
    }
}

/** The live level bar is full at this many dB above baseline (the noise threshold is 15). */
private const val LEVEL_BAR_MAX_DB = 30.0

/** Shows at most this many events; older ones are summarised. */
private const val MAX_EVENTS_LISTED = 10

@Composable
private fun NoiseEventsCard(events: List<NoiseEventUi>, zone: ZoneId, time: DateTimeFormatter) {
    InfoCard {
        CentredText(stringResource(R.string.noise_events_title, events.size), MaterialTheme.typography.titleMedium)
        if (events.isEmpty()) {
            CentredText(stringResource(R.string.noise_events_none), MaterialTheme.typography.bodyMedium)
        }
        events.take(MAX_EVENTS_LISTED).forEach { event ->
            CentredText(
                stringResource(
                    R.string.noise_event_line,
                    time.format(event.start.atZone(zone)),
                    event.duration.toSeconds(),
                    event.peakDbRelative,
                ),
                MaterialTheme.typography.bodyMedium,
            )
        }
        if (events.size > MAX_EVENTS_LISTED) {
            CentredText(
                stringResource(R.string.noise_events_more, events.size - MAX_EVENTS_LISTED),
                MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: AlarmUi,
    inBed: Boolean,
    time: DateTimeFormatter,
    zone: ZoneId,
    device: DeviceState,
    actions: HomeActions,
) {
    InfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.alarm_title), style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = if (inBed) alarm.tonightEnd != null else alarm.enabled,
                onCheckedChange = actions.onAlarmToggled,
                modifier = Modifier.testTag("alarmSwitch"),
            )
        }
        val windowEnd = time.format(alarm.windowEnd)
        when {
            inBed && alarm.firedAt != null ->
                CentredText(stringResource(R.string.alarm_rang_at, time.format(alarm.firedAt.atZone(zone))), MaterialTheme.typography.bodyLarge)
            inBed && alarm.tonightStart != null && alarm.tonightEnd != null -> {
                CentredText(
                    stringResource(
                        R.string.alarm_tonight,
                        time.format(alarm.tonightStart.atZone(zone)),
                        time.format(alarm.tonightEnd.atZone(zone)),
                    ),
                    MaterialTheme.typography.bodyLarge,
                )
                CentredText(
                    stringResource(R.string.alarm_explainer, time.format(alarm.tonightEnd.atZone(zone))),
                    MaterialTheme.typography.bodySmall,
                )
            }
            inBed -> CentredText(stringResource(R.string.alarm_none_tonight), MaterialTheme.typography.bodyLarge)
            alarm.enabled -> {
                CentredText(
                    stringResource(R.string.alarm_window, time.format(alarm.windowStart), windowEnd),
                    MaterialTheme.typography.headlineSmall,
                )
                CentredText(stringResource(R.string.alarm_explainer, windowEnd), MaterialTheme.typography.bodySmall)
            }
            else -> CentredText(stringResource(R.string.alarm_off_tonight), MaterialTheme.typography.bodyMedium)
        }

        val alarmMatters = if (inBed) alarm.tonightEnd != null else alarm.enabled
        if (alarmMatters) {
            if (!device.exactAlarmsAllowed) {
                Warning(stringResource(R.string.alarm_exact_denied))
                Button(onClick = actions.onAllowExactAlarms) { Text(stringResource(R.string.allow)) }
            }
            if (device.alarmVolumeOff) Warning(stringResource(R.string.alarm_volume_off))
            if (!device.notificationsGranted) {
                Warning(stringResource(R.string.alarm_notifications_off))
            } else if (!device.fullScreenAllowed) {
                Warning(stringResource(R.string.alarm_full_screen_denied))
                Button(onClick = actions.onAllowFullScreen) { Text(stringResource(R.string.allow)) }
            }
        }
    }
}

@Composable
private fun RingingCard(state: AlarmState, time: DateTimeFormatter, zone: ZoneId, actions: HomeActions) {
    InfoCard(warning = true) {
        val title = when (state) {
            is AlarmState.Snoozed -> stringResource(R.string.alarm_snoozed_title, time.format(state.until.atZone(zone)))
            else -> stringResource(R.string.alarm_good_morning)
        }
        CentredText(title, MaterialTheme.typography.titleLarge)
        Button(onClick = actions.onDismissAlarm) { Text(stringResource(R.string.alarm_dismiss)) }
        if (state is AlarmState.Ringing) {
            OutlinedButton(onClick = actions.onSnoozeAlarm) { Text(stringResource(R.string.alarm_snooze_short)) }
        }
    }
}

@Composable
private fun Warning(text: String) {
    CentredText(text, MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error))
}

@Composable
private fun BatteryCard(isSamsung: Boolean, actions: HomeActions) {
    InfoCard {
        CentredText(stringResource(R.string.battery_title), MaterialTheme.typography.titleMedium)
        CentredText(stringResource(R.string.battery_body), MaterialTheme.typography.bodyMedium)
        Button(onClick = actions.onAllowBattery) { Text(stringResource(R.string.battery_allow)) }
        if (isSamsung) {
            CentredText(stringResource(R.string.battery_samsung), MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = actions.onOpenAppSettings) { Text(stringResource(R.string.open_app_settings)) }
        }
    }
}

@Composable
private fun LastNightCard(last: LastNightUi, zone: ZoneId, time: DateTimeFormatter) {
    InfoCard {
        CentredText(stringResource(R.string.last_night), MaterialTheme.typography.titleMedium)
        CentredText(
            stringResource(
                R.string.time_range,
                time.format(last.bedTapAt.atZone(zone)),
                time.format(last.wakeTapAt.atZone(zone)),
            ),
            MaterialTheme.typography.headlineSmall,
        )
        CentredText(
            stringResource(R.string.time_in_bed, formatDuration(last.timeInBed)),
            MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverdueNightDialog(
    prompt: OverduePrompt,
    zone: ZoneId,
    onKeepOpen: () -> Unit,
    onCloseAt: (LocalTime) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val dayAndTime = DateTimeFormatter.ofPattern(if (is24Hour) "EEE d MMM, HH:mm" else "EEE d MMM, h:mm a")

    if (!picking) {
        AlertDialog(
            onDismissRequest = onKeepOpen,
            title = { Text(stringResource(R.string.overdue_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.overdue_body,
                        dayAndTime.format(prompt.bedTapAt.atZone(zone)),
                        formatDuration(prompt.openFor),
                    ),
                )
            },
            confirmButton = { TextButton(onClick = { picking = true }) { Text(stringResource(R.string.overdue_set_wake_time)) } },
            dismissButton = { TextButton(onClick = onKeepOpen) { Text(stringResource(R.string.overdue_keep_open)) } },
        )
    } else {
        val pickerState = rememberTimePickerState(
            initialHour = prompt.suggestedWakeTime.hour,
            initialMinute = prompt.suggestedWakeTime.minute,
            is24Hour = is24Hour,
        )
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.wake_picker_title)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    onCloseAt(LocalTime.of(pickerState.hour, pickerState.minute))
                }) { Text(stringResource(R.string.close_night)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Text that stays centred when it wraps onto several lines on narrow screens. */
@Composable
private fun CentredText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text = text, style = style, textAlign = TextAlign.Center, modifier = modifier)
}

@Composable
private fun rememberTimeFormatter(): DateTimeFormatter {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(is24Hour) { DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a") }
}

@Composable
private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}

@Preview(widthDp = 320)
@Composable
private fun HomeScreenNarrowPreview() {
    val now = Instant.parse("2026-11-04T05:00:00Z")
    SleepTightTheme {
        HomeScreen(
            state = HomeUiState(
                loading = false,
                lastNight = LastNightUi(now.minus(Duration.ofHours(32)), now.minus(Duration.ofHours(24)), Duration.ofHours(8)),
            ),
            device = DeviceState(),
            message = null,
            actions = HomeActions(),
        )
    }
}
