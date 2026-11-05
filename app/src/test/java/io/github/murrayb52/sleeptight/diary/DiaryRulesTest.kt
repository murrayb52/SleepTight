package io.github.murrayb52.sleeptight.diary

import io.github.murrayb52.sleeptight.data.CoffeeVenue
import io.github.murrayb52.sleeptight.data.DrinkType
import io.github.murrayb52.sleeptight.data.Night
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.data.WakeNote
import io.github.murrayb52.sleeptight.data.WakeReason
import io.github.murrayb52.sleeptight.ui.diary.surveyNightFor
import io.github.murrayb52.sleeptight.ui.home.caffeineTarget
import io.github.murrayb52.sleeptight.ui.home.caffeineUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class DiaryRulesTest {
    private val zone = ZoneId.of("Africa/Johannesburg")
    private fun t(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()
    private val min = 60_000L

    @Test
    fun `the screen asks for a wake note only after settling, not while the alarm rings, and not twice in 10 minutes`() {
        val bed = 0L
        assertFalse(WakePromptRule.shouldPrompt(10 * min, bed, alarmIdle = true, lastPromptAt = 0))
        assertTrue(WakePromptRule.shouldPrompt(15 * min, bed, alarmIdle = true, lastPromptAt = 0))
        assertFalse(WakePromptRule.shouldPrompt(60 * min, bed, alarmIdle = false, lastPromptAt = 0))
        assertFalse(WakePromptRule.shouldPrompt(60 * min, bed, alarmIdle = true, lastPromptAt = 55 * min))
        assertTrue(WakePromptRule.shouldPrompt(65 * min, bed, alarmIdle = true, lastPromptAt = 55 * min))
        assertTrue(WakePromptRule.stillFresh(0, 14 * min))
        assertFalse(WakePromptRule.stillFresh(0, 15 * min))
    }

    @Test
    fun `wake reasons round-trip through the stored text`() {
        val note = WakeNote(nightId = 1, at = 0, notedAt = 0, reasons = WakeNote.encode(setOf(WakeReason.WATER, WakeReason.TOILET)))
        assertEquals("TOILET,WATER", note.reasons)
        assertEquals(listOf(WakeReason.TOILET, WakeReason.WATER), note.reasonList)
        assertEquals(emptyList<WakeReason>(), note.copy(reasons = "").reasonList)
    }

    @Test
    fun `drinks take their caffeine from the settings`() {
        val s = Settings()
        val at = Instant.EPOCH
        assertEquals(126.0, caffeineEntry(DrinkChoice.COFFEE, at, s, CoffeeVenue.HOME, shots = 2).mg, 1e-9)
        assertEquals(75.0, caffeineEntry(DrinkChoice.COFFEE, at, s, CoffeeVenue.RESTAURANT).mg, 1e-9)
        val decaf = caffeineEntry(DrinkChoice.DECAF, at, s, CoffeeVenue.RESTAURANT, shots = 2)
        assertEquals(6.0, decaf.mg, 1e-9)
        assertEquals(true, decaf.decaf)
        val coke = caffeineEntry(DrinkChoice.COKE_ZERO, at, s, volumeMl = 500)
        assertEquals(DrinkType.COKE_ZERO, coke.drinkType)
        assertEquals(50.0, coke.mg, 1e-9)
    }

    @Test
    fun `caffeine is projected to the coming bedtime, or now when up late`() {
        val ten = LocalTime.of(22, 0)
        assertEquals(t("2026-11-05T22:00"), caffeineTarget(t("2026-11-05T14:00"), zone, ten))
        assertEquals(t("2026-11-05T23:30"), caffeineTarget(t("2026-11-05T23:30"), zone, ten))
        assertEquals(t("2026-11-06T01:00"), caffeineTarget(t("2026-11-06T01:00"), zone, ten))
        assertEquals(t("2026-11-06T22:00"), caffeineTarget(t("2026-11-06T05:00"), zone, ten))
    }

    @Test
    fun `a 17h00 double espresso leaves about half by 22h00`() {
        val drink = caffeineEntry(DrinkChoice.COFFEE, t("2026-11-05T17:00"), Settings(), shots = 2)
        val ui = caffeineUi(listOf(drink), Settings(), t("2026-11-05T18:00"), zone)
        assertEquals(63.0, ui.byBedtimeMg, 1e-6)
        assertEquals(1, ui.recent.size)
    }

    @Test
    fun `the survey is offered for the latest night for 12 hours, unless put off or in bed`() {
        val wake = t("2026-11-05T06:00").toEpochMilli()
        val night = Night(id = 7, bedTapAt = wake - 8 * 60 * min, wakeTapAt = wake, zoneId = zone.id)
        assertEquals(7L, surveyNightFor(night, null, emptySet(), wake + 60 * min))
        assertNull(surveyNightFor(night, null, setOf(7L), wake + 60 * min))
        assertNull(surveyNightFor(night, null, emptySet(), wake + 13 * 60 * min))
        assertNull(surveyNightFor(night, night.copy(id = 8, wakeTapAt = null), emptySet(), wake + 60 * min))
        assertNull(surveyNightFor(null, null, emptySet(), wake))
    }
}
