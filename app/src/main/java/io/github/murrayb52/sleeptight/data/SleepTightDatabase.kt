package io.github.murrayb52.sleeptight.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Night::class,
        SoundSample::class,
        NoiseEvent::class,
        SleepStageSegment::class,
        HeartRateSample::class,
        CaffeineEntry::class,
        Settings::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // 1 → 2: Settings.alarmEnabled (defaults to on).
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class SleepTightDatabase : RoomDatabase() {
    abstract fun nightDao(): NightDao
    abstract fun soundSampleDao(): SoundSampleDao
    abstract fun noiseEventDao(): NoiseEventDao
    abstract fun sleepStageSegmentDao(): SleepStageSegmentDao
    abstract fun heartRateSampleDao(): HeartRateSampleDao
    abstract fun caffeineEntryDao(): CaffeineEntryDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        private const val NAME = "sleeptight.db"

        // No destructive migration fallback: the database holds the user's recorded nights, so a
        // schema change must ship a real Migration (schemas are exported to app/schemas/).
        fun create(context: Context): SleepTightDatabase =
            Room.databaseBuilder(context, SleepTightDatabase::class.java, NAME).build()
    }
}
