package io.github.murrayb52.sleeptight.alarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.murrayb52.sleeptight.MainActivity
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.SleepTightApplication
import io.github.murrayb52.sleeptight.data.Night
import io.github.murrayb52.sleeptight.metrics.AlarmConfig
import io.github.murrayb52.sleeptight.ui.components.HoldButton
import io.github.murrayb52.sleeptight.ui.components.ScoreDial
import io.github.murrayb52.sleeptight.ui.components.SunriseSky
import io.github.murrayb52.sleeptight.ui.home.formatDuration
import io.github.murrayb52.sleeptight.ui.home.rememberTimeFormatter
import io.github.murrayb52.sleeptight.ui.theme.Palette
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The sun rises very slowly: it reaches dawn ([RINGING_SKY_MAX]) over the longest an alarm rings. */
private val SUNRISE: Duration = AlarmRingService.MAX_RING

/** While ringing the sky stops at dawn; full day only comes with I'm up. */
private const val RINGING_SKY_MAX = 0.7f

/** After I'm up, the sky reaches full day this quickly. */
private const val TO_DAY_MILLIS = 1_200

/**
 * The alarm, shown over the lock screen: a night sky with a sun rising slowly while it rings. Swipe
 * up anywhere to snooze. I'm up (press and hold) silences the alarm and ends the night in one go
 * (2026-11-05; it was a separate Stop and I'm up before), sweeps the sky to full day and shows the
 * finished night.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val container = (application as SleepTightApplication).container
        setContent {
            val state by container.alarmState.collectAsStateWithLifecycle()
            var stoppedNightId by rememberSaveable { mutableStateOf<Long?>(null) }
            val ringing = state as? AlarmState.Ringing

            // Close when the alarm ends some other way (snoozed, stopped from the notification).
            LaunchedEffect(state, stoppedNightId) {
                if (stoppedNightId == null && state !is AlarmState.Ringing) finish()
            }

            val nightFlow = remember(stoppedNightId) { stoppedNightId?.let { container.nightRepository.night(it) } ?: flowOf(null) }
            val night by nightFlow.collectAsStateWithLifecycle(null)
            val eventsFlow = remember(stoppedNightId) { stoppedNightId?.let { container.nightRepository.noiseEvents(it) } ?: flowOf(emptyList()) }
            val events by eventsFlow.collectAsStateWithLifecycle(emptyList())

            AlarmExperience(
                ringingSince = ringing?.since,
                nextSnoozeMinutes = ringing?.nextSnoozeMinutes,
                snoozesUsed = ringing?.snoozesUsed ?: 0,
                stopped = stoppedNightId != null,
                morning = night?.let { morningOf(it, events.size) },
                onSnooze = { startService(AlarmRingService.intent(this, AlarmRingService.ACTION_SNOOZE)) },
                onImUp = {
                    stoppedNightId = ringing?.nightId
                    startService(AlarmRingService.intent(this, AlarmRingService.ACTION_IM_UP))
                },
                onSeeNight = { id ->
                    startActivity(MainActivity.openNight(this, id))
                    finish()
                },
                // Into the app either way, where "How did you sleep?" is waiting.
                onDone = {
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                },
            )
        }
    }
}

/** Last night, for the morning summary. */
data class MorningUi(
    val nightId: Long,
    val bedTapAt: Instant,
    val wakeTapAt: Instant?,
    val zone: ZoneId,
    val noiseEvents: Int,
    val snoozes: Int,
    val score: Int? = null,
    /** True once the closed night has been scored (even if there was no data to score). */
    val scored: Boolean = false,
)

fun morningOf(night: Night, noiseEvents: Int) = MorningUi(
    night.id,
    Instant.ofEpochMilli(night.bedTapAt),
    night.wakeTapAt?.let(Instant::ofEpochMilli),
    ZoneId.of(night.zoneId),
    noiseEvents,
    night.snoozeCount,
    io.github.murrayb52.sleeptight.scoring.ScoreCache.decode(night.scoreJson)?.total,
    io.github.murrayb52.sleeptight.scoring.ScoreCache.decode(night.scoreJson) != null,
)

@Composable
fun AlarmExperience(
    ringingSince: Instant?,
    nextSnoozeMinutes: Int?,
    snoozesUsed: Int,
    stopped: Boolean,
    morning: MorningUi?,
    onSnooze: () -> Unit,
    onImUp: () -> Unit,
    onSeeNight: (Long) -> Unit,
    onDone: () -> Unit,
    animate: Boolean = true,
) {
    val sky = remember { Animatable(0f) }
    var showMorning by remember { mutableStateOf(false) }

    // While ringing: the sun rises slowly from the moment the alarm went off.
    LaunchedEffect(ringingSince, stopped) {
        if (stopped || ringingSince == null) return@LaunchedEffect
        while (true) {
            val elapsed = Duration.between(ringingSince, Instant.now()).toMillis().toFloat()
            val target = (elapsed / SUNRISE.toMillis()).coerceIn(0f, 1f) * RINGING_SKY_MAX
            if (animate) sky.animateTo(target, tween(1_000, easing = LinearEasing)) else sky.snapTo(target)
            if (!animate) break
        }
    }
    // After I'm up: quickly but smoothly to full day, then the summary.
    LaunchedEffect(stopped) {
        if (!stopped) return@LaunchedEffect
        if (animate) sky.animateTo(1f, tween(TO_DAY_MILLIS, easing = FastOutSlowInEasing)) else sky.snapTo(1f)
        showMorning = true
    }

    Box(Modifier.fillMaxSize()) {
        SunriseSky(sky.value, Modifier.fillMaxSize(), twinkle = animate)
        AnimatedVisibility(visible = !showMorning, exit = fadeOut(tween(300))) {
            SleepTightTheme {
                RingingContent(nextSnoozeMinutes, snoozesUsed, stopped, onSnooze, onImUp)
            }
        }
        AnimatedVisibility(visible = showMorning, enter = fadeIn(tween(500))) {
            SleepTightTheme(day = true) {
                MorningSummary(morning, onSeeNight, onDone)
            }
        }
    }
}

@Composable
private fun RingingContent(nextSnoozeMinutes: Int?, snoozesUsed: Int, stopped: Boolean, onSnooze: () -> Unit, onImUp: () -> Unit) {
    val time = rememberTimeFormatter()
    val density = LocalDensity.current
    val threshold = with(density) { 140.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val canSnooze = nextSnoozeMinutes != null && !stopped
    val snoozeLabel = stringResource(R.string.alarm_snooze_action)
    val now by produceState(Instant.now().atZone(ZoneId.systemDefault())) {
        while (true) {
            delay(1_000)
            value = Instant.now().atZone(ZoneId.systemDefault())
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .testTag("alarmScreen")
            .semantics {
                if (canSnooze) customActions = listOf(CustomAccessibilityAction(snoozeLabel) { onSnooze(); true })
            }
            .pointerInput(canSnooze) {
                if (!canSnooze) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag < -threshold) onSnooze()
                        val from = drag
                        scope.launch {
                            Animatable(from).animateTo(0f, tween(250)) { drag = value }
                        }
                    },
                    onDragCancel = { drag = 0f },
                    onVerticalDrag = { _, dy -> drag = (drag + dy).coerceAtMost(0f) },
                )
            }
            .graphicsLayer { translationY = drag * 0.5f }
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(DateTimeFormatter.ofPattern("EEEE d MMMM").format(now).uppercase(), style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        Text(time.format(now), style = MaterialTheme.typography.displayLarge, color = Palette.Text)
        Text(stringResource(R.string.alarm_good_morning), style = MaterialTheme.typography.headlineSmall, color = Palette.Text)

        Spacer(Modifier.weight(1f))
        // Held, like I'm up on Tonight, so a half-asleep swipe or bump can't end the night.
        HoldButton(
            stringResource(R.string.im_up),
            holdingText = stringResource(R.string.keep_holding),
            onHeld = { if (!stopped) onImUp() },
            modifier = Modifier.widthIn(max = 480.dp).heightIn(min = 68.dp).testTag("alarmImUp"),
        )

        Spacer(Modifier.height(28.dp))
        if (canSnooze) {
            SnoozeMeter(snoozesUsed)
            Spacer(Modifier.height(10.dp))
            Text("⌃", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
            Text(stringResource(R.string.swipe_to_snooze), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        } else if (snoozesUsed > 0) {
            Text(
                stringResource(R.string.alarm_no_snoozes_left),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Text,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One mark per snooze; used ones in amber. No durations: the ladder is baked in. */
@Composable
private fun SnoozeMeter(used: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(AlarmConfig().maxSnoozes) { i ->
            Box(
                Modifier
                    .size(width = 28.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i < used) Palette.Ember else Color.White.copy(alpha = 0.35f)),
            )
        }
    }
}

@Composable
private fun MorningSummary(morning: MorningUi?, onSeeNight: (Long) -> Unit, onDone: () -> Unit) {
    val time = rememberTimeFormatter()
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.alarm_good_morning), style = MaterialTheme.typography.displaySmall, color = Palette.DayText)
        Text(
            stringResource(R.string.morning_last_night),
            style = MaterialTheme.typography.titleMedium,
            color = Palette.DayMuted,
        )
        Spacer(Modifier.height(28.dp))
        if (morning != null) {
            ScoreDial(
                morning.score,
                size = 120.dp,
                stroke = 8.dp,
                textStyle = MaterialTheme.typography.displaySmall.copy(color = Palette.DayText),
                track = Palette.DayText.copy(alpha = 0.12f),
            )
            // The night closes a moment after I'm up, and scoring takes another.
            val note = when {
                morning.wakeTapAt == null || !morning.scored -> R.string.score_calculating
                morning.score == null -> R.string.score_no_data
                else -> null
            }
            if (note != null) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = Palette.DayMuted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            val inBed = Duration.between(morning.bedTapAt, morning.wakeTapAt ?: Instant.now())
            Row(Modifier.fillMaxWidth().widthIn(max = 480.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DayTile(formatDuration(inBed), stringResource(R.string.tile_in_bed), Modifier.weight(1f))
                DayTile(morning.noiseEvents.toString(), stringResource(R.string.tile_noise), Modifier.weight(1f))
                DayTile(morning.snoozes.toString(), stringResource(R.string.tile_snoozes), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(
                    R.string.night_times,
                    time.format(morning.bedTapAt.atZone(morning.zone)),
                    morning.wakeTapAt?.let { time.format(it.atZone(morning.zone)) } ?: stringResource(R.string.now),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.DayMuted,
            )
        }
        Spacer(Modifier.weight(1f))
        if (morning != null) {
            Button(
                onClick = { onSeeNight(morning.nightId) },
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).heightIn(min = 60.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Ember, contentColor = Palette.OnEmber),
            ) {
                Text(stringResource(R.string.see_the_night), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).heightIn(min = 56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.DayText),
        ) { Text(stringResource(R.string.done), style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun DayTile(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Palette.DayCard.copy(alpha = 0.8f)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Palette.DayText)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.DayMuted)
    }
}
