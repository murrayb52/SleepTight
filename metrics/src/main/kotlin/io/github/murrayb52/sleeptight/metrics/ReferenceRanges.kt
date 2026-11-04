package io.github.murrayb52.sleeptight.metrics

import java.time.Duration

/** Where a measurement sits against its healthy reference range. */
enum class RangeVerdict { BELOW, WITHIN, ABOVE }

/**
 * Typical healthy-adult ranges shown next to each metric. They're for context only: the score
 * uses [ScoreConfig], not these. Bounds are inclusive.
 */
object ReferenceRanges {
    val totalSleepMin: Duration = Duration.ofHours(7)
    val totalSleepMax: Duration = Duration.ofHours(9)
    const val EFFICIENCY_MIN: Double = 0.85
    val timeToFallAsleepMax: Duration = Duration.ofMinutes(30)
    val deepShare: ClosedFloatingPointRange<Double> = 0.13..0.23
    val remShare: ClosedFloatingPointRange<Double> = 0.20..0.25

    fun totalSleep(d: Duration): RangeVerdict = when {
        d < totalSleepMin -> RangeVerdict.BELOW
        d > totalSleepMax -> RangeVerdict.ABOVE
        else -> RangeVerdict.WITHIN
    }

    fun efficiency(x: Double): RangeVerdict = if (x < EFFICIENCY_MIN) RangeVerdict.BELOW else RangeVerdict.WITHIN

    fun timeToFallAsleep(d: Duration): RangeVerdict = if (d > timeToFallAsleepMax) RangeVerdict.ABOVE else RangeVerdict.WITHIN

    fun deep(share: Double): RangeVerdict = band(share, deepShare)

    fun rem(share: Double): RangeVerdict = band(share, remShare)

    // Shares are compared at whole-percent precision, as they're shown.
    private fun band(x: Double, range: ClosedFloatingPointRange<Double>): RangeVerdict {
        val pct = Math.round(x * 100)
        return when {
            pct < Math.round(range.start * 100) -> RangeVerdict.BELOW
            pct > Math.round(range.endInclusive * 100) -> RangeVerdict.ABOVE
            else -> RangeVerdict.WITHIN
        }
    }
}
