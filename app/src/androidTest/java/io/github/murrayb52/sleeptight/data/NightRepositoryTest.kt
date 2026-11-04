package io.github.murrayb52.sleeptight.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test can move forward. */
private class MutableClock(var now: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
    fun advance(by: Duration) {
        now = now.plus(by)
    }
}

@RunWith(AndroidJUnit4::class)
class NightRepositoryTest {
    private lateinit var db: SleepTightDatabase
    private lateinit var clock: MutableClock
    private lateinit var repo: NightRepository
    private val zone = ZoneId.of("Africa/Johannesburg")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SleepTightDatabase::class.java)
            .build()
        clock = MutableClock(Instant.parse("2026-11-03T20:00:00Z"), zone)
        repo = NightRepository(db, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun goToBedOpensANightAtNowInTheDeviceZone() = runTest {
        val night = repo.goToBed()
        assertEquals(clock.now.toEpochMilli(), night.bedTapAt)
        assertEquals(zone.id, night.zoneId)
        assertTrue(night.isOpen)
        assertEquals(night, repo.openNight.first())
    }

    @Test
    fun doubleBedTapReturnsTheSameNight() = runTest {
        val first = repo.goToBed()
        clock.advance(Duration.ofSeconds(2))
        val second = repo.goToBed()
        assertEquals(first, second)
        assertEquals(1, db.nightDao().observeAll().first().size)
    }

    @Test
    fun wakeUpClosesTheOpenNight() = runTest {
        val bed = repo.goToBed()
        clock.advance(Duration.ofHours(8))
        assertNotNull(repo.wakeUp())
        assertNull(repo.openNight.first())
        val last = repo.latestClosedNight.first()!!
        assertEquals(bed.id, last.id)
        assertEquals(clock.now.toEpochMilli(), last.wakeTapAt)
    }

    @Test
    fun wakeUpWithNoOpenNightDoesNothing() = runTest {
        assertNull(repo.wakeUp())
    }

    @Test(expected = IllegalArgumentException::class)
    fun wakeUpBeforeBedTapIsRejected() = runTest {
        repo.goToBed()
        repo.wakeUp(clock.now.minusSeconds(60))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wakeUpInTheFutureIsRejected() = runTest {
        repo.goToBed()
        repo.wakeUp(clock.now.plusSeconds(60))
    }

    @Test
    fun aNewNightCanStartAfterTheLastOneClosed() = runTest {
        val first = repo.goToBed()
        clock.advance(Duration.ofHours(8))
        repo.wakeUp()
        clock.advance(Duration.ofHours(16))
        val second = repo.goToBed()
        assertTrue(second.id != first.id)
        assertEquals(second, repo.openNight.first())
    }

    @Test
    fun settingsDefaultUntilSaved() = runTest {
        assertEquals(Settings(), repo.settings.first())
        repo.updateSettings(Settings(targetSleepMinutes = 450))
        assertEquals(450, repo.settings.first().targetSleepMinutes)
    }

    @Test
    fun deletingANightDeletesEverythingRecordedInIt() = runTest {
        val night = repo.goToBed()
        val t = night.bedTapAt
        db.soundSampleDao().insertAll(listOf(SoundSample(night.id, t + 1_000, 3f)))
        db.noiseEventDao().insert(NoiseEvent(nightId = night.id, start = t + 1_000, end = t + 3_000, peakDbRelative = 20f))
        db.sleepStageSegmentDao().insertAll(
            listOf(SleepStageSegment(nightId = night.id, start = t, end = t + 60_000, stage = SleepStage.LIGHT, source = StageSource.GARMIN)),
        )
        db.heartRateSampleDao().insertAll(listOf(HeartRateSample(night.id, t + 1_000, 55)))

        repo.deleteNight(night)

        assertTrue(db.soundSampleDao().getForNight(night.id).isEmpty())
        assertTrue(db.noiseEventDao().getForNight(night.id).isEmpty())
        assertTrue(db.sleepStageSegmentDao().getForNight(night.id).isEmpty())
        assertTrue(db.heartRateSampleDao().getForNight(night.id).isEmpty())
    }

    @Test
    fun caffeineEntriesRoundTrip() = runTest {
        val dao = db.caffeineEntryDao()
        val t = clock.now.toEpochMilli()
        val id = dao.insert(
            CaffeineEntry(timestamp = t, drinkType = DrinkType.COFFEE, decaf = false, venue = CoffeeVenue.HOME, shots = 2, mg = 126.0),
        )
        val saved = dao.getBetween(t, t + 1).single()
        assertEquals(id, saved.id)
        assertEquals(CoffeeVenue.HOME, saved.venue)
        assertEquals(126.0, saved.mg, 0.0)
    }

    @Test
    fun closedNightsBetweenFiltersByBedTap() = runTest {
        val base = Instant.parse("2026-11-01T20:00:00Z").atOffset(ZoneOffset.UTC).toInstant()
        val dao = db.nightDao()
        repeat(3) { i ->
            val bed = base.plus(Duration.ofDays(i.toLong())).toEpochMilli()
            dao.insert(Night(bedTapAt = bed, wakeTapAt = bed + 8 * 3_600_000, zoneId = zone.id))
        }
        val from = base.plus(Duration.ofDays(1)).toEpochMilli()
        val nights = dao.getClosedNightsBetween(from, from + Duration.ofDays(5).toMillis())
        assertEquals(2, nights.size)
        assertEquals(from, nights.first().bedTapAt)
    }
}
