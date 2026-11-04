package io.github.murrayb52.sleeptight.metrics

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

val ZONE: ZoneId = ZoneId.of("Africa/Johannesburg")

fun at(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant()

fun minutes(m: Long): Duration = Duration.ofMinutes(m)

/**
 * Builds a synthetic night: stages are laid end to end from [bed], so
 * `night("2026-11-03T22:00") { awake(10); light(240) }` sleeps lightly from 22:10 to 02:10.
 * The wake tap defaults to the end of the last segment.
 */
class SyntheticNight(private val bed: Instant) {
    private var cursor = bed
    val segments = mutableListOf<SleepSegment>()
    val noise = mutableListOf<NoisePeriod>()

    private fun add(stage: Stage, mins: Long) {
        val end = cursor.plus(minutes(mins))
        segments += SleepSegment(cursor, end, stage)
        cursor = end
    }

    fun awake(mins: Long) = add(Stage.AWAKE, mins)
    fun light(mins: Long) = add(Stage.LIGHT, mins)
    fun deep(mins: Long) = add(Stage.DEEP, mins)
    fun rem(mins: Long) = add(Stage.REM, mins)
    fun unknown(mins: Long) = add(Stage.UNKNOWN, mins)

    /** No data for this long (a gap between segments). */
    fun gap(mins: Long) {
        cursor = cursor.plus(minutes(mins))
    }

    /** A noise event starting [afterBedMins] after the bed tap, lasting [seconds]. */
    fun noiseAt(afterBedMins: Long, seconds: Long = 5, peak: Double = 20.0) {
        val start = bed.plus(minutes(afterBedMins))
        noise += NoisePeriod(start, start.plusSeconds(seconds), peak)
    }

    fun build(source: SleepDataSource = SleepDataSource.GARMIN, wake: Instant = cursor) =
        NightInput(bedTapAt = bed, wakeTapAt = wake, segments = segments.toList(), source = source, noise = noise.toList())
}

fun night(
    bed: String = "2026-11-03T22:00",
    source: SleepDataSource = SleepDataSource.GARMIN,
    wake: String? = null,
    block: SyntheticNight.() -> Unit,
): NightInput {
    val builder = SyntheticNight(at(bed)).apply(block)
    return if (wake == null) builder.build(source) else builder.build(source, at(wake))
}

/** A textbook night: 10 min to fall asleep, 470 min asleep, no wake-ups, 22:00–06:00. */
fun goodNight(source: SleepDataSource = SleepDataSource.GARMIN) = night(source = source) {
    awake(10)
    light(120); deep(60); rem(30)
    light(60); deep(50); rem(40)
    light(70); rem(40)
}
