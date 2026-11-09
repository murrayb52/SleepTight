package io.github.murrayb52.sleeptight.metrics

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

/** Every alarm threshold, in one place. */
data class AlarmConfig(
    /** Activity is compared over this many of the most recent complete minutes. */
    val recentMinutes: Int = 2,
    /** ...against this percentile of the night's per-minute activity. */
    val percentile: Double = 0.75,
    /**
     * And it must also average at least this many active seconds a minute, so a single cough on a
     * dead-quiet night (75th percentile 0) doesn't count as waking up.
     */
    val minActiveSeconds: Double = 3.0,
    /** No early trigger until the night has this many minutes of activity data. */
    val minHistoryMinutes: Int = 30,
    /** Volume ramp: starts at this fraction (barely audible)... */
    val rampStartVolume: Float = 0.02f,
    /** ...and reaches full volume after this long, rising evenly in perceived loudness. */
    val rampDuration: Duration = Duration.ofMinutes(3),
    /** The first snooze. Every curve starts here. */
    val firstSnoozeMinutes: Int = 9,
    /** The last snooze on a decreasing curve (linear or exponential). */
    val lastSnoozeMinutes: Int = 2,
    /** Snoozes allowed by default before the alarm can only be stopped (a setting since 2026-11-09). */
    val defaultSnoozes: Int = 5,
    /** The most snoozes the setting allows. */
    val maxSnoozesSetting: Int = 10,
    /** The bedtime reminder comes this long before the bedtime goal. */
    val bedtimeReminderLead: Duration = Duration.ofMinutes(30),
)

data class WakeWindow(val start: Instant, val end: Instant) {
    companion object {
        /**
         * The first wake window after [bedTapAt]: [start] is the next time that local clock time
         * comes round, and [end] the next time its clock time comes round after that.
         */
        fun after(bedTapAt: Instant, zone: ZoneId, start: LocalTime, end: LocalTime): WakeWindow {
            val s = nextOccurrence(start, after = bedTapAt, zone)
            val e = nextOccurrence(end, after = s, zone)
            return WakeWindow(s, e)
        }

        private fun nextOccurrence(time: LocalTime, after: Instant, zone: ZoneId): Instant {
            val from = after.atZone(zone)
            val sameDay = from.toLocalDate().atTime(time).atZone(zone)
            return (if (sameDay.isAfter(from)) sameDay else from.toLocalDate().plusDays(1).atTime(time).atZone(zone)).toInstant()
        }
    }
}

object SmartTrigger {
    /**
     * Whether to sound the alarm early at [now]: the mean activity of the last
     * [AlarmConfig.recentMinutes] complete minutes is above the night's
     * [AlarmConfig.percentile] and at least [AlarmConfig.minActiveSeconds].
     *
     * @param activity the night's per-minute activity so far (see [activityPerMinute]).
     */
    fun shouldWakeNow(activity: List<MinuteActivity>, now: Instant, config: AlarmConfig = AlarmConfig()): Boolean {
        if (activity.size < config.minHistoryMinutes) return false
        val currentMinute = Instant.ofEpochMilli(now.toEpochMilli() / 60_000 * 60_000)
        val wanted = (1..config.recentMinutes).map { currentMinute.minus(Duration.ofMinutes(it.toLong())) }.toSet()
        val recent = activity.filter { it.minuteStart in wanted }
        if (recent.size < config.recentMinutes) return false

        val sorted = activity.map { it.activeSeconds }.sorted()
        val rank = ceil(config.percentile * sorted.size).toInt().coerceIn(1, sorted.size)
        val threshold = sorted[rank - 1]
        val mean = recent.map { it.activeSeconds }.average()
        return mean > threshold && mean >= config.minActiveSeconds
    }
}

object VolumeRamp {
    /**
     * Alarm volume (0..1) after ringing for [elapsed]. Exponential in amplitude (a constant number
     * of dB per second), because hearing is logarithmic: a linear ramp is nearly full volume
     * within the first third of its length.
     */
    fun at(elapsed: Duration, config: AlarmConfig = AlarmConfig()): Float {
        val progress = (elapsed.toMillis().toFloat() / config.rampDuration.toMillis()).coerceIn(0f, 1f)
        return config.rampStartVolume * (1f / config.rampStartVolume).pow(progress)
    }
}

/** How snooze lengths change from one snooze to the next. */
enum class SnoozeCurve {
    /** Every snooze is the first length. */
    CONSTANT,

    /** Equal steps from the first length down to the last. */
    LINEAR,

    /** The same fraction shorter each time, from the first length down to the last: big drops first. */
    EXPONENTIAL,
}

/**
 * The night's snoozes: [allowed] of them (a setting), with lengths from [AlarmConfig.firstSnoozeMinutes]
 * following the chosen [SnoozeCurve]. Once they're used up the alarm can only be stopped.
 */
object SnoozeLadder {
    /** Every snooze length in minutes, in order. Whole minutes, rounded. */
    fun lengths(allowed: Int, curve: SnoozeCurve, config: AlarmConfig = AlarmConfig()): List<Int> {
        val n = allowed.coerceIn(0, config.maxSnoozesSetting)
        val first = config.firstSnoozeMinutes.toDouble()
        val last = config.lastSnoozeMinutes.toDouble()
        return List(n) { i ->
            val t = if (n == 1) 0.0 else i.toDouble() / (n - 1)
            val minutes = when (curve) {
                SnoozeCurve.CONSTANT -> first
                SnoozeCurve.LINEAR -> first + (last - first) * t
                SnoozeCurve.EXPONENTIAL -> first * (last / first).pow(t)
            }
            minutes.roundToInt()
        }
    }

    /** Length of the next snooze, or null if no snoozes are left. */
    fun minutesFor(snoozesSoFar: Int, allowed: Int, curve: SnoozeCurve, config: AlarmConfig = AlarmConfig()): Int? =
        lengths(allowed, curve, config).getOrNull(snoozesSoFar)

    fun remaining(snoozesSoFar: Int, allowed: Int): Int = (allowed - snoozesSoFar).coerceAtLeast(0)
}

object BedtimeReminder {
    /** The next reminder strictly after [now]: [AlarmConfig.bedtimeReminderLead] before the [bedtime] goal. */
    fun next(now: Instant, zone: ZoneId, bedtime: LocalTime, config: AlarmConfig = AlarmConfig()): Instant {
        val time = bedtime.minus(config.bedtimeReminderLead)
        val local = now.atZone(zone)
        val today = local.toLocalDate().atTime(time).atZone(zone)
        return (if (today.isAfter(local)) today else local.toLocalDate().plusDays(1).atTime(time).atZone(zone)).toInstant()
    }
}
