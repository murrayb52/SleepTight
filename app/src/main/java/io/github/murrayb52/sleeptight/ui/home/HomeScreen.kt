package io.github.murrayb52.sleeptight.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeRoute(viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        message = message,
        onGoingToBed = viewModel::onGoingToBed,
        onImUp = viewModel::onImUp,
        onKeepNightOpen = viewModel::onKeepNightOpen,
        onCloseNightAt = viewModel::onCloseNightAt,
        onMessageShown = viewModel::onMessageShown,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    message: Int?,
    onGoingToBed: () -> Unit,
    onImUp: () -> Unit,
    onKeepNightOpen: (nightId: Long) -> Unit,
    onCloseNightAt: (LocalTime) -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
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
                HomeContent(state, onGoingToBed, onImUp)
            }
        }
    }

    state.overduePrompt?.let { prompt ->
        OverdueNightDialog(prompt, state.zone, onKeepOpen = { onKeepNightOpen(prompt.nightId) }, onCloseAt = onCloseNightAt)
    }
}

@Composable
private fun HomeContent(state: HomeUiState, onGoingToBed: () -> Unit, onImUp: () -> Unit) {
    val time = rememberTimeFormatter()
    val contentWidth = Modifier.fillMaxWidth().widthIn(max = 480.dp)

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
        onClick = if (open != null) onImUp else onGoingToBed,
        emphasiseMorning = open != null,
        modifier = contentWidth,
    )
    Spacer(Modifier.height(40.dp))

    val last = state.lastNight
    if (last != null) {
        LastNightCard(last, state.zone, time, contentWidth)
    } else if (open == null) {
        CentredText(stringResource(R.string.first_night_hint), MaterialTheme.typography.bodyMedium, contentWidth)
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
private fun LastNightCard(last: LastNightUi, zone: ZoneId, time: DateTimeFormatter, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
private fun CentredText(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
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
            message = null,
            onGoingToBed = {},
            onImUp = {},
            onKeepNightOpen = {},
            onCloseNightAt = {},
            onMessageShown = {},
        )
    }
}
