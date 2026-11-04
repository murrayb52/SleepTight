package io.github.murrayb52.sleeptight.metrics

import java.time.Duration
import java.time.LocalTime

/**
 * Every score weight and threshold, in one place so they're easy to tune.
 * Weights are the maximum points per component and add up to 100.
 */
data class ScoreConfig(
    // Component weights
    val durationWeight: Double = 35.0,
    val efficiencyWeight: Double = 25.0,
    val deepRemWeight: Double = 20.0,
    val interruptionsWeight: Double = 10.0,
    val regularityWeight: Double = 10.0,

    // Efficiency: 0 points at or below zero, full at or above full
    val efficiencyZeroAt: Double = 0.70,
    val efficiencyFullAt: Double = 0.90,

    // Deep + REM share of total sleep
    val deepRemZeroAt: Double = 0.20,
    val deepRemFullAt: Double = 0.40,

    // Interruptions: points lost (out of interruptionsWeight) per wake-up and per noise cluster
    val pointsPerWakeUp: Double = 2.0,
    val pointsPerNoiseCluster: Double = 1.0,
    /** An awake spell after sleep onset counts as a wake-up only if it lasts at least this long. */
    val minWakeUp: Duration = Duration.ofMinutes(2),
    /** Noise events starting within this long of the previous event's end belong to the same cluster. */
    val noiseClusterGap: Duration = Duration.ofMinutes(5),

    // Regularity factors (each 0..1)
    /** Bedtime factor falls from 1 at the goal to 0 this long after it. */
    val bedtimeZeroAfter: Duration = Duration.ofMinutes(60),
    /** Duration factor falls from 1 at the sleep goal to 0 this far below it. */
    val durationZeroBelow: Duration = Duration.ofHours(2),
    /** Snooze factor lost per snooze over the goal: 1 → 0.5 → 0. */
    val snoozeFactorStep: Double = 0.5,
    /** Bedtime spread (standard deviation) at or below which consistency is full. */
    val consistencyFullAt: Duration = Duration.ofMinutes(15),
    /** Bedtime spread at or above which consistency is 0. */
    val consistencyZeroAt: Duration = Duration.ofMinutes(60),
    /** Consistency looks at nights in this many days, ending with the night being scored. */
    val consistencyWindowNights: Int = 7,
    /** Consistency isn't worked out from fewer nights than this. */
    val consistencyMinNights: Int = 3,
) {
    val totalWeight: Double get() = durationWeight + efficiencyWeight + deepRemWeight + interruptionsWeight + regularityWeight
}

/** The user's personal goals (from Settings). They feed the duration component and regularity. */
data class Goals(
    val bedtime: LocalTime = LocalTime.of(22, 0),
    val sleep: Duration = Duration.ofHours(8),
    val maxSnoozes: Int = 1,
)

/**
 * Linear ramp: 0 at [zeroAt], 1 at [fullAt], clamped to 0..1 outside. Works in either direction
 * ([zeroAt] may be above [fullAt] for "lower is better" values).
 */
internal fun ramp(x: Double, zeroAt: Double, fullAt: Double): Double =
    ((x - zeroAt) / (fullAt - zeroAt)).coerceIn(0.0, 1.0)
