package io.github.murrayb52.sleeptight.metrics

import java.time.Duration
import java.time.Instant

/**
 * Estimates asleep or awake from activity per minute, for nights without Garmin data.
 *
 * - A minute with at least [SoundConfig.estimateAwakeActiveSeconds] active seconds is awake.
 * - Consecutive quiet minutes count as asleep only when the run lasts at least
 *   [SoundConfig.estimateMinQuietMinutes]; shorter quiet runs count as awake (not settled yet).
 * - Minutes with no data (for example while the service wasn't running) leave a gap.
 *
 * Asleep segments use [Stage.UNKNOWN] (asleep, stage not known): there's no deep or REM estimate.
 * Segments are merged where they touch and trimmed to [bedTapAt, wakeTapAt].
 */
object SleepEstimator {
    private val MINUTE: Duration = Duration.ofMinutes(1)

    fun estimate(activity: List<MinuteActivity>, bedTapAt: Instant, wakeTapAt: Instant, config: SoundConfig = SoundConfig()): List<SleepSegment> {
        val minutes = activity
            .filter { it.minuteStart.plus(MINUTE).isAfter(bedTapAt) && it.minuteStart.isBefore(wakeTapAt) }
            .sortedBy { it.minuteStart }
        if (minutes.isEmpty()) return emptyList()

        // Split into runs of consecutive minutes (a missing minute ends a run).
        val runs = mutableListOf<MutableList<MinuteActivity>>()
        for (m in minutes) {
            val last = runs.lastOrNull()?.lastOrNull()
            if (last != null && last.minuteStart.plus(MINUTE) == m.minuteStart) runs.last() += m else runs += mutableListOf(m)
        }

        val raw = mutableListOf<SleepSegment>()
        for (run in runs) {
            // Within a run, group quiet stretches and decide each one by its length.
            var i = 0
            while (i < run.size) {
                val quiet = run[i].activeSeconds < config.estimateAwakeActiveSeconds
                var j = i
                while (j < run.size && (run[j].activeSeconds < config.estimateAwakeActiveSeconds) == quiet) j++
                val stage = if (quiet && j - i >= config.estimateMinQuietMinutes) Stage.UNKNOWN else Stage.AWAKE
                raw += SleepSegment(run[i].minuteStart, run[j - 1].minuteStart.plus(MINUTE), stage)
                i = j
            }
        }

        return raw
            .fold(mutableListOf<SleepSegment>()) { acc, s ->
                val prev = acc.lastOrNull()
                if (prev != null && prev.stage == s.stage && prev.end == s.start) acc[acc.lastIndex] = prev.copy(end = s.end) else acc += s
                acc
            }
            .map { it.copy(start = maxOf(it.start, bedTapAt), end = minOf(it.end, wakeTapAt)) }
            .filter { it.end.isAfter(it.start) }
    }
}
