package io.github.murrayb52.sleeptight.metrics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class NightDateTest {
    private val zone = ZoneId.of("Africa/Johannesburg")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test
    fun `an evening bed tap belongs to that evening`() {
        assertEquals(LocalDate.parse("2026-11-03"), NightDate.of(at("2026-11-03T21:45"), zone))
    }

    @Test
    fun `an after-midnight bed tap belongs to the previous evening`() {
        assertEquals(LocalDate.parse("2026-11-03"), NightDate.of(at("2026-11-04T00:30"), zone))
    }

    @Test
    fun `a tap just before noon still belongs to the previous evening`() {
        assertEquals(LocalDate.parse("2026-11-03"), NightDate.of(at("2026-11-04T11:59"), zone))
    }

    @Test
    fun `the day starts at noon`() {
        assertEquals(LocalDate.parse("2026-11-04"), NightDate.of(at("2026-11-04T12:00"), zone))
    }

    @Test
    fun `the same instant can fall on different nights in different zones`() {
        val instant = at("2026-11-04T00:30") // 22:30 the previous evening in London (GMT)
        assertEquals(LocalDate.parse("2026-11-03"), NightDate.of(instant, ZoneId.of("Europe/London")))
        assertEquals(LocalDate.parse("2026-11-03"), NightDate.of(instant, zone))
        // 11:30 in Auckland (UTC+13 in November) on the 4th: still the night of the 3rd.
        assertEquals(LocalDate.parse("2026-11-03"), NightDate.of(instant, ZoneId.of("Pacific/Auckland")))
        // 12:30 in Kiritimati (UTC+14): the day of the 4th has begun.
        assertEquals(LocalDate.parse("2026-11-04"), NightDate.of(instant, ZoneId.of("Pacific/Kiritimati")))
    }
}
