package io.github.murrayb52.sleeptight.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NightDao {
    @Insert
    suspend fun insert(night: Night): Long

    @Update
    suspend fun update(night: Night)

    @Delete
    suspend fun delete(night: Night)

    @Query("SELECT * FROM nights WHERE id = :id")
    suspend fun get(id: Long): Night?

    @Query("SELECT * FROM nights WHERE id = :id")
    fun observe(id: Long): Flow<Night?>

    @Query("SELECT * FROM nights WHERE wakeTapAt IS NULL ORDER BY bedTapAt DESC LIMIT 1")
    suspend fun getOpenNight(): Night?

    @Query("SELECT * FROM nights WHERE wakeTapAt IS NULL ORDER BY bedTapAt DESC LIMIT 1")
    fun observeOpenNight(): Flow<Night?>

    @Query("SELECT * FROM nights WHERE wakeTapAt IS NOT NULL ORDER BY bedTapAt DESC LIMIT 1")
    fun observeLatestClosedNight(): Flow<Night?>

    /** Closed nights whose bed tap falls in [fromBed, toBed), oldest first. */
    @Query(
        "SELECT * FROM nights WHERE wakeTapAt IS NOT NULL AND bedTapAt >= :fromBed AND bedTapAt < :toBed " +
            "ORDER BY bedTapAt",
    )
    suspend fun getClosedNightsBetween(fromBed: Long, toBed: Long): List<Night>

    @Query("SELECT * FROM nights ORDER BY bedTapAt DESC")
    fun observeAll(): Flow<List<Night>>

    @Query("SELECT * FROM nights WHERE wakeTapAt IS NOT NULL ORDER BY bedTapAt DESC")
    fun observeClosed(): Flow<List<Night>>

    @Query("UPDATE nights SET scoreJson = :json WHERE id = :id")
    suspend fun setScoreJson(id: Long, json: String?)

    /** Closed nights with no cached score, or one from an older scoring version. */
    @Query("SELECT id FROM nights WHERE wakeTapAt IS NOT NULL AND (scoreJson IS NULL OR scoreJson NOT LIKE :versionPrefix || '%')")
    suspend fun closedNightIdsNeedingScore(versionPrefix: String): List<Long>

    @Query("SELECT id FROM nights WHERE wakeTapAt IS NOT NULL")
    suspend fun closedNightIds(): List<Long>

    @Query("UPDATE nights SET garminImported = :imported WHERE id = :id")
    suspend fun setGarminImported(id: Long, imported: Boolean)

    /** Closed nights that ended after [sinceWake] and have no Garmin data yet, newest first. */
    @Query("SELECT id FROM nights WHERE wakeTapAt IS NOT NULL AND wakeTapAt >= :sinceWake AND garminImported = 0 ORDER BY wakeTapAt DESC")
    suspend fun closedNightIdsAwaitingGarmin(sinceWake: Long): List<Long>

    @Query("UPDATE nights SET alarmWindowStart = :start, alarmWindowEnd = :end WHERE id = :id")
    suspend fun setAlarmWindow(id: Long, start: Long?, end: Long?)

    /** Records when the alarm first rang; later rings (after snoozes) don't overwrite it. */
    @Query("UPDATE nights SET alarmFiredAt = :at WHERE id = :id AND alarmFiredAt IS NULL")
    suspend fun markAlarmFired(id: Long, at: Long)

    @Query("UPDATE nights SET snoozeCount = snoozeCount + 1 WHERE id = :id")
    suspend fun addSnooze(id: Long)

    /** Sets the wake tap only if the night is still open. Returns the number of rows changed. */
    @Query("UPDATE nights SET wakeTapAt = :wakeTapAt WHERE id = :id AND wakeTapAt IS NULL")
    suspend fun close(id: Long, wakeTapAt: Long): Int
}

@Dao
interface SoundSampleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(samples: List<SoundSample>)

    @Query("SELECT * FROM sound_samples WHERE nightId = :nightId ORDER BY timestamp")
    suspend fun getForNight(nightId: Long): List<SoundSample>

    @Query("SELECT * FROM sound_samples WHERE nightId = :nightId AND timestamp >= :since ORDER BY timestamp")
    fun observeForNightSince(nightId: Long, since: Long): Flow<List<SoundSample>>
}

/** How many noise events a night has. */
data class NightNoiseCount(val nightId: Long, val count: Int)

@Dao
interface NoiseEventDao {
    @Query("SELECT nightId, COUNT(*) AS count FROM noise_events GROUP BY nightId")
    fun observeCounts(): Flow<List<NightNoiseCount>>

    @Insert
    suspend fun insert(event: NoiseEvent): Long

    @Update
    suspend fun update(event: NoiseEvent)

    @Query("SELECT * FROM noise_events WHERE nightId = :nightId ORDER BY start")
    suspend fun getForNight(nightId: Long): List<NoiseEvent>

    @Query("SELECT * FROM noise_events WHERE nightId = :nightId ORDER BY start")
    fun observeForNight(nightId: Long): Flow<List<NoiseEvent>>
}

@Dao
interface SleepStageSegmentDao {
    @Insert
    suspend fun insertAll(segments: List<SleepStageSegment>)

    @Query("DELETE FROM sleep_stage_segments WHERE nightId = :nightId")
    suspend fun deleteForNight(nightId: Long)

    @Query("SELECT * FROM sleep_stage_segments WHERE nightId = :nightId ORDER BY start")
    suspend fun getForNight(nightId: Long): List<SleepStageSegment>

    @Query("SELECT * FROM sleep_stage_segments WHERE nightId = :nightId ORDER BY start")
    fun observeForNight(nightId: Long): Flow<List<SleepStageSegment>>

    @Query("DELETE FROM sleep_stage_segments WHERE nightId = :nightId AND source = :source")
    suspend fun deleteForNight(nightId: Long, source: StageSource)
}

@Dao
interface HeartRateSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<HeartRateSample>)

    @Query("DELETE FROM heart_rate_samples WHERE nightId = :nightId")
    suspend fun deleteForNight(nightId: Long)

    @Query("SELECT * FROM heart_rate_samples WHERE nightId = :nightId ORDER BY timestamp")
    suspend fun getForNight(nightId: Long): List<HeartRateSample>

    @Query("SELECT * FROM heart_rate_samples WHERE nightId = :nightId ORDER BY timestamp")
    fun observeForNight(nightId: Long): Flow<List<HeartRateSample>>
}

@Dao
interface CaffeineEntryDao {
    @Insert
    suspend fun insert(entry: CaffeineEntry): Long

    @Update
    suspend fun update(entry: CaffeineEntry)

    @Delete
    suspend fun delete(entry: CaffeineEntry)

    @Query("SELECT * FROM caffeine_entries WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp")
    suspend fun getBetween(from: Long, to: Long): List<CaffeineEntry>

    @Query("SELECT * FROM caffeine_entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CaffeineEntry>>
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = ${Settings.SINGLETON_ID}")
    suspend fun get(): Settings?

    @Query("SELECT * FROM settings WHERE id = ${Settings.SINGLETON_ID}")
    fun observe(): Flow<Settings?>

    @Upsert
    suspend fun upsert(settings: Settings)
}
