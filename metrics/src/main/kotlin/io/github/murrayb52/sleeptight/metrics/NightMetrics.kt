package io.github.murrayb52.sleeptight.metrics

import java.time.Duration
import java.time.Instant

/**
 * One night's metrics. Sleep metrics are null when the night has no sleep data
 * ([hasSleepData] false); deep and REM shares are null unless the stages came from Garmin.
 */
data class NightMetrics(
    val timeInBed: Duration,
    val hasSleepData: Boolean,
    /** Start of the first non-awake segment. */
    val sleepOnset: Instant?,
    val timeToFallAsleep: Duration?,
    val totalSleep: Duration?,
    val wakeUps: Int?,
    /** Total sleep / time in bed, 0..1. */
    val efficiency: Double?,
    val deepShare: Double?,
    val remShare: Double?,
    val deepRemShare: Double?,
    /** Noise events inside the night, in time order. */
    val noiseEvents: List<NoisePeriod>,
    val noiseClusters: Int,
) {
    companion object {
        fun of(input: NightInput, config: ScoreConfig = ScoreConfig()): NightMetrics {
            val timeInBed = Duration.between(input.bedTapAt, input.wakeTapAt)
            val noise = input.noise
                .filter { it.end.isAfter(input.bedTapAt) && it.start.isBefore(input.wakeTapAt) }
                .sortedBy { it.start }
            val clusters = countClusters(noise, config.noiseClusterGap)

            if (input.source == null) {
                return NightMetrics(timeInBed, false, null, null, null, null, null, null, null, null, noise, clusters)
            }

            val segments = trimToNight(input.segments, input.bedTapAt, input.wakeTapAt)
            val asleep = segments.filter { it.stage != Stage.AWAKE }
            val totalSleep = asleep.fold(Duration.ZERO) { sum, s -> sum + s.length }
            val onset = asleep.firstOrNull()?.start
            val wakeUps = countWakeUps(segments, config.minWakeUp)

            val stageShares = input.source == SleepDataSource.GARMIN && !totalSleep.isZero
            fun share(vararg stages: Stage): Double? = if (!stageShares) {
                null
            } else {
                segments.filter { it.stage in stages }.fold(Duration.ZERO) { sum, s -> sum + s.length }.toMillis()
                    .toDouble() / totalSleep.toMillis()
            }

            return NightMetrics(
                timeInBed = timeInBed,
                hasSleepData = true,
                sleepOnset = onset,
                timeToFallAsleep = onset?.let { Duration.between(input.bedTapAt, it) },
                totalSleep = totalSleep,
                wakeUps = wakeUps,
                efficiency = totalSleep.toMillis().toDouble() / timeInBed.toMillis(),
                deepShare = share(Stage.DEEP),
                remShare = share(Stage.REM),
                deepRemShare = share(Stage.DEEP, Stage.REM),
                noiseEvents = noise,
                noiseClusters = clusters,
            )
        }

        /**
         * Wake-ups are runs of awake segments (back-to-back awake segments form one run) that
         * come after sleep onset, last at least [minWakeUp], and are followed by more sleep.
         * The final awakening before the wake tap isn't an interruption.
         */
        private fun countWakeUps(segments: List<SleepSegment>, minWakeUp: Duration): Int {
            var count = 0
            var seenSleep = false
            var run = Duration.ZERO
            for (s in segments) {
                if (s.stage == Stage.AWAKE) {
                    if (seenSleep) run += s.length
                } else {
                    if (run >= minWakeUp) count++
                    run = Duration.ZERO
                    seenSleep = true
                }
            }
            return count
        }

        private fun countClusters(noise: List<NoisePeriod>, gap: Duration): Int {
            var clusters = 0
            var clusterEnd: Instant? = null
            for (event in noise) {
                if (clusterEnd == null || Duration.between(clusterEnd, event.start) > gap) clusters++
                clusterEnd = if (clusterEnd == null || event.end.isAfter(clusterEnd)) event.end else clusterEnd
            }
            return clusters
        }

        private val SleepSegment.length: Duration get() = Duration.between(start, end)
    }
}

/**
 * Trims segments to the night: clips them to [from, to] (the bed and wake taps), drops ones outside,
 * and removes overlaps (a later segment loses the part an earlier one already covers), so no minute
 * is counted twice. The result is in time order. Used for every imported segment: the taps define
 * the night.
 */
fun trimToNight(segments: List<SleepSegment>, from: Instant, to: Instant): List<SleepSegment> {
    val out = mutableListOf<SleepSegment>()
    var covered = from
    for (s in segments.sortedBy { it.start }) {
        val start = maxOf(s.start, covered)
        val end = minOf(s.end, to)
        if (end.isAfter(start)) {
            out += s.copy(start = start, end = end)
            covered = end
        }
    }
    return out
}
