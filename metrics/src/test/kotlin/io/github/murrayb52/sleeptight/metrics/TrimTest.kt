package io.github.murrayb52.sleeptight.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimTest {
    private fun seg(from: String, to: String, stage: Stage = Stage.LIGHT) = SleepSegment(at(from), at(to), stage)
    private val bed = at("2026-11-03T22:00")
    private val wake = at("2026-11-04T06:00")

    @Test
    fun `segments are clipped to the taps and ones outside are dropped`() {
        val trimmed = trimToNight(
            listOf(
                seg("2026-11-03T21:00", "2026-11-03T21:50"), // before bed: dropped
                seg("2026-11-03T21:30", "2026-11-03T23:00"), // clipped at the start
                seg("2026-11-04T05:30", "2026-11-04T07:00", Stage.REM), // clipped at the end
                seg("2026-11-04T06:30", "2026-11-04T07:30"), // after wake: dropped
            ),
            bed,
            wake,
        )
        assertEquals(listOf(seg("2026-11-03T22:00", "2026-11-03T23:00"), seg("2026-11-04T05:30", "2026-11-04T06:00", Stage.REM)), trimmed)
    }

    @Test
    fun `overlaps are removed so no minute counts twice, in time order`() {
        val trimmed = trimToNight(
            listOf(seg("2026-11-04T01:00", "2026-11-04T03:00", Stage.DEEP), seg("2026-11-03T23:00", "2026-11-04T02:00")),
            bed,
            wake,
        )
        assertEquals(listOf(seg("2026-11-03T23:00", "2026-11-04T02:00"), seg("2026-11-04T02:00", "2026-11-04T03:00", Stage.DEEP)), trimmed)
    }

    @Test
    fun `nothing in range, nothing out`() {
        assertTrue(trimToNight(listOf(seg("2026-11-04T07:00", "2026-11-04T08:00")), bed, wake).isEmpty())
    }
}
