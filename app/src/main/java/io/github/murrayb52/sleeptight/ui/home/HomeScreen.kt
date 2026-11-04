package io.github.murrayb52.sleeptight.ui.home

import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.alarm.AlarmState
import io.github.murrayb52.sleeptight.tracking.Notifications
import io.github.murrayb52.sleeptight.tracking.TrackingStatus.Problem
import io.github.murrayb52.sleeptight.tracking.TrackingStatus.State
import io.github.murrayb52.sleeptight.ui.components.CentredText
import io.github.murrayb52.sleeptight.ui.components.HoldButton
import io.github.murrayb52.sleeptight.ui.components.Panel
import io.github.murrayb52.sleeptight.ui.components.ScoreDial
import io.github.murrayb52.sleeptight.ui.components.StandardPickerType
import io.github.murrayb52.sleeptight.ui.components.StarrySky
import io.github.murrayb52.sleeptight.ui.components.WheelPicker
import io.github.murrayb52.sleeptight.ui.theme.Palette
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.sin

/** Everything the Sleep tab can ask for. */
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
    val onStopAlarm: () -> Unit = {},
    val onAllowExactAlarms: () -> Unit = {},
    val onAllowFullScreen: () -> Unit = {},
    val onWakeByChanged: (LocalTime) -> Unit = {},
    val onWindowLengthChanged: (Int) -> Unit = {},
    val onOpenNight: (nightId: Long) -> Unit = {},
)

@Composable
fun HomeRoute(onOpenNight: (Long) -> Unit, viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    var device by remember { mutableStateOf(readDeviceState(context)) }
    var showBedsideCheck by rememberSaveable { mutableStateOf(false) }

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

    fun startNight() {
        Notifications.cancelBedtimeReminder(context)
        val missing = bedTimePermissions.filterNot { p ->
            when (p) {
                Manifest.permission.RECORD_AUDIO -> device.micGranted
                else -> device.notificationsGranted
            }
        }
        if (missing.isEmpty()) viewModel.onGoingToBed(trackSound = true) else bedPermissions.launch(missing.toTypedArray())
    }

    HomeScreen(
        state = state,
        device = device,
        message = message,
        actions = HomeActions(
            onGoingToBed = { if (state.goals.bedsideCheckEnabled) showBedsideCheck = true else startNight() },
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
            onStopAlarm = viewModel::onStopAlarm,
            onAllowExactAlarms = { context.startActivity(exactAlarmSettingsIntent(context)) },
            onAllowFullScreen = { context.startActivity(fullScreenSettingsIntent(context)) },
            onWakeByChanged = viewModel::onWakeByChanged,
            onWindowLengthChanged = viewModel::onWindowLengthChanged,
            onOpenNight = onOpenNight,
        ),
    )

    if (showBedsideCheck) {
        BedsideCheckSheet(
            onStart = { dontShowAgain ->
                showBedsideCheck = false
                if (dontShowAgain) viewModel.onBedsideCheckDontShow()
                startNight()
            },
            onDismiss = { showBedsideCheck = false },
        )
    }
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    device: DeviceState,
    message: Int?,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    twinkle: Boolean = true,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            actions.onMessageShown()
        }
    }

    val inBed = state.openNight != null
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = if (inBed) Palette.DeepNight else Palette.Sky,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            StarrySky(Modifier.fillMaxSize(), count = if (inBed) 80 else 40, twinkle = twinkle)
            // fillMaxSize before verticalScroll keeps a full-screen minimum height, so content can
            // sit at the bottom yet still scroll at large font sizes.
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                // Two groups: the top of the screen and the action at the bottom.
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                if (!state.loading) {
                    if (inBed) TonightContent(state, device, actions, animate = twinkle) else SleepContent(state, device, actions)
                }
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

// ---------------------------------------------------------------- Sleep (before bed)

@Composable
private fun SleepContent(state: HomeUiState, device: DeviceState, actions: HomeActions) {
    val time = rememberTimeFormatter()
    val now = Instant.now().atZone(state.zone)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        Row(contentWidth, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(DateTimeFormatter.ofPattern("EEEE d MMMM").format(now), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                Text(stringResource(greetingFor(now.toLocalTime())), style = MaterialTheme.typography.headlineMedium)
            }
            Moon(Modifier.size(54.dp))
    }

    Spacer(Modifier.height(20.dp))
    Panel(contentWidth) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.bedtime_goal, time.format(state.goals.bedtime)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(
                if (state.goals.reminderEnabled) {
                    stringResource(R.string.reminder_at, time.format(state.goals.bedtime.minusMinutes(30)))
                } else {
                    stringResource(R.string.reminder_off)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
                textAlign = TextAlign.End,
            )
        }
    }

    SetupWarnings(alarmOn = true, device, actions)

    Spacer(Modifier.height(28.dp))
    val alarm = state.alarm
    Text(stringResource(R.string.wake_me_by).uppercase(), style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
    Spacer(Modifier.height(4.dp))
    WheelPicker(
        values = wakeByValues,
        selected = alarm.windowEnd,
        label = { time.format(it) },
        onSelected = actions.onWakeByChanged,
        // "6:15 AM" needs a smaller size than "06:15" to fit the selection box.
        selectedStyle = if (DateFormat.is24HourFormat(LocalContext.current)) {
            MaterialTheme.typography.displayMedium
        } else {
            MaterialTheme.typography.displaySmall
        },
        otherStyle = MaterialTheme.typography.headlineLarge.copy(color = Palette.Faint),
        description = stringResource(R.string.wake_me_by),
        modifier = contentWidth.testTag("wakeByWheel"),
        // Round to the nearest 5 minutes if the stored time is off the grid.
        nearest = { _, t -> Math.floorMod(Math.round((t.hour * 60 + t.minute) / 5.0).toInt(), 24 * 12) },
    )

    Spacer(Modifier.height(12.dp))
    var choosingWindow by rememberSaveable { mutableStateOf(false) }
    Chip(stringResource(R.string.window_chip, alarm.windowMinutes)) { choosingWindow = true }
    Spacer(Modifier.height(10.dp))
    // Nothing is set yet: the alarm is armed by the bed tap, for this window.
    CentredText(
        stringResource(R.string.alarm_explainer, time.format(alarm.windowStart), time.format(alarm.windowEnd)),
        MaterialTheme.typography.bodyMedium,
        contentWidth,
        colour = Palette.TextSoft,
    )
    if (choosingWindow) {
        WindowLengthDialog(alarm.windowMinutes, onPick = { actions.onWindowLengthChanged(it); choosingWindow = false }, onDismiss = { choosingWindow = false })
    }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = actions.onGoingToBed,
            modifier = contentWidth.heightIn(min = 64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Ember, contentColor = Palette.OnEmber),
        ) {
            Text(stringResource(R.string.going_to_bed), style = MaterialTheme.typography.headlineSmall)
    }

    Spacer(Modifier.height(14.dp))
    val last = state.lastNight
    if (last != null) {
        LastNightRow(last, state.zone, time) { actions.onOpenNight(last.nightId) }
    } else {
        CentredText(stringResource(R.string.first_night_hint), MaterialTheme.typography.bodyMedium, contentWidth, Palette.Muted)
    }
    }
}

/** Every 5 minutes round the clock, for the wake-by wheel. */
private val wakeByValues: List<LocalTime> = (0 until 24 * 12).map { LocalTime.MIDNIGHT.plusMinutes(it * 5L) }

private fun greetingFor(t: LocalTime) = when (t.hour) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_afternoon
    in 18..21 -> R.string.greeting_evening
    else -> R.string.greeting_night
}

@Composable
private fun Chip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Palette.Raised, contentColor = Palette.Text),
    ) { Text(text, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun WindowLengthDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.window_title)) },
        text = {
            Column {
                listOf(15, 20, 30, 45, 60).forEach { m ->
                    TextButton(onClick = { onPick(m) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.window_option, m) + if (m == current) " ✓" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = Palette.Card,
    )
}

@Composable
private fun LastNightRow(last: LastNightUi, zone: ZoneId, time: DateTimeFormatter, onClick: () -> Unit) {
    Row(
        contentWidth
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScoreDial(last.score, size = 44.dp, textStyle = MaterialTheme.typography.labelLarge)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.last_night), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    R.string.last_night_summary,
                    time.format(last.bedTapAt.atZone(zone)),
                    time.format(last.wakeTapAt.atZone(zone)),
                    formatDuration(last.timeInBed),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = Palette.Muted)
    }
}

/** A crescent moon. */
@Composable
private fun Moon(modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(Color(0xFFE9E2CC).copy(alpha = 0.18f), radius = r * 1.25f)
        val moon = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(r, r), r)) }
        val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(r * 1.6f, r * 0.75f), r)) }
        drawPath(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, moon, bite), Color(0xFFE9E2CC))
    }
}

@Composable
private fun SetupWarnings(alarmOn: Boolean, device: DeviceState, actions: HomeActions) {
    val warnings = buildList<@Composable () -> Unit> {
        if (!device.batteryUnrestricted) add { BatteryCard(device.isSamsung, actions) }
        if (alarmOn) {
            if (!device.exactAlarmsAllowed) add { WarningPanel(stringResource(R.string.alarm_exact_denied), stringResource(R.string.allow), actions.onAllowExactAlarms) }
            if (device.alarmVolumeOff) add { WarningPanel(stringResource(R.string.alarm_volume_off)) }
            if (!device.notificationsGranted) {
                add { WarningPanel(stringResource(R.string.alarm_notifications_off)) }
            } else if (!device.fullScreenAllowed) {
                add { WarningPanel(stringResource(R.string.alarm_full_screen_denied), stringResource(R.string.allow), actions.onAllowFullScreen) }
            }
        }
    }
    warnings.forEach {
        Spacer(Modifier.height(12.dp))
        it()
    }
}

@Composable
private fun WarningPanel(text: String, button: String? = null, onClick: () -> Unit = {}) {
    Panel(contentWidth, warning = true, horizontalAlignment = Alignment.CenterHorizontally) {
        CentredText(text, MaterialTheme.typography.bodyMedium)
        if (button != null) Button(onClick = onClick) { Text(button) }
    }
}

@Composable
private fun BatteryCard(isSamsung: Boolean, actions: HomeActions) {
    Panel(contentWidth, horizontalAlignment = Alignment.CenterHorizontally) {
        CentredText(stringResource(R.string.battery_title), MaterialTheme.typography.titleMedium)
        CentredText(stringResource(R.string.battery_body), MaterialTheme.typography.bodyMedium, colour = Palette.TextSoft)
        Button(onClick = actions.onAllowBattery) { Text(stringResource(R.string.battery_allow)) }
        if (isSamsung) {
            CentredText(stringResource(R.string.battery_samsung), MaterialTheme.typography.bodyMedium, colour = Palette.TextSoft)
            OutlinedButton(onClick = actions.onOpenAppSettings) { Text(stringResource(R.string.open_app_settings)) }
        }
    }
}

// ---------------------------------------------------------------- Tonight (in bed)

@Composable
private fun TonightContent(state: HomeUiState, device: DeviceState, actions: HomeActions, animate: Boolean) {
    val time = rememberTimeFormatter()
    val open = state.openNight ?: return
    val tracking = state.tracking
    val listening = device.micGranted && tracking.state == State.RECORDING

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Palette.CardDim)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (listening) Palette.Good else Palette.Poor))
            Text(
                stringResource(if (listening) R.string.listening_pill else R.string.not_listening_pill),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSoft,
            )
        }

        Spacer(Modifier.height(48.dp))
        Text(time.format(Instant.now().atZone(state.zone)), style = MaterialTheme.typography.displayLarge, color = Color(0xFFDCE2F2))
        CentredText(
            stringResource(R.string.in_bed_since_for, time.format(open.bedTapAt.atZone(state.zone)), formatDuration(open.inBedFor)),
            MaterialTheme.typography.bodyLarge,
            contentWidth,
            Palette.Muted,
        )

        Spacer(Modifier.height(20.dp))
        TonightAlarm(state.alarm, state.zone, time, actions)

        Spacer(Modifier.height(28.dp))
        SoundPanel(state, device, actions, time)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        tracking.live?.takeIf { listening }?.let { live ->
            // The wave follows the 100 ms display level; the text the once-a-second stored level.
            LevelWave(tracking.displayDbRelative ?: live.dbRelative, Modifier.fillMaxWidth().height(60.dp), animate)
            Text(
                stringResource(R.string.sound_level_now, Math.round(live.dbRelative).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            if (live.micSilent) {
                Spacer(Modifier.height(6.dp))
                CentredText(stringResource(R.string.sound_mic_silent), MaterialTheme.typography.bodySmall, contentWidth, Palette.Poor)
            }
            Spacer(Modifier.height(16.dp))
        }
        HoldButton(stringResource(R.string.im_up), onHeld = actions.onImUp, modifier = contentWidth.testTag("imUp"))
        Spacer(Modifier.height(8.dp))
        CentredText(stringResource(R.string.hold_hint), MaterialTheme.typography.bodySmall, contentWidth, Color(0xFF8790B0))
    }
}

@Composable
private fun TonightAlarm(alarm: AlarmUi, zone: ZoneId, time: DateTimeFormatter, actions: HomeActions) {
    when (val s = alarm.state) {
        is AlarmState.Ringing, is AlarmState.Snoozed -> Panel(contentWidth, colour = Palette.CardDim, horizontalAlignment = Alignment.CenterHorizontally) {
            val title = if (s is AlarmState.Snoozed) {
                stringResource(R.string.alarm_snoozed_title, time.format(s.until.atZone(zone)))
            } else {
                stringResource(R.string.alarm_good_morning)
            }
            CentredText(title, MaterialTheme.typography.titleMedium)
            Button(
                onClick = actions.onStopAlarm,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Ember, contentColor = Palette.OnEmber),
            ) { Text(stringResource(R.string.alarm_stop)) }
            if (s is AlarmState.Ringing && s.nextSnoozeMinutes != null) {
                OutlinedButton(onClick = actions.onSnoozeAlarm) { Text(stringResource(R.string.alarm_snooze_action)) }
            }
        }
        AlarmState.Idle -> {
            val label = when {
                alarm.firedAt != null -> stringResource(R.string.alarm_rang_at, time.format(alarm.firedAt.atZone(zone)))
                alarm.tonightStart != null && alarm.tonightEnd != null ->
                    stringResource(R.string.alarm_tonight, time.format(alarm.tonightStart.atZone(zone)), time.format(alarm.tonightEnd.atZone(zone)))
                else -> stringResource(R.string.alarm_none_tonight)
            }
            val armed = alarm.tonightEnd != null && alarm.firedAt == null
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = alarm.firedAt == null) { actions.onAlarmToggled(!armed) }
                    .semantics { role = Role.Switch; stateDescription = if (armed) "On" else "Off" }
                    .testTag("alarmSwitch")
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AlarmGlyph(if (armed) Palette.Ember else Palette.Faint)
                Text(label, style = MaterialTheme.typography.titleMedium, color = Color(0xFFDCE2F2))
            }
        }
    }
}

@Composable
private fun AlarmGlyph(colour: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = 1.8.dp.toPx()
        drawCircle(colour, radius = size.minDimension * 0.34f, center = Offset(size.width / 2, size.height * 0.55f), style = Stroke(w))
        drawLine(colour, Offset(size.width * 0.2f, size.height * 0.16f), Offset(size.width * 0.08f, size.height * 0.28f), w)
        drawLine(colour, Offset(size.width * 0.8f, size.height * 0.16f), Offset(size.width * 0.92f, size.height * 0.28f), w)
    }
}

@Composable
private fun SoundPanel(state: HomeUiState, device: DeviceState, actions: HomeActions, time: DateTimeFormatter) {
    val tracking = state.tracking
    val problem = when {
        !device.micGranted -> Problem.MIC_PERMISSION
        tracking.state == State.STOPPED_WITH_PROBLEM -> tracking.problem
        else -> null
    }
    when (problem) {
        Problem.MIC_PERMISSION -> Panel(contentWidth, warning = true, horizontalAlignment = Alignment.CenterHorizontally) {
            CentredText(stringResource(R.string.sound_off_title), MaterialTheme.typography.titleMedium)
            CentredText(stringResource(R.string.sound_off_mic_permission), MaterialTheme.typography.bodyMedium)
            Button(onClick = actions.onAllowMicrophone) { Text(stringResource(R.string.allow_microphone)) }
        }
        Problem.MIC_UNAVAILABLE -> Panel(contentWidth, warning = true, horizontalAlignment = Alignment.CenterHorizontally) {
            CentredText(stringResource(R.string.sound_stopped_title), MaterialTheme.typography.titleMedium)
            CentredText(stringResource(R.string.sound_stopped_mic_unavailable), MaterialTheme.typography.bodyMedium)
            Button(onClick = actions.onRestartTracking) { Text(stringResource(R.string.try_again)) }
        }
        Problem.SERVICE_NOT_ALLOWED -> Panel(contentWidth, warning = true, horizontalAlignment = Alignment.CenterHorizontally) {
            CentredText(stringResource(R.string.sound_stopped_title), MaterialTheme.typography.titleMedium)
            CentredText(stringResource(R.string.sound_stopped_killed), MaterialTheme.typography.bodyMedium)
            Button(onClick = actions.onRestartTracking) { Text(stringResource(R.string.resume)) }
        }
        null -> NoisePanel(state, time)
    }
    if (!device.notificationsGranted && state.alarm.tonightEnd == null) {
        Spacer(Modifier.height(10.dp))
        CentredText(stringResource(R.string.notifications_off), MaterialTheme.typography.bodySmall, contentWidth, Palette.Muted)
    }
}

/** Shows at most this many events; older ones are summarised. */
private const val MAX_EVENTS_LISTED = 5

@Composable
private fun NoisePanel(state: HomeUiState, time: DateTimeFormatter) {
    val events = state.noiseEvents
    val open = state.openNight ?: return
    Panel(contentWidth, colour = Palette.CardDim) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.noise_events_title, events.size), style = MaterialTheme.typography.titleMedium, color = Color(0xFFDCE2F2))
            events.firstOrNull()?.let {
                Text(
                    stringResource(R.string.noise_latest, time.format(it.start.atZone(state.zone)), it.peakDbRelative),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
            }
        }
        // A strip from the bed tap to now, with a tick for each event.
        val total = open.inBedFor.toMillis().coerceAtLeast(1)
        Canvas(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF121935))) {
            events.forEach { e ->
                val x = (Duration.between(open.bedTapAt, e.start).toMillis().toFloat() / total).coerceIn(0f, 1f) * size.width
                drawLine(Palette.Moon, Offset(x, size.height * 0.2f), Offset(x, size.height * 0.8f), 3.dp.toPx())
            }
        }
        if (events.isEmpty()) {
            Text(stringResource(R.string.noise_events_none), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        events.take(MAX_EVENTS_LISTED).forEach { event ->
            Text(
                stringResource(R.string.noise_event_line, time.format(event.start.atZone(state.zone)), event.duration.toSeconds(), event.peakDbRelative),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSoft,
            )
        }
        if (events.size > MAX_EVENTS_LISTED) {
            Text(stringResource(R.string.noise_events_more, events.size - MAX_EVENTS_LISTED), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}

/**
 * A gentle wave whose height follows the live level (bigger above the room's baseline). The level
 * arrives ten times a second; the height eases towards it and the wave keeps flowing, so it moves
 * smoothly instead of jumping with each reading.
 */
@Composable
private fun LevelWave(dbRelative: Double, modifier: Modifier, animate: Boolean = true) {
    val target = (dbRelative.coerceIn(0.0, 30.0) / 30.0).toFloat()
    val amplitude by animateFloatAsState(target, animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing), label = "level")
    val phase = if (animate) {
        rememberInfiniteTransition(label = "flow").animateFloat(
            initialValue = 0f,
            targetValue = 2 * PI.toFloat(),
            animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing)),
            label = "phase",
        ).value
    } else {
        0f
    }
    Canvas(modifier) {
        listOf(Color(0xFF3E5BB8) to 1f, Color(0xFF1F2E66) to 0.5f).forEach { (colour, scale) ->
            val path = Path()
            val mid = size.height * 0.6f
            for (i in 0..90) {
                val x = size.width * i / 90f
                val y = mid - (6.dp.toPx() + amplitude * size.height * 0.4f) * scale *
                    sin(i / 90f * 4 * PI.toFloat() + scale * 2 - phase * scale.coerceAtLeast(0.7f)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, colour, style = Stroke(2.dp.toPx()))
        }
    }
}

// ---------------------------------------------------------------- Shared

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
                Text(stringResource(R.string.overdue_body, dayAndTime.format(prompt.bedTapAt.atZone(zone)), formatDuration(prompt.openFor)))
            },
            confirmButton = { TextButton(onClick = { picking = true }) { Text(stringResource(R.string.overdue_set_wake_time)) } },
            dismissButton = { TextButton(onClick = onKeepOpen) { Text(stringResource(R.string.overdue_keep_open)) } },
            containerColor = Palette.Card,
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
            text = { StandardPickerType { TimePicker(state = pickerState) } },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    onCloseAt(LocalTime.of(pickerState.hour, pickerState.minute))
                }) { Text(stringResource(R.string.close_night)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = Palette.Card,
        )
    }
}

@Composable
fun rememberTimeFormatter(): DateTimeFormatter {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(is24Hour) { DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a") }
}

@Composable
fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    return if (hours > 0 && minutes == 0) {
        stringResource(R.string.duration_hours, hours)
    } else if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}
