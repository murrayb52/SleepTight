package io.github.murrayb52.sleeptight.metrics

import java.time.Duration

/**
 * Typical healthy-adult ranges shown next to each metric. They're for context only: the score
 * uses [ScoreConfig], not these.
 */
object ReferenceRanges {
    val totalSleepMin: Duration = Duration.ofHours(7)
    val totalSleepMax: Duration = Duration.ofHours(9)
    const val EFFICIENCY_MIN: Double = 0.85
    val timeToFallAsleepMax: Duration = Duration.ofMinutes(30)
    val deepShare: ClosedFloatingPointRange<Double> = 0.13..0.23
    val remShare: ClosedFloatingPointRange<Double> = 0.20..0.25
}
