package io.github.murrayb52.sleeptight.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class WakeWindowTest {
    private val start = LocalTime.of(5, 45)
    private val end = LocalTime.of(6, 15)

    @Test
    fun `an evening bed tap wakes the next morning`() {
        val w = WakeWindow.after(at("2026-11-03T22:00"), ZONE, start, end)
        assertEquals(at("2026-11-04T05:45"), w.start)
        assertEquals(at("2026-11-04T06:15"), w.end)
    }

    @Test
    fun `an after-midnight bed tap wakes the same morning`() {
        val w = WakeWindow.after(at("2026-11-04T01:30"), ZONE, start, end)
        assertEquals(at("2026-11-04T05:45"), w.start)
    }

    @Test
    fun `a bed tap inside the window means tomorrow's window`() {
        val w = WakeWindow.after(at("2026-11-04T05:50"), ZONE, start, end)
        assertEquals(at("2026-11-05T05:45"), w.start)
    }

    @Test
    fun `a window crossing midnight ends the next day`() {
        val w = WakeWindow.after(at("2026-11-03T20:00"), ZONE, LocalTime.of(23, 50), LocalTime.of(0, 20))
        assertEquals(at("2026-11-03T23:50"), w.start)
        assertEquals(at("2026-11-04T00:20"), w.end)
    }

    @Test
    fun `daylight saving is handled by the zone`() {
        // London clocks went back at 02:00 on 25 Oct 2026; the window is still 05:45 local.
        val london = ZoneId.of("Europe/London")
        val bed = java.time.LocalDateTime.parse("2026-10-24T22:00").atZone(london).toInstant()
        val w = WakeWindow.after(bed, london, start, end)
        assertEquals(java.time.LocalDateTime.parse("2026-10-25T05:45").atZone(london).toInstant(), w.start)
        assertEquals(Duration.ofMinutes(30), Duration.between(w.start, w.end))
    }
}

class SmartTriggerTest {
    private val windowStart = at("2026-11-04T05:45")

    /** A night of [minutes] quiet-ish minutes ending at [until], then the given recent minutes. */
    private fun night(minutes: Int, quietLevel: (Int) -> Int, recent: List<Int>, until: Instant = windowStart.plusSeconds(300)): List<MinuteActivity> {
        val total = minutes + recent.size
        val first = until.minus(Duration.ofMinutes(total.toLong()))
        return (0 until total).map { i ->
            val active = if (i < minutes) quietLevel(i) else recent[i - minutes]
            MinuteActivity(first.plus(Duration.ofMinutes(i.toLong())), active)
        }
    }

    private val now = windowStart.plusSeconds(300) // 05:50:00, last complete minutes are 05:48 and 05:49

    @Test
    fun `restless last two minutes above the 75th percentile trigger`() {
        // Night: mostly 0-4 active seconds; 75th percentile is low. Last two minutes: 12 and 10.
        val activity = night(400, { it % 5 }, recent = listOf(12, 10))
        assertTrue(SmartTrigger.shouldWakeNow(activity, now))
    }

    @Test
    fun `quiet last two minutes don't trigger`() {
        val activity = night(400, { it % 5 }, recent = listOf(1, 0))
        assertFalse(SmartTrigger.shouldWakeNow(activity, now))
    }

    @Test
    fun `a restless night needs more than its usual restlessness`() {
        // Every minute has 10 active seconds, so 10 isn't above the 75th percentile.
        val activity = night(400, { 10 }, recent = listOf(10, 10))
        assertFalse(SmartTrigger.shouldWakeNow(activity, now))
    }

    @Test
    fun `a dead-quiet night still needs a minimum of real activity`() {
        // 75th percentile is 0, so one cough (1 s) would be "above" it, but that's below the minimum.
        val activity = night(400, { 0 }, recent = listOf(1, 1))
        assertFalse(SmartTrigger.shouldWakeNow(activity, now))
        val moving = night(400, { 0 }, recent = listOf(4, 3))
        assertTrue(SmartTrigger.shouldWakeNow(moving, now))
    }

    @Test
    fun `missing recent minutes mean no early trigger`() {
        val activity = night(400, { it % 5 }, recent = listOf(12, 10)).dropLast(1)
        assertFalse(SmartTrigger.shouldWakeNow(activity, now))
    }

    @Test
    fun `too little history means no early trigger`() {
        val activity = night(20, { 0 }, recent = listOf(12, 10))
        assertFalse(SmartTrigger.shouldWakeNow(activity, now))
    }

    @Test
    fun `the current partial minute isn't used`() {
        // Activity in the minute starting 05:50 is ignored at 05:50:30.
        val activity = night(400, { it % 5 }, recent = listOf(1, 0)) +
            MinuteActivity(now, 50)
        assertFalse(SmartTrigger.shouldWakeNow(activity, now.plusSeconds(30)))
    }
}

class VolumeRampTest {
    @Test
    fun `ramps from 10 to 100 percent over 60 seconds`() {
        assertEquals(0.10f, VolumeRamp.at(Duration.ZERO), 1e-6f)
        assertEquals(0.55f, VolumeRamp.at(Duration.ofSeconds(30)), 1e-6f)
        assertEquals(1.00f, VolumeRamp.at(Duration.ofSeconds(60)), 1e-6f)
        assertEquals(1.00f, VolumeRamp.at(Duration.ofMinutes(5)), 1e-6f)
        assertEquals(0.10f, VolumeRamp.at(Duration.ofSeconds(-3)), 1e-6f)
    }
}
