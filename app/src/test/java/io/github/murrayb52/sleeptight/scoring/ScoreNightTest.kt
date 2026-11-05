package io.github.murrayb52.sleeptight.scoring

import io.github.murrayb52.sleeptight.data.Night
import io.github.murrayb52.sleeptight.data.NoiseEvent
import io.github.murrayb52.sleeptight.data.SleepStage
import io.github.murrayb52.sleeptight.data.SleepStageSegment
import io.github.murrayb52.sleeptight.data.StageSource
import io.github.murrayb52.sleeptight.metrics.Goals
import io.github.murrayb52.sleeptight.metrics.LevelSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ScoreNightTest {
    private val zone = ZoneId.of("Africa/Johannesburg")
    private fun t(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()
    private val bed = t("2026-11-03T22:00")
    private val wake = t("2026-11-04T06:00")
    private val night = Night(id = 5, bedTapAt = bed.toEpochMilli(), wakeTapAt = wake.toEpochMilli(), zoneId = zone.id, snoozeCount = 1)

    /** Per-second levels: [restless] minutes at the start and end, quiet in between. */
    private fun sound(restless: Long = 15): List<LevelSample> {
        val seconds = Duration.between(bed, wake).seconds
        return (0 until seconds).map { s ->
            val minute = s / 60
            val loud = minute < restless || minute >= seconds / 60 - restless
            LevelSample(bed.plusSeconds(s), if (loud && s % 60 < 10) 15.0 else 0.0)
        }
    }

    private fun garmin(stage: SleepStage, from: String, to: String) =
        SleepStageSegment(nightId = 5, start = t(from).toEpochMilli(), end = t(to).toEpochMilli(), stage = stage, source = StageSource.GARMIN)

    @Test
    fun `without Garmin the night is estimated from sound and the estimate is returned to store`() {
        val scored = scoreNight(night, emptyList(), emptyList(), emptyList(), Goals(), sound())
        val cache = scored.cache
        assertEquals("ESTIMATED", cache.source)
        assertTrue(cache.deepRemRedistributed)
        assertEquals(450L, cache.metrics.totalSleepMinutes)
        assertNotNull(cache.total)
        val segs = requireNotNull(scored.estimatedSegments)
        assertTrue(segs.all { it.source == StageSource.ESTIMATED && it.nightId == 5L })
        assertEquals(listOf(SleepStage.AWAKE, SleepStage.UNKNOWN, SleepStage.AWAKE), segs.map { it.stage })
    }

    @Test
    fun `Garmin segments win and nothing is estimated`() {
        val segments = listOf(
            garmin(SleepStage.LIGHT, "2026-11-03T22:10", "2026-11-04T01:00"),
            garmin(SleepStage.DEEP, "2026-11-04T01:00", "2026-11-04T03:00"),
            garmin(SleepStage.REM, "2026-11-04T03:00", "2026-11-04T05:50"),
        )
        val scored = scoreNight(night, segments, emptyList(), emptyList(), Goals(), sound = emptyList())
        assertEquals("GARMIN", scored.cache.source)
        assertNull(scored.estimatedSegments)
        assertEquals(460L, scored.cache.metrics.totalSleepMinutes)
        assertEquals(5, scored.cache.parts.size)
    }

    @Test
    fun `no Garmin and no sound means no score, but time in bed is kept`() {
        val scored = scoreNight(night, emptyList(), emptyList(), emptyList(), Goals(), sound = emptyList())
        assertNull(scored.cache.total)
        assertNull(scored.cache.source)
        assertEquals(480L, scored.cache.metrics.timeInBedMinutes)
        assertTrue(scored.estimatedSegments!!.isEmpty())
    }

    @Test
    fun `noise events and snoozes feed the score`() {
        val noise = listOf(NoiseEvent(nightId = 5, start = t("2026-11-04T01:00").toEpochMilli(), end = t("2026-11-04T01:00").toEpochMilli() + 3000, peakDbRelative = 20f))
        val cache = scoreNight(night, emptyList(), noise, emptyList(), Goals(maxSnoozes = 0), sound()).cache
        assertEquals(1, cache.metrics.noiseClusters)
        assertEquals(0.5, cache.regularity.snoozes, 1e-9)
    }

    @Test
    fun `the cache round-trips and old versions are ignored`() {
        val cache = scoreNight(night, emptyList(), emptyList(), emptyList(), Goals(), sound()).cache
        val json = cache.encode()
        assertTrue(json.startsWith(ScoreCache.PREFIX))
        assertEquals(cache, ScoreCache.decode(json))
        assertNull(ScoreCache.decode(json.replace("\"version\":${ScoreCache.VERSION}", "\"version\":0")))
        assertNull(ScoreCache.decode("not json"))
    }
}
