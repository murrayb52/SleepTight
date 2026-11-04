package io.github.murrayb52.sleeptight.metrics

import java.time.Instant

/** Sleep stage of a segment. [UNKNOWN] means asleep, stage not known (counts as sleep). */
enum class Stage { AWAKE, LIGHT, DEEP, REM, UNKNOWN }

/** Where a night's segments came from. Only [GARMIN] segments distinguish deep and REM. */
enum class SleepDataSource { GARMIN, ESTIMATED }

data class SleepSegment(val start: Instant, val end: Instant, val stage: Stage)

data class NoisePeriod(val start: Instant, val end: Instant, val peakDbRelative: Double)

/**
 * Everything needed to work out one night's metrics. [source] is null when the night has no
 * sleep data at all (no Garmin import and no estimate).
 */
data class NightInput(
    val bedTapAt: Instant,
    val wakeTapAt: Instant,
    val segments: List<SleepSegment>,
    val source: SleepDataSource?,
    val noise: List<NoisePeriod> = emptyList(),
) {
    init {
        require(wakeTapAt.isAfter(bedTapAt)) { "wakeTapAt must be after bedTapAt" }
    }
}
