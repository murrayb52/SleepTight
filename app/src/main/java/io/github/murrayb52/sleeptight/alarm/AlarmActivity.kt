package io.github.murrayb52.sleeptight.alarm

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.SleepTightApplication
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The full-screen alarm, shown over the lock screen. Closes itself once the alarm stops ringing. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val container = (application as SleepTightApplication).container
        val pattern = if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a"
        setContent {
            SleepTightTheme {
                val state by container.alarmState.collectAsStateWithLifecycle()
                val settings by container.nightRepository.settings.collectAsStateWithLifecycle(Settings())
                LaunchedEffect(state) {
                    if (state !is AlarmState.Ringing) finish()
                }
                AlarmScreen(
                    time = DateTimeFormatter.ofPattern(pattern).format(Instant.now().atZone(ZoneId.systemDefault())),
                    snoozeMinutes = settings.snoozeMinutes,
                    onSnooze = { startService(AlarmRingService.intent(this, AlarmRingService.ACTION_SNOOZE)) },
                    onDismiss = { startService(AlarmRingService.intent(this, AlarmRingService.ACTION_DISMISS)) },
                )
            }
        }
    }
}

@Composable
fun AlarmScreen(time: String, snoozeMinutes: Int, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val width = Modifier.fillMaxWidth().widthIn(max = 480.dp)
            Text(time, style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center, modifier = width)
            Text(
                stringResource(R.string.alarm_good_morning),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = width,
            )
            Spacer(Modifier.height(48.dp))
            Button(onClick = onDismiss, modifier = width.heightIn(min = 96.dp), shape = RoundedCornerShape(32.dp)) {
                Text(stringResource(R.string.alarm_dismiss), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onSnooze, modifier = width.heightIn(min = 72.dp), shape = RoundedCornerShape(32.dp)) {
                Text(
                    stringResource(R.string.alarm_snooze, snoozeMinutes),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.alarm_snooze_counted),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = width,
            )
        }
    }
}
