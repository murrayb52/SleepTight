package io.github.murrayb52.sleeptight.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.PI
import kotlin.math.sin

class SoundLevelTest {
    @Test
    fun `full-scale square wave is 0 dBFS`() {
        val samples = ShortArray(1600) { if (it % 2 == 0) Short.MAX_VALUE else (-Short.MAX_VALUE).toShort() }
        assertEquals(0.0, SoundLevel.dbfs(samples), 0.01)
    }

    @Test
    fun `halving the amplitude loses about 6 dB`() {
        fun sine(amplitude: Double) = ShortArray(16000) { (amplitude * sin(2 * PI * 440 * it / 16000)).toInt().toShort() }
        val loud = SoundLevel.dbfs(sine(16000.0))
        val quiet = SoundLevel.dbfs(sine(8000.0))
        assertEquals(6.02, loud - quiet, 0.05)
    }

    @Test
    fun `digital silence is floored instead of minus infinity`() {
        assertEquals(SoundLevel.FLOOR_DBFS, SoundLevel.dbfs(ShortArray(16000)), 0.0)
    }

    @Test
    fun `only the first count samples are used`() {
        val samples = ShortArray(100) { if (it < 50) 1000 else 0 }
        assertEquals(SoundLevel.dbfs(ShortArray(50) { 1000 }), SoundLevel.dbfs(samples, count = 50), 1e-9)
    }
}

class RollingMedianTest {
    @Test
    fun `empty has no median`() {
        assertNull(RollingMedian(5).median)
    }

    @Test
    fun `median of odd and even counts`() {
        val m = RollingMedian(10)
        listOf(5.0, 1.0, 3.0).forEach(m::add)
        assertEquals(3.0, m.median!!, 0.0)
        m.add(10.0)
        assertEquals(4.0, m.median!!, 0.0)
    }

    @Test
    fun `old values drop out of the window`() {
        val m = RollingMedian(3)
        listOf(100.0, 100.0, 100.0, 1.0, 2.0, 3.0).forEach(m::add)
        assertEquals(2.0, m.median!!, 0.0)
        assertEquals(3, m.size)
    }

    @Test
    fun `a few loud seconds don't move a 20-minute median`() {
        val m = RollingMedian(1200)
        repeat(1200) { m.add(-60.0) }
        repeat(30) { m.add(-20.0) }
        assertEquals(-60.0, m.median!!, 0.0)
    }
}

class NoiseEventDetectorTest {
    private val t0: Instant = at("2026-11-03T23:00")
    private fun t(s: Long) = t0.plusSeconds(s)

    /** Feeds one sample per second; [loud] seconds get the given level, others 0 dB. */
    private fun run(seconds: Long, loud: Map<Long, Double>, config: SoundConfig = SoundConfig()): List<NoiseChange> {
        val d = NoiseEventDetector(config)
        return (0 until seconds).mapNotNull { s -> d.onSample(t(s), loud[s] ?: 0.0) }
    }

    @Test
    fun `quiet night, no events`() {
        assertTrue(run(600, emptyMap()).isEmpty())
    }

    @Test
    fun `just under the threshold is not an event`() {
        assertTrue(run(60, mapOf(10L to 15.0)).isEmpty())
    }

    @Test
    fun `one loud second is an event`() {
        val changes = run(60, mapOf(10L to 22.0))
        assertEquals(listOf(NoiseChange.Started(NoisePeriod(t(10), t(11), 22.0))), changes)
    }

    @Test
    fun `consecutive loud seconds extend the event and keep the peak`() {
        val changes = run(60, mapOf(10L to 20.0, 11L to 30.0, 12L to 18.0))
        assertEquals(3, changes.size)
        assertEquals(NoiseChange.Extended(NoisePeriod(t(10), t(13), 30.0)), changes.last())
    }

    @Test
    fun `events under 30 seconds apart merge`() {
        // Ends at 11 s; the next starts at 40 s, a 29-second gap.
        val changes = run(120, mapOf(10L to 20.0, 40L to 25.0))
        assertEquals(NoiseChange.Extended(NoisePeriod(t(10), t(41), 25.0)), changes.last())
    }

    @Test
    fun `events 30 seconds or more apart stay separate`() {
        val changes = run(120, mapOf(10L to 20.0, 41L to 25.0))
        assertEquals(2, changes.count { it is NoiseChange.Started })
    }

    @Test
    fun `a longer minimum duration needs a run of loud seconds`() {
        val config = SoundConfig(minNoiseSeconds = 3)
        assertTrue(run(60, mapOf(10L to 20.0, 11L to 20.0), config).isEmpty())
        val changes = run(60, mapOf(10L to 20.0, 11L to 21.0, 12L to 20.0), config)
        assertEquals(listOf(NoiseChange.Started(NoisePeriod(t(10), t(13), 21.0))), changes)
    }
}

class ActivityTest {
    @Test
    fun `counts seconds above the activity threshold per minute`() {
        val start = at("2026-11-03T23:00")
        val samples = (0 until 180L).map { s ->
            val level = when {
                s < 60 -> if (s < 5) 9.0 else 2.0 // 5 active seconds in minute 0
                s < 120 -> 8.0 // exactly at the threshold: not active
                else -> 20.0 // all 60 active
            }
            LevelSample(start.plusSeconds(s), level)
        }
        val activity = activityPerMinute(samples)
        assertEquals(listOf(5, 0, 60), activity.map { it.activeSeconds })
        assertEquals(listOf(start, start.plusSeconds(60), start.plusSeconds(120)), activity.map { it.minuteStart })
    }

    @Test
    fun `minutes with no samples are absent`() {
        val start = at("2026-11-03T23:00")
        val activity = activityPerMinute(listOf(LevelSample(start, 10.0), LevelSample(start.plusSeconds(300), 10.0)))
        assertEquals(2, activity.size)
    }
}
