package io.github.murrayb52.sleeptight.alarm

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * The built-in alarm sound, generated rather than bundled so the app needs no audio files: a
 * rising three-note bell (C6, E6, G6) that decays and repeats every 3 seconds.
 */
object Chime {
    const val SAMPLE_RATE = 44_100
    private const val LOOP_SECONDS = 3.0
    private const val PEAK = 0.8
    private val notes = listOf(0.00 to 1046.50, 0.35 to 1318.51, 0.70 to 1567.98)

    fun generate(sampleRate: Int = SAMPLE_RATE): ShortArray {
        val n = (sampleRate * LOOP_SECONDS).toInt()
        val mix = DoubleArray(n)
        for ((offset, freq) in notes) {
            val start = (offset * sampleRate).toInt()
            for (i in start until n) {
                val t = (i - start).toDouble() / sampleRate
                val envelope = exp(-3.0 * t) * min(1.0, t / 0.005) // 5 ms attack avoids a click
                mix[i] += envelope * (
                    sin(2 * PI * freq * t) + 0.25 * sin(2 * PI * 2 * freq * t) + 0.1 * sin(2 * PI * 3 * freq * t)
                    )
            }
        }
        val peak = mix.maxOf { abs(it) }.takeIf { it > 0 } ?: 1.0
        return ShortArray(n) { (mix[it] / peak * PEAK * Short.MAX_VALUE).toInt().toShort() }
    }
}
