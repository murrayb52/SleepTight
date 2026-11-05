package io.github.murrayb52.sleeptight.metrics

import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
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
    /**
     * A second this many dB above baseline (strictly) counts towards activity. 8 dB was far too
     * sensitive on a real phone: 5 % of a soundly slept night's seconds cleared it. 12 dB, with
     * the values below, fitted Garmin best on the first overnight test (2026-11-05).
     */
    val activityThresholdDb: Double = 12.0,
    /** Sleep estimate: a minute with at least this many active seconds is awake. */
    val estimateAwakeActiveSeconds: Int = 5,
    /** Falling asleep takes a run of at least this many quiet minutes (the whole run counts). */
    val estimateMinQuietMinutes: Int = 10,
    /** After this many minutes awake (or missing), falling asleep again needs the quiet run again. */
    val estimateResettleMinutes: Int = 15,
    /** Stored band levels: low is below this... */
    val lowBandMaxHz: Double = 300.0,
    /** ...high is above this, and mid is between. */
    val highBandMinHz: Double = 2_000.0,
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

/** One second of sound described by numbers only (no audio), all in dBFS. */
data class SecondFeatures(
    /** RMS level of the whole second: what the stored level was before 2026-11-05. */
    val dbfs: Double,
    /** The loudest single sample: a sharp knock shows here even when the second's average is low. */
    val peakDbfs: Double,
    /** Below [SoundConfig.lowBandMaxHz]: thumps, movement against the bed, snoring's rumble. */
    val lowDbfs: Double,
    /** Between the two: most of the voice range (talking, coughing). */
    val midDbfs: Double,
    /** Above [SoundConfig.highBandMinHz]: rustling bedding, hiss, sibilants. */
    val highDbfs: Double,
)

/**
 * Builds one second's level from shorter chunks (for example 100 ms), so the live display can update
 * ten times a second while the stored level is still the RMS of the whole second. Alongside the
 * level it keeps the peak and the level in three frequency bands ([features]). The band filters
 * keep their state across seconds, as a continuous stream needs; [reset] only starts a new second.
 */
class LevelAccumulator(sampleRate: Int = 16_000, config: SoundConfig = SoundConfig()) {
    private var sumSquares = 0.0
    private var peak = 0
    private var lowSum = 0.0
    private var midSum = 0.0
    private var highSum = 0.0
    private val low = Biquad.lowPass(sampleRate, config.lowBandMaxHz)
    private val midHigh = Biquad.highPass(sampleRate, config.lowBandMaxHz)
    private val midLow = Biquad.lowPass(sampleRate, config.highBandMinHz)
    private val high = Biquad.highPass(sampleRate, config.highBandMinHz)
    var samples = 0
        private set

    /** Adds a chunk and returns the chunk's own level (dBFS), for the live display. */
    fun add(chunk: ShortArray, count: Int = chunk.size): Double {
        var chunkSum = 0.0
        for (i in 0 until count) {
            val v = chunk[i].toInt()
            val s = v.toDouble()
            chunkSum += s * s
            if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
            val l = low.process(s)
            lowSum += l * l
            val m = midLow.process(midHigh.process(s))
            midSum += m * m
            val h = high.process(s)
            highSum += h * h
        }
        sumSquares += chunkSum
        samples += count
        return levelOf(chunkSum, count)
    }

    /** The level (dBFS) of everything added since the last [reset]. */
    fun dbfs(): Double = levelOf(sumSquares, samples)

    /** The level, peak and band levels of everything added since the last [reset]. */
    fun features(): SecondFeatures = SecondFeatures(
        dbfs = dbfs(),
        peakDbfs = if (peak == 0) SoundLevel.FLOOR_DBFS else (20 * log10(peak.toDouble() / Short.MAX_VALUE)).coerceAtLeast(SoundLevel.FLOOR_DBFS),
        lowDbfs = levelOf(lowSum, samples),
        midDbfs = levelOf(midSum, samples),
        highDbfs = levelOf(highSum, samples),
    )

    fun reset() {
        sumSquares = 0.0
        peak = 0
        lowSum = 0.0
        midSum = 0.0
        highSum = 0.0
        samples = 0
    }

    private fun levelOf(sum: Double, n: Int): Double {
        if (n <= 0 || sum < 1e-9) return SoundLevel.FLOOR_DBFS
        return (20 * log10(sqrt(sum / n) / Short.MAX_VALUE)).coerceAtLeast(SoundLevel.FLOOR_DBFS)
    }
}

/** A second-order (12 dB per octave) Butterworth filter, from the Audio EQ Cookbook. */
internal class Biquad private constructor(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }

    companion object {
        private const val Q = 0.7071067811865476 // Butterworth

        fun lowPass(sampleRate: Int, hz: Double): Biquad {
            val w = 2 * PI * hz / sampleRate
            val alpha = sin(w) / (2 * Q)
            val c = cos(w)
            val a0 = 1 + alpha
            return Biquad((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
        }

        fun highPass(sampleRate: Int, hz: Double): Biquad {
            val w = 2 * PI * hz / sampleRate
            val alpha = sin(w) / (2 * Q)
            val c = cos(w)
            val a0 = 1 + alpha
            return Biquad((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
        }
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
