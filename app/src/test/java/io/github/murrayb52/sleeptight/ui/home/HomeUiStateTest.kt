package io.github.murrayb52.sleeptight.ui.home

import io.github.murrayb52.sleeptight.data.Night
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class HomeUiStateTest {
    private val zone = ZoneId.of("Africa/Johannesburg")
    private val eightHours = Duration.ofHours(8)

    private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()
    private fun night(id: Long, bed: String, wake: String? = null) =
        Night(id = id, bedTapAt = at(bed).toEpochMilli(), wakeTapAt = wake?.let { at(it).toEpochMilli() }, zoneId = zone.id)

    private fun build(open: Night?, now: String, dismissed: Long? = null) =
        buildHomeUiState(open, at(now), zone, eightHours, dismissed)

    @Test
    fun `no open night`() {
        val state = build(open = null, now = "2026-11-04T20:00")
        assertEquals(false, state.loading)
        assertNull(state.openNight)
        assertNull(state.overduePrompt)
    }

    @Test
    fun `open night reports time in bed so far`() {
        val state = build(open = night(1, "2026-11-03T22:00"), now = "2026-11-03T23:30")
        assertEquals(Duration.ofMinutes(90), state.openNight?.inBedFor)
        assertNull(state.overduePrompt)
    }

    @Test
    fun `no prompt just before 16 hours`() {
        val state = build(open = night(1, "2026-11-03T22:00"), now = "2026-11-04T13:59")
        assertNull(state.overduePrompt)
    }

    @Test
    fun `prompt at 16 hours, suggesting bed plus the sleep goal`() {
        val state = build(open = night(1, "2026-11-03T22:00"), now = "2026-11-04T14:00")
        val prompt = requireNotNull(state.overduePrompt)
        assertEquals(1L, prompt.nightId)
        assertEquals(Duration.ofHours(16), prompt.openFor)
        assertEquals(LocalTime.of(6, 0), prompt.suggestedWakeTime)
    }

    @Test
    fun `dismissed prompt stays hidden for that night only`() {
        assertNull(build(open = night(1, "2026-11-03T22:00"), now = "2026-11-04T15:00", dismissed = 1).overduePrompt)
        assertNotNull(build(open = night(2, "2026-11-03T22:00"), now = "2026-11-04T15:00", dismissed = 1).overduePrompt)
    }

    @Test
    fun `picked wake time after midnight resolves to the next morning`() {
        val wake = resolveWakeTime(at("2026-11-03T22:00"), LocalTime.of(6, 15), zone, at("2026-11-04T15:00"))
        assertEquals(at("2026-11-04T06:15"), wake)
    }

    @Test
    fun `picked wake time later the same evening resolves to that evening`() {
        val wake = resolveWakeTime(at("2026-11-03T22:00"), LocalTime.of(23, 30), zone, at("2026-11-04T15:00"))
        assertEquals(at("2026-11-03T23:30"), wake)
    }

    @Test
    fun `picked wake time that has not happened yet is rejected`() {
        assertNull(resolveWakeTime(at("2026-11-03T22:00"), LocalTime.of(21, 0), zone, at("2026-11-04T15:00")))
    }

    @Test
    fun `picking exactly the bed time means a day later`() {
        assertNull(resolveWakeTime(at("2026-11-03T22:00"), LocalTime.of(22, 0), zone, at("2026-11-04T15:00")))
    }
}
