package io.github.murrayb52.sleeptight.health

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.murrayb52.sleeptight.data.Night
import io.github.murrayb52.sleeptight.data.SleepTightDatabase
import io.github.murrayb52.sleeptight.data.SleepStage
import io.github.murrayb52.sleeptight.data.StageSource
import io.github.murrayb52.sleeptight.metrics.SleepSegment
import io.github.murrayb52.sleeptight.metrics.Stage
import io.github.murrayb52.sleeptight.scoring.NightScorer
import io.github.murrayb52.sleeptight.scoring.ScoreCache
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

private class FakeGarmin(
    var status: SourceStatus = SourceStatus.READY,
    var segments: List<SleepSegment> = emptyList(),
    var heartRate: List<HeartRatePoint> = emptyList(),
) : GarminSource {
    var reads = 0
    override suspend fun status() = status
    override suspend fun sleep(from: Instant, to: Instant): List<SleepSegment> {
        reads++
        return segments.filter { it.end.isAfter(from) && it.start.isBefore(to) }
    }
    override suspend fun heartRate(from: Instant, to: Instant) = heartRate
}

@RunWith(AndroidJUnit4::class)
class GarminImporterTest {
    private lateinit var db: SleepTightDatabase
    private val bed = Instant.parse("2026-11-03T20:00:00Z")
    private val wake = bed.plus(Duration.ofHours(8))
    private val now = wake.plus(Duration.ofHours(1))
    private val garmin = FakeGarmin()
    private lateinit var importer: GarminImporter

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SleepTightDatabase::class.java).build()
        importer = GarminImporter(db, garmin, NightScorer(db), Clock.fixed(now, ZoneOffset.UTC))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun night() = db.nightDao().insert(Night(bedTapAt = bed.toEpochMilli(), wakeTapAt = wake.toEpochMilli(), zoneId = "Africa/Johannesburg"))

    @Test
    fun importsTrimsStoresAndRescores() = runTest {
        val id = night()
        garmin.segments = listOf(
            // Garmin thinks sleep began 20 minutes before the bed tap and ended 15 after the wake tap.
            SleepSegment(bed.minus(Duration.ofMinutes(20)), bed.plus(Duration.ofHours(3)), Stage.LIGHT),
            SleepSegment(bed.plus(Duration.ofHours(3)), bed.plus(Duration.ofHours(5)), Stage.DEEP),
            SleepSegment(bed.plus(Duration.ofHours(5)), wake.plus(Duration.ofMinutes(15)), Stage.REM),
        )
        garmin.heartRate = listOf(HeartRatePoint(bed.plus(Duration.ofHours(1)), 52), HeartRatePoint(wake.plus(Duration.ofHours(1)), 70))

        assertEquals(GarminImporter.Result.IMPORTED, importer.import(id))

        val stored = db.sleepStageSegmentDao().getForNight(id).filter { it.source == StageSource.GARMIN }
        assertEquals(bed.toEpochMilli(), stored.first().start) // trimmed to the taps
        assertEquals(wake.toEpochMilli(), stored.last().end)
        assertEquals(listOf(52), db.heartRateSampleDao().getForNight(id).map { it.bpm }) // only inside the night
        val night = db.nightDao().get(id)!!
        assertTrue(night.garminImported)
        assertEquals("GARMIN", ScoreCache.decode(night.scoreJson)?.source)
    }

    @Test
    fun nothingFromGarminYetMeansNoData() = runTest {
        val id = night()
        assertEquals(GarminImporter.Result.NO_DATA, importer.import(id))
        assertTrue(!db.nightDao().get(id)!!.garminImported)
    }

    @Test
    fun withoutAccessNothingIsRead() = runTest {
        val id = night()
        garmin.status = SourceStatus.NEEDS_PERMISSION
        assertEquals(GarminImporter.Result.NOT_CONNECTED, importer.import(id))
        assertEquals(0, garmin.reads)
    }

    @Test
    fun recentNightsAreImportedAndAnUnchangedNightIsLeftAlone() = runTest {
        val id = night()
        garmin.segments = listOf(SleepSegment(bed.plus(Duration.ofMinutes(10)), wake, Stage.LIGHT))
        assertTrue(importer.importRecent().isEmpty()) // a first import isn't an update
        assertTrue(db.nightDao().get(id)!!.garminImported)
        assertEquals(GarminImporter.Result.UNCHANGED, importer.import(id))
        assertTrue(importer.importRecent().isEmpty())
    }

    @Test
    fun aNightGarminRewritesIsReplacedAndReported() = runTest {
        val id = night()
        garmin.segments = listOf(SleepSegment(bed.plus(Duration.ofMinutes(10)), wake, Stage.LIGHT))
        importer.import(id)
        val before = ScoreCache.decode(db.nightDao().get(id)!!.scoreJson)?.total

        // Garmin later splits the night into light and deep sleep.
        garmin.segments = listOf(
            SleepSegment(bed.plus(Duration.ofMinutes(10)), bed.plus(Duration.ofHours(3)), Stage.LIGHT),
            SleepSegment(bed.plus(Duration.ofHours(3)), wake, Stage.DEEP),
        )
        val updates = importer.importRecent()

        assertEquals(listOf(id), updates.map { it.nightId })
        assertEquals(before, updates.single().scoreBefore)
        assertEquals(ScoreCache.decode(db.nightDao().get(id)!!.scoreJson)?.total, updates.single().scoreAfter)
        val stages = db.sleepStageSegmentDao().getForNight(id).filter { it.source == StageSource.GARMIN }.map { it.stage }
        assertEquals(listOf(SleepStage.LIGHT, SleepStage.DEEP), stages)
    }
}
