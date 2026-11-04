package io.github.murrayb52.sleeptight.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalTime

class RegularityTest {
    private val eps = 1e-9

    private fun regularity(
        bed: String = "2026-11-03T22:00",
        totalSleep: Duration? = minutes(480),
        snoozes: Int = 0,
        history: List<String> = emptyList(),
        goals: Goals = Goals(),
    ) = Regularity.of(
        bedTapAt = at(bed),
        zone = ZONE,
        totalSleep = totalSleep,
        snoozeCount = snoozes,
        recentBedTaps = history.map { BedTap(at(it), ZONE) },
        goals = goals,
    )

    @Test
    fun `bedtime factor is full by 22h00 and falls to zero at 23h00`() {
        assertEquals(1.0, regularity(bed = "2026-11-03T21:15").bedtime, eps)
        assertEquals(1.0, regularity(bed = "2026-11-03T22:00").bedtime, eps)
        assertEquals(0.5, regularity(bed = "2026-11-03T22:30").bedtime, eps)
        assertEquals(0.0, regularity(bed = "2026-11-03T23:00").bedtime, eps)
        assertEquals(0.0, regularity(bed = "2026-11-04T00:30").bedtime, eps)
    }

    @Test
    fun `bedtime follows the goal`() {
        val goals = Goals(bedtime = LocalTime.of(22, 30))
        assertEquals(1.0, regularity(bed = "2026-11-03T22:30", goals = goals).bedtime, eps)
        assertEquals(0.5, regularity(bed = "2026-11-03T23:00", goals = goals).bedtime, eps)
    }

    @Test
    fun `an after-midnight bedtime goal is measured on the right day`() {
        val goals = Goals(bedtime = LocalTime.of(0, 15))
        assertEquals(1.0, regularity(bed = "2026-11-03T23:50", goals = goals).bedtime, eps)
        assertEquals(0.5, regularity(bed = "2026-11-04T00:45", goals = goals).bedtime, eps)
    }

    @Test
    fun `duration factor is full at 8 hours and falls to zero at 6`() {
        assertEquals(1.0, regularity(totalSleep = minutes(540)).duration!!, eps)
        assertEquals(1.0, regularity(totalSleep = minutes(480)).duration!!, eps)
        assertEquals(0.5, regularity(totalSleep = minutes(420)).duration!!, eps)
        assertEquals(0.0, regularity(totalSleep = minutes(360)).duration!!, eps)
        assertEquals(0.0, regularity(totalSleep = minutes(200)).duration!!, eps)
        assertNull(regularity(totalSleep = null).duration)
    }

    @Test
    fun `snooze factor is full at one, half at two, zero at three or more`() {
        assertEquals(1.0, regularity(snoozes = 0).snoozes, eps)
        assertEquals(1.0, regularity(snoozes = 1).snoozes, eps)
        assertEquals(0.5, regularity(snoozes = 2).snoozes, eps)
        assertEquals(0.0, regularity(snoozes = 3).snoozes, eps)
        assertEquals(0.0, regularity(snoozes = 7).snoozes, eps)
    }

    @Test
    fun `consistency needs at least 3 nights`() {
        assertNull(regularity(history = listOf("2026-11-02T22:00")).consistency)
        assertEquals(1.0, regularity(history = listOf("2026-11-02T22:00", "2026-11-01T22:00")).consistency!!, eps)
    }

    @Test
    fun `consistency is measured across midnight`() {
        // 23:50, 00:10, 23:50, 00:10: standard deviation 10 min, inside the 15-minute full-marks band.
        val r = regularity(
            bed = "2026-11-04T00:10",
            history = listOf("2026-11-02T23:50", "2026-11-02T00:10", "2026-11-01T23:50"),
        )
        assertEquals(1.0, r.consistency!!, eps)
    }

    @Test
    fun `consistency falls linearly from 15 to 60 minutes of spread`() {
        // Bed times of 21:22:30 and 22:37:30 alternate: population SD 37.5 min, halfway.
        val r = regularity(
            bed = "2026-11-03T22:37:30",
            history = listOf("2026-11-02T21:22:30", "2026-11-01T22:37:30", "2026-10-31T21:22:30"),
        )
        assertEquals(0.5, r.consistency!!, eps)
        val wild = regularity(bed = "2026-11-03T23:30", history = listOf("2026-11-02T21:00", "2026-11-01T23:30", "2026-10-31T21:00"))
        assertEquals(0.0, wild.consistency!!, eps)
    }

    @Test
    fun `consistency only looks at the last 7 nights`() {
        // Nights before 28 Oct are outside the window for the night of 3 Nov.
        val r = regularity(
            history = listOf("2026-11-02T22:00", "2026-10-28T22:00", "2026-10-27T20:00", "2026-10-20T20:00"),
        )
        assertEquals(1.0, r.consistency!!, eps)
    }

    @Test
    fun `this night is not counted twice if it is also in the history`() {
        val r = regularity(bed = "2026-11-03T22:00", history = listOf("2026-11-03T22:00", "2026-11-02T22:00"))
        assertNull(r.consistency)
    }

    @Test
    fun `the mean ignores factors that can't be worked out`() {
        val r = regularity(bed = "2026-11-03T22:30", totalSleep = null, snoozes = 0)
        assertEquals((0.5 + 1.0) / 2, r.mean, eps)
        val full = regularity(
            bed = "2026-11-03T22:30",
            totalSleep = minutes(420),
            snoozes = 2,
            history = listOf("2026-11-02T22:30", "2026-11-01T22:30"),
        )
        assertEquals((0.5 + 0.5 + 0.5 + 1.0) / 4, full.mean, eps)
    }
}
