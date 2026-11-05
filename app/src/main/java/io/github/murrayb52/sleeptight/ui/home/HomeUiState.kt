package io.github.murrayb52.sleeptight.ui.home

import io.github.murrayb52.sleeptight.alarm.AlarmState
import io.github.murrayb52.sleeptight.data.CaffeineEntry
import io.github.murrayb52.sleeptight.data.Night
import io.github.murrayb52.sleeptight.diary.toDose
import io.github.murrayb52.sleeptight.metrics.CAFFEINE_LOOKBACK
import io.github.murrayb52.sleeptight.metrics.caffeineAt
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.data.NoiseEvent
import io.github.murrayb52.sleeptight.scoring.ScoreCache
import io.github.murrayb52.sleeptight.tracking.TrackingStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** After this long, an open night is probably a forgotten "I'm up" tap, so Home asks about it. */
val OPEN_NIGHT_PROMPT_AFTER: Duration = Duration.ofHours(16)

/** A night shorter than this is an accidental tap, not a night: I'm up discards it. */
val SHORTEST_NIGHT: Duration = Duration.ofMinutes(5)

data class HomeUiState(
    val loading: Boolean = true,
    /** Zone used to show times: the device's current zone. */
    val zone: ZoneId = ZoneId.systemDefault(),
    val openNight: OpenNightUi? = null,
    val overduePrompt: OverduePrompt? = null,
    /** Overnight sound monitoring; only meaningful while a night is open. */
    val tracking: TrackingStatus = TrackingStatus(),
    /** Noise events so far in the open night, most recent first. */
    val noiseEvents: List<NoiseEventUi> = emptyList(),
    val alarm: AlarmUi = AlarmUi(),
    val goals: GoalsUi = GoalsUi(),
    /** Raw settings, for resolving a logged drink's caffeine. */
    val settings: Settings = Settings(),
    val caffeine: CaffeineUi = CaffeineUi(),
)

/** Caffeine for the Sleep tab: drinks in the last day and what's projected to be left at bedtime. */
data class CaffeineUi(val recent: List<CaffeineEntry> = emptyList(), val byBedtimeMg: Double = 0.0)

/**
 * The moment to project caffeine to: the next bedtime goal, or now if that was within the last
 * 6 hours (you're up late, so bedtime is now).
 */
fun caffeineTarget(now: Instant, zone: ZoneId, bedtime: LocalTime): Instant {
    val local = now.atZone(zone)
    var next = local.toLocalDate().atTime(bedtime).atZone(zone)
    if (next.isBefore(local)) next = next.plusDays(1)
    return if (Duration.between(local, next) > Duration.ofHours(18)) now else next.toInstant()
}

fun caffeineUi(entries: List<CaffeineEntry>, settings: Settings, now: Instant, zone: ZoneId): CaffeineUi {
    val bedtime = LocalTime.MIDNIGHT.plusMinutes(settings.targetBedtimeMinutes.toLong())
    val target = caffeineTarget(now, zone, bedtime)
    val recent = entries.filter { it.timestamp >= now.minus(CAFFEINE_LOOKBACK).toEpochMilli() && it.timestamp <= now.toEpochMilli() }
    return CaffeineUi(recent, caffeineAt(entries.map { it.toDose() }, target, settings.caffeineHalfLifeHours))
}

data class GoalsUi(
    val bedtime: LocalTime = LocalTime.of(22, 0),
    val reminderEnabled: Boolean = true,
    val bedsideCheckEnabled: Boolean = true,
    val earplugsDefault: Boolean = true,
)

fun goalsUi(settings: Settings) = GoalsUi(
    bedtime = LocalTime.MIDNIGHT.plusMinutes(settings.targetBedtimeMinutes.toLong()),
    reminderEnabled = settings.bedtimeReminderEnabled,
    bedsideCheckEnabled = settings.bedsideCheckEnabled,
    earplugsDefault = settings.earplugsDefault,
)

data class AlarmUi(
    val windowStart: LocalTime = LocalTime.of(5, 45),
    val windowEnd: LocalTime = LocalTime.of(6, 15),
    val windowMinutes: Int = 30,
    /** The open night's armed window, or null if no alarm is set for it. */
    val tonightStart: Instant? = null,
    val tonightEnd: Instant? = null,
    val firedAt: Instant? = null,
    val state: AlarmState = AlarmState.Idle,
)

fun alarmUi(settings: Settings, openNight: Night?, state: AlarmState) = AlarmUi(
    windowStart = LocalTime.MIDNIGHT.plusMinutes(settings.alarmWindowStartMinutes.toLong()),
    windowEnd = LocalTime.MIDNIGHT.plusMinutes(settings.alarmWindowEndMinutes.toLong()),
    windowMinutes = Math.floorMod(settings.alarmWindowEndMinutes - settings.alarmWindowStartMinutes, 24 * 60),
    tonightStart = openNight?.alarmWindowStart?.let(Instant::ofEpochMilli),
    tonightEnd = openNight?.alarmWindowEnd?.let(Instant::ofEpochMilli),
    firedAt = openNight?.alarmFiredAt?.let(Instant::ofEpochMilli),
    state = state,
)

data class NoiseEventUi(val start: Instant, val duration: Duration, val peakDbRelative: Int)

fun NoiseEvent.toUi() = NoiseEventUi(
    start = Instant.ofEpochMilli(start),
    duration = Duration.ofMillis(end - start),
    peakDbRelative = Math.round(peakDbRelative),
)

/** Device-side facts the screen needs, re-read whenever the app comes back to the foreground. */
data class DeviceState(
    val micGranted: Boolean = true,
    val notificationsGranted: Boolean = true,
    val batteryUnrestricted: Boolean = true,
    val isSamsung: Boolean = false,
    val alarmVolumeOff: Boolean = false,
    val exactAlarmsAllowed: Boolean = true,
    val fullScreenAllowed: Boolean = true,
)

data class OpenNightUi(val nightId: Long, val bedTapAt: Instant, val inBedFor: Duration)

/** Shown when the open night has been open for at least [OPEN_NIGHT_PROMPT_AFTER]. */
data class OverduePrompt(
    val nightId: Long,
    val bedTapAt: Instant,
    val openFor: Duration,
    /** Pre-filled wake time for the picker: bed tap plus the sleep goal. */
    val suggestedWakeTime: LocalTime,
)

fun buildHomeUiState(
    openNight: Night?,
    now: Instant,
    zone: ZoneId,
    targetSleep: Duration,
    promptDismissedForNightId: Long?,
): HomeUiState {
    val open = openNight?.let {
        val bed = Instant.ofEpochMilli(it.bedTapAt)
        OpenNightUi(it.id, bed, Duration.between(bed, now).coerceAtLeast(Duration.ZERO))
    }
    val prompt = open
        ?.takeIf { it.inBedFor >= OPEN_NIGHT_PROMPT_AFTER && it.nightId != promptDismissedForNightId }
        ?.let { OverduePrompt(it.nightId, it.bedTapAt, it.inBedFor, it.bedTapAt.plus(targetSleep).atZone(zone).toLocalTime()) }
    return HomeUiState(loading = false, zone = zone, openNight = open, overduePrompt = prompt)
}

/**
 * Turns a wake time picked on a clock face into an instant: the first time that clock time
 * occurs after [bedTapAt]. Returns null if that is still in the future.
 */
fun resolveWakeTime(bedTapAt: Instant, time: LocalTime, zone: ZoneId, now: Instant): Instant? {
    val bed = bedTapAt.atZone(zone)
    var candidate = bed.toLocalDate().atTime(time).atZone(zone)
    if (!candidate.isAfter(bed)) candidate = bed.toLocalDate().plusDays(1).atTime(time).atZone(zone)
    val instant = candidate.toInstant()
    return instant.takeUnless { it.isAfter(now) }
}
