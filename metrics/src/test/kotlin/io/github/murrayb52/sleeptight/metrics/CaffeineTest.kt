package io.github.murrayb52.sleeptight.metrics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CaffeineTest {
    private val bed = Instant.parse("2026-11-05T20:00:00Z")
    private fun hoursBefore(h: Double) = bed.minus(Duration.ofMinutes((h * 60).toLong()))

    @Test
    fun `a drink at the moment counted is all still there`() {
        assertEquals(100.0, caffeineAt(listOf(CaffeineDose(bed, 100.0)), bed), 1e-9)
    }

    @Test
    fun `half is left after one half-life and a quarter after two`() {
        assertEquals(50.0, caffeineAt(listOf(CaffeineDose(hoursBefore(5.0), 100.0)), bed, halfLifeHours = 5.0), 1e-9)
        assertEquals(25.0, caffeineAt(listOf(CaffeineDose(hoursBefore(10.0), 100.0)), bed, halfLifeHours = 5.0), 1e-9)
    }

    @Test
    fun `drinks add up`() {
        val doses = listOf(CaffeineDose(hoursBefore(5.0), 126.0), CaffeineDose(hoursBefore(2.5), 50.0))
        assertEquals(63.0 + 50.0 * Math.pow(0.5, 0.5), caffeineAt(doses, bed), 1e-9)
    }

    @Test
    fun `drinks after the moment or over a day before are ignored`() {
        val doses = listOf(CaffeineDose(bed.plusSeconds(60), 100.0), CaffeineDose(hoursBefore(24.5), 100.0))
        assertEquals(0.0, caffeineAt(doses, bed), 1e-9)
    }
}
