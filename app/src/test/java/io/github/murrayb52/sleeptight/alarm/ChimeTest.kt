package io.github.murrayb52.sleeptight.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChimeTest {
    private val pcm = Chime.generate()

    @Test
    fun `three-second loop at 44_1 kHz`() {
        assertEquals(Chime.SAMPLE_RATE * 3, pcm.size)
    }

    @Test
    fun `peaks at 80 percent of full scale, never clipping`() {
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertEquals(0.8 * Short.MAX_VALUE, peak.toDouble(), Short.MAX_VALUE * 0.01)
    }

    @Test
    fun `starts and ends near silence so the loop doesn't click`() {
        assertTrue(abs(pcm.first().toInt()) < 100)
        assertTrue(pcm.takeLast(100).all { abs(it.toInt()) < 0.01 * Short.MAX_VALUE })
    }
}
