package io.github.murrayb52.sleeptight.metrics

import java.time.Duration
import java.time.Instant
import kotlin.math.log10
import kotlin.math.sqrt

/** Every overnight sound threshold, in one place. Levels are dB relative to the rolling baseline. */
data class SoundConfig(
    /** The baseline is the median level over this many one-second samples (20 minutes). */
    val baselineWindowSeconds: Int = 20 * 60,
    /** A second more than this many dB above baseline counts as loud... */
    val noiseThresholdDb: Double = 15.0,
    /** ...and an event needs at least this many consecutive loud seconds. */
    val minNoiseSeconds: Int = 1,
    /** Events closer together than this are merged into one. */
    val noiseMergeGap: Duration = Duration.ofSeconds(30),
    /** A second this many dB above baseline (strictly) counts towards activity. */
    val activityThresholdDb: Double = 8.0,
    /** Sleep estimate: a minute with at least this many active seconds is awake... */
    val estimateAwakeActiveSeconds: Int = 3,
    /** ...and only a run of at least this many quiet minutes counts as asleep. */
    val estimateMinQuietMinutes: Int = 20,
)

object SoundLevel {
    /** Level reported for digital silence, where the true value would be minus infinity. */
    const val FLOOR_DBFS: Double = -120.0

    /** RMS level of 16-bit PCM in dB relative to full scale (0 dBFS = loudest possible). */
    fun dbfs(samples: ShortArray, count: Int = samples.size): Double {
        if (count <= 0) return FLOOR_DBFS
        var sumSquares = 0.0
        for (i in 0 until count) {
            val s = samples[i].toDouble()
            sumSquares += s * s
        }
        val rms = sqrt(sumSquares / count)
        if (rms == 0.0) return FLOOR_DBFS
        return (20 * log10(rms / Short.MAX_VALUE)).coerceAtLeast(FLOOR_DBFS)
    }
}

/** Median of the last [capacity] values, kept sorted so each update is cheap. */
class RollingMedian(private val capacity: Int) {
    private val order = ArrayDeque<Double>()
    private val sorted = ArrayList<Double>()

    val size: Int get() = order.size

    val median: Double?
        get() {
            val n = sorted.size
            if (n == 0) return null
            return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
        }

    fun add(value: Double) {
        if (order.size == capacity) {
            val oldest = order.removeFirst()
            sorted.removeAt(sorted.binarySearch(oldest).let { if (it < 0) -it - 1 else it })
        }
        order.addLast(value)
        val i = sorted.binarySearch(value)
        sorted.add(if (i < 0) -i - 1 else i, value)
    }
}

/** What a new sample did to the noise events: started a new one, or extended the latest. */
sealed interface NoiseChange {
    val event: NoisePeriod

    data class Started(override val event: NoisePeriod) : NoiseChange
    data class Extended(override val event: NoisePeriod) : NoiseChange
}

/**
 * Turns one-second relative levels into noise events: loud for at least
 * [SoundConfig.minNoiseSeconds], merged when less than [SoundConfig.noiseMergeGap] apart.
 * Each sample at `t` covers the second `[t, t + 1 s)`.
 */
class NoiseEventDetector(private val config: SoundConfig = SoundConfig()) {
    private var current: NoisePeriod? = null
    private var runStart: Instant? = null
    private var runPeak = 0.0
    private var runLength = 0

    fun onSample(at: Instant, dbRelative: Double): NoiseChange? {
        if (dbRelative <= config.noiseThresholdDb) {
            runStart = null
            runLength = 0
            runPeak = 0.0
            return null
        }
        if (runStart == null) {
            runStart = at
            runPeak = dbRelative
        }
        runLength++
        runPeak = maxOf(runPeak, dbRelative)
        if (runLength < config.minNoiseSeconds) return null

        val end = at.plusSeconds(1)
        val latest = current
        return if (latest != null && Duration.between(latest.end, runStart) < config.noiseMergeGap) {
            latest.copy(end = end, peakDbRelative = maxOf(latest.peakDbRelative, runPeak))
                .also { current = it }
                .let { NoiseChange.Extended(it) }
        } else {
            NoisePeriod(runStart!!, end, runPeak).also { current = it }.let { NoiseChange.Started(it) }
        }
    }
}

data class LevelSample(val at: Instant, val dbRelative: Double)

data class MinuteActivity(val minuteStart: Instant, val activeSeconds: Int)

/**
 * Activity per minute: the number of seconds above [SoundConfig.activityThresholdDb]. A rough
 * movement proxy for the smart alarm and the fallback sleep estimate. Minutes with no samples
 * are left out.
 */
fun activityPerMinute(samples: List<LevelSample>, config: SoundConfig = SoundConfig()): List<MinuteActivity> =
    samples.groupBy { it.at.toEpochMilli() / 60_000 }
        .toSortedMap()
        .map { (minute, inMinute) ->
            MinuteActivity(
                Instant.ofEpochMilli(minute * 60_000),
                inMinute.count { it.dbRelative > config.activityThresholdDb },
            )
        }
