package io.github.murrayb52.sleeptight.metrics

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.sqrt

/** A past night's bed tap, with the zone it was recorded in. */
data class BedTap(val at: Instant, val zone: ZoneId)

/**
 * Goal adherence for one night. Each factor is 0..1. [duration] is null without sleep data, and
 * [consistency] is null with too few recent nights; [mean] averages whichever factors exist.
 */
data class Regularity(
    val bedtime: Double,
    val duration: Double?,
    val snoozes: Double,
    val consistency: Double?,
) {
    val mean: Double get() = listOfNotNull(bedtime, duration, snoozes, consistency).average()

    companion object {
        /**
         * @param recentBedTaps other nights' bed taps; any outside the consistency window are
         *   ignored, so passing more history than needed is fine.
         */
        fun of(
            bedTapAt: Instant,
            zone: ZoneId,
            totalSleep: Duration?,
            snoozeCount: Int,
            recentBedTaps: List<BedTap>,
            goals: Goals = Goals(),
            config: ScoreConfig = ScoreConfig(),
        ): Regularity {
            val bedtime = 1.0 - ramp(
                minutesLate(bedTapAt, zone, goals),
                zeroAt = 0.0,
                fullAt = config.bedtimeZeroAfter.toMinutes().toDouble(),
            )

            val duration = totalSleep?.let {
                ramp(
                    it.toMinutes().toDouble(),
                    zeroAt = (goals.sleep - config.durationZeroBelow).toMinutes().toDouble(),
                    fullAt = goals.sleep.toMinutes().toDouble(),
                )
            }

            val overGoal = (snoozeCount - goals.maxSnoozes).coerceAtLeast(0)
            val snoozes = (1.0 - config.snoozeFactorStep * overGoal).coerceAtLeast(0.0)

            return Regularity(bedtime, duration, snoozes, consistency(bedTapAt, zone, recentBedTaps, goals, config))
        }

        /**
         * How late the bed tap was relative to the bedtime goal on that night's evening, in
         * minutes (negative = early). Measuring against each night's own date handles midnight.
         */
        fun minutesLate(bedTapAt: Instant, zone: ZoneId, goals: Goals): Double {
            val nightDate = NightDate.of(bedTapAt, zone)
            // A goal like 00:15 falls on the morning after the night's date.
            val goalDate = if (goals.bedtime < NightDate.DAY_STARTS_AT) nightDate.plusDays(1) else nightDate
            val goal = goalDate.atTime(goals.bedtime).atZone(zone).toInstant()
            return Duration.between(goal, bedTapAt).toMillis() / 60_000.0
        }

        /** Standard deviation of bedtimes over the window, mapped onto 0..1. */
        private fun consistency(
            bedTapAt: Instant,
            zone: ZoneId,
            recent: List<BedTap>,
            goals: Goals,
            config: ScoreConfig,
        ): Double? {
            val thisNight = NightDate.of(bedTapAt, zone)
            val windowStart = thisNight.minusDays(config.consistencyWindowNights - 1L)
            val inWindow = (recent.filter { it.at != bedTapAt } + BedTap(bedTapAt, zone))
                .filter { NightDate.of(it.at, it.zone) in windowStart..thisNight }
            if (inWindow.size < config.consistencyMinNights) return null

            val lateness = inWindow.map { minutesLate(it.at, it.zone, goals) }
            val mean = lateness.average()
            val sd = sqrt(lateness.sumOf { (it - mean) * (it - mean) } / lateness.size)
            return 1.0 - ramp(
                sd,
                zeroAt = config.consistencyFullAt.toMinutes().toDouble(),
                fullAt = config.consistencyZeroAt.toMinutes().toDouble(),
            )
        }
    }
}
