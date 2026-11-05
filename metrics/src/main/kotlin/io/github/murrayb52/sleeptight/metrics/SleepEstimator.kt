package io.github.murrayb52.sleeptight.metrics

import java.time.Duration
import java.time.Instant

/**
 * Estimates asleep or awake from activity per minute, for nights without Garmin data.
 *
 * - A minute with at least [SoundConfig.estimateAwakeActiveSeconds] active seconds is restless.
 * - **Falling asleep** takes a run of at least [SoundConfig.estimateMinQuietMinutes] quiet minutes;
 *   the whole run counts as asleep. Until then, every minute is awake.
 * - **Once asleep**, quiet minutes are asleep and restless minutes awake, so a stir costs only
 *   its own minutes.
 * - After [SoundConfig.estimateResettleMinutes] restless minutes in a row, you're awake again, and
 *   falling back asleep needs the quiet run again. A gap that long does the same.
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

        val asleep = BooleanArray(minutes.size)
        var settled = false
        var quietRun = 0
        var restlessRun = 0
        minutes.forEachIndexed { i, m ->
            val missing = if (i == 0) 0L else Duration.between(minutes[i - 1].minuteStart, m.minuteStart).toMinutes() - 1
            if (missing >= config.estimateResettleMinutes) settled = false
            if (missing > 0) quietRun = 0

            if (m.activeSeconds >= config.estimateAwakeActiveSeconds) {
                quietRun = 0
                restlessRun++
                if (restlessRun >= config.estimateResettleMinutes) settled = false
            } else {
                restlessRun = 0
                quietRun++
                if (settled) {
                    asleep[i] = true
                } else if (quietRun >= config.estimateMinQuietMinutes) {
                    settled = true
                    for (k in i - quietRun + 1..i) asleep[k] = true
                }
            }
        }

        return minutes
            .mapIndexed { i, m -> SleepSegment(m.minuteStart, m.minuteStart.plus(MINUTE), if (asleep[i]) Stage.UNKNOWN else Stage.AWAKE) }
            .fold(mutableListOf<SleepSegment>()) { acc, s ->
                val prev = acc.lastOrNull()
                if (prev != null && prev.stage == s.stage && prev.end == s.start) acc[acc.lastIndex] = prev.copy(end = s.end) else acc += s
                acc
            }
            .map { it.copy(start = maxOf(it.start, bedTapAt), end = minOf(it.end, wakeTapAt)) }
            .filter { it.end.isAfter(it.start) }
    }
}
