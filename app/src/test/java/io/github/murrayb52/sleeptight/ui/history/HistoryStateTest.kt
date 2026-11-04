package io.github.murrayb52.sleeptight.ui.history

import io.github.murrayb52.sleeptight.data.Night
import io.github.murrayb52.sleeptight.metrics.Goals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class HistoryStateTest {
    private val zone = ZoneId.of("Africa/Johannesburg")
    private fun ms(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    private fun night(id: Long, bed: String, wake: String, snoozes: Int = 0) =
        Night(id = id, bedTapAt = ms(bed), wakeTapAt = ms(wake), zoneId = zone.id, snoozeCount = snoozes)

    private val nights = listOf(
        night(1, "2026-11-03T21:50", "2026-11-04T05:50", snoozes = 1), // by goal
        night(2, "2026-11-02T22:30", "2026-11-03T06:00"), // 30 min late
        night(3, "2026-11-01T23:50", "2026-11-02T06:20", snoozes = 2), // 110 min late
        night(4, "2026-10-20T22:00", "2026-10-21T06:00"), // outside 7 days, inside 30
    )
    private val today = LocalDate.parse("2026-11-04")

    @Test
    fun `seven days keeps recent nights, newest first`() {
        val h = buildHistory(nights, mapOf(1L to 3), Goals(), HistoryRange.WEEK, today)
        assertEquals(listOf(1L, 2L, 3L), h.nights.map { it.id })
        assertEquals(3, h.nights.first().noiseEvents)
        assertEquals(LocalDate.parse("2026-11-03"), h.nights.first().date)
    }

    @Test
    fun `thirty days includes older nights`() {
        assertEquals(4, buildHistory(nights, emptyMap(), Goals(), HistoryRange.MONTH, today).nights.size)
    }

    @Test
    fun `averages handle bedtimes across midnight`() {
        val a = buildHistory(nights, emptyMap(), Goals(), HistoryRange.WEEK, today).averages!!
        assertEquals(3, a.nights)
        // Late by -10, 30 and 110 minutes: average 43.3 → 22:43.
        assertEquals(LocalTime.of(22, 43), a.bedtime)
        assertEquals(33, a.byGoalPercent)
        assertEquals(1.0, a.snoozesPerNight, 1e-9)
        assertEquals(Duration.ofMinutes((480 + 450 + 390) / 3L), a.timeInBed)
        assertNull(a.score)
    }

    @Test
    fun `week strip covers the last seven dates, with gaps`() {
        val h = buildHistory(nights, emptyMap(), Goals(), HistoryRange.WEEK, today)
        assertEquals(7, h.week.size)
        assertEquals(today, h.week.last().date)
        assertEquals(1L, h.week[5].nightId) // 3 Nov
        assertNull(h.week.last().nightId) // tonight hasn't happened
    }

    @Test
    fun `no nights, no averages`() {
        assertNull(buildHistory(emptyList(), emptyMap(), Goals(), HistoryRange.WEEK, today).averages)
    }
}
