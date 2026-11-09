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
    fun `a bed tap inside the window keeps today's wake-by time and opens the window at the tap`() {
        // Changed 2026-11-09: this used to move to tomorrow, so an alarm set for 10 minutes after
        // going to bed (with a 30-minute window) silently didn't ring.
        val w = WakeWindow.after(at("2026-11-04T05:50"), ZONE, start, end)
        assertEquals(at("2026-11-04T05:50"), w.start)
        assertEquals(at("2026-11-04T06:15"), w.end)
    }

    @Test
    fun `a bed tap at the wake-by time or after means tomorrow's window`() {
        assertEquals(at("2026-11-05T06:15"), WakeWindow.after(at("2026-11-04T06:15"), ZONE, start, end).end)
        assertEquals(at("2026-11-05T05:45"), WakeWindow.after(at("2026-11-04T06:20"), ZONE, start, end).start)
    }

    @Test
    fun `a bed tap inside a window that crossed midnight keeps that window`() {
        val w = WakeWindow.after(at("2026-11-04T00:05"), ZONE, LocalTime.of(23, 50), LocalTime.of(0, 20))
        assertEquals(at("2026-11-04T00:05"), w.start)
        assertEquals(at("2026-11-04T00:20"), w.end)
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
    fun `eases in from 2 to 100 percent over 3 minutes, evenly in decibels`() {
        assertEquals(0.02f, VolumeRamp.at(Duration.ZERO), 1e-6f)
        // Halfway is the geometric mean, sqrt(0.02 * 1.0), not 51 %.
        assertEquals(0.1414f, VolumeRamp.at(Duration.ofSeconds(90)), 1e-3f)
        assertEquals(1.00f, VolumeRamp.at(Duration.ofMinutes(3)), 1e-6f)
        assertEquals(1.00f, VolumeRamp.at(Duration.ofMinutes(5)), 1e-6f)
        assertEquals(0.02f, VolumeRamp.at(Duration.ofSeconds(-3)), 1e-6f)
    }
}

class SnoozeLadderTest {
    @Test
    fun `constant snoozes are all 9 minutes`() {
        assertEquals(listOf(9, 9, 9), SnoozeLadder.lengths(3, SnoozeCurve.CONSTANT))
    }

    @Test
    fun `linear snoozes fall in equal steps from 9 to 2 minutes`() {
        // 9, 7.25, 5.5, 3.75, 2, rounded.
        assertEquals(listOf(9, 7, 6, 4, 2), SnoozeLadder.lengths(5, SnoozeCurve.LINEAR))
        assertEquals(listOf(9, 8, 7, 7, 6, 5, 4, 4, 3, 2), SnoozeLadder.lengths(10, SnoozeCurve.LINEAR))
    }

    @Test
    fun `exponential snoozes fall by the same fraction each time, from 9 to 2 minutes`() {
        // 9, 6.18, 4.24, 2.91, 2: each about 0.69 of the one before, so the big drops come first.
        assertEquals(listOf(9, 6, 4, 3, 2), SnoozeLadder.lengths(5, SnoozeCurve.EXPONENTIAL))
        assertEquals(listOf(9, 8, 6, 5, 5, 4, 3, 3, 2, 2), SnoozeLadder.lengths(10, SnoozeCurve.EXPONENTIAL))
    }

    @Test
    fun `one snooze is the first length, two go straight from first to last`() {
        SnoozeCurve.entries.forEach { curve ->
            assertEquals(listOf(9), SnoozeLadder.lengths(1, curve))
        }
        assertEquals(listOf(9, 2), SnoozeLadder.lengths(2, SnoozeCurve.LINEAR))
        assertEquals(listOf(9, 2), SnoozeLadder.lengths(2, SnoozeCurve.EXPONENTIAL))
        assertEquals(listOf(9, 9), SnoozeLadder.lengths(2, SnoozeCurve.CONSTANT))
    }

    @Test
    fun `no snoozes allowed means none at all`() {
        assertEquals(emptyList<Int>(), SnoozeLadder.lengths(0, SnoozeCurve.LINEAR))
        assertEquals(null, SnoozeLadder.minutesFor(snoozesSoFar = 0, allowed = 0, curve = SnoozeCurve.LINEAR))
    }

    @Test
    fun `the next snooze follows the ladder, then runs out`() {
        val next = (0..6).map { SnoozeLadder.minutesFor(snoozesSoFar = it, allowed = 5, curve = SnoozeCurve.EXPONENTIAL) }
        assertEquals(listOf(9, 6, 4, 3, 2, null, null), next)
    }

    @Test
    fun `settings outside the allowed range are clamped`() {
        assertEquals(10, SnoozeLadder.lengths(25, SnoozeCurve.CONSTANT).size)
        assertEquals(0, SnoozeLadder.lengths(-1, SnoozeCurve.CONSTANT).size)
    }

    @Test
    fun `remaining snoozes count down`() {
        assertEquals(5, SnoozeLadder.remaining(0, allowed = 5))
        assertEquals(1, SnoozeLadder.remaining(4, allowed = 5))
        assertEquals(0, SnoozeLadder.remaining(7, allowed = 5))
    }
}

class BedtimeReminderTest {
    private val bedtime = LocalTime.of(22, 0)

    @Test
    fun `the reminder is 30 minutes before bedtime, later today`() {
        assertEquals(at("2026-11-04T21:30"), BedtimeReminder.next(at("2026-11-04T18:00"), ZONE, bedtime))
    }

    @Test
    fun `once today's reminder has passed, it's tomorrow's`() {
        assertEquals(at("2026-11-05T21:30"), BedtimeReminder.next(at("2026-11-04T21:30"), ZONE, bedtime))
        assertEquals(at("2026-11-05T21:30"), BedtimeReminder.next(at("2026-11-04T23:59"), ZONE, bedtime))
    }

    @Test
    fun `an after-midnight bedtime reminds on the right side of midnight`() {
        assertEquals(at("2026-11-04T00:00"), BedtimeReminder.next(at("2026-11-03T22:00"), ZONE, LocalTime.of(0, 30)))
    }
}
