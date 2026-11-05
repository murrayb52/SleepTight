package io.github.murrayb52.sleeptight.scoring

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.murrayb52.sleeptight.data.Night
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.data.SleepStage
import io.github.murrayb52.sleeptight.data.SleepStageSegment
import io.github.murrayb52.sleeptight.data.SleepTightDatabase
import io.github.murrayb52.sleeptight.data.SoundSample
import io.github.murrayb52.sleeptight.data.StageSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class NightScorerTest {
    private lateinit var db: SleepTightDatabase
    private lateinit var scorer: NightScorer
    private val bed = Instant.parse("2026-11-03T20:00:00Z")
    private val wake = bed.plus(Duration.ofHours(8))

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SleepTightDatabase::class.java).build()
        scorer = NightScorer(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun closedNightWithSound(): Long {
        val id = db.nightDao().insert(Night(bedTapAt = bed.toEpochMilli(), wakeTapAt = wake.toEpochMilli(), zoneId = "Africa/Johannesburg"))
        // Restless for the first and last 15 minutes, quiet in between.
        val samples = (0 until 8 * 3600L).map { s ->
            val minute = s / 60
            val loud = (minute < 15 || minute >= 465) && s % 60 < 10
            SoundSample(id, bed.plusSeconds(s).toEpochMilli(), if (loud) 15f else 0f)
        }
        samples.chunked(5_000).forEach { db.soundSampleDao().insertAll(it) }
        return id
    }

    @Test
    fun soundAloneGivesAnEstimatedScoreAndStoresTheEstimate() = runTest {
        val id = closedNightWithSound()
        val cache = requireNotNull(scorer.rescore(id))
        assertEquals("ESTIMATED", cache.source)
        assertEquals(450L, cache.metrics.totalSleepMinutes)
        assertEquals(cache, ScoreCache.decode(db.nightDao().get(id)!!.scoreJson))
        val stored = db.sleepStageSegmentDao().getForNight(id)
        assertTrue(stored.isNotEmpty() && stored.all { it.source == StageSource.ESTIMATED })
    }

    @Test
    fun garminDataTakesOverWhenItArrives() = runTest {
        val id = closedNightWithSound()
        scorer.rescore(id)
        db.sleepStageSegmentDao().insertAll(
            listOf(
                SleepStageSegment(nightId = id, start = bed.plusSeconds(600).toEpochMilli(), end = bed.plusSeconds(4 * 3600).toEpochMilli(), stage = SleepStage.LIGHT, source = StageSource.GARMIN),
                SleepStageSegment(nightId = id, start = bed.plusSeconds(4 * 3600).toEpochMilli(), end = bed.plusSeconds(7 * 3600).toEpochMilli(), stage = SleepStage.DEEP, source = StageSource.GARMIN),
            ),
        )
        val cache = requireNotNull(scorer.rescore(id))
        assertEquals("GARMIN", cache.source)
        assertEquals(410L, cache.metrics.totalSleepMinutes)
    }

    @Test
    fun staleFindsUnscoredNightsAndGoalChangesRescore() = runTest {
        val id = closedNightWithSound()
        scorer.rescoreStale()
        val before = requireNotNull(ScoreCache.decode(db.nightDao().get(id)!!.scoreJson))
        db.settingsDao().upsert(Settings(targetSleepMinutes = 10 * 60))
        scorer.rescoreAll()
        val after = requireNotNull(ScoreCache.decode(db.nightDao().get(id)!!.scoreJson))
        assertTrue("a 10 h goal should lower the duration points", after.parts.first { it.component == "DURATION" }.points < before.parts.first { it.component == "DURATION" }.points)
    }

    @Test
    fun openNightsAreNotScored() = runTest {
        val id = db.nightDao().insert(Night(bedTapAt = bed.toEpochMilli(), zoneId = "Africa/Johannesburg"))
        assertEquals(null, scorer.rescore(id))
    }
}
