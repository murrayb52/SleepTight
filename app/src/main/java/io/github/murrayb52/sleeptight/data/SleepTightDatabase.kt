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
        ScreenEvent::class,
        CaffeineEntry::class,
        Settings::class,
        NightContext::class,
        WakeNote::class,
        MorningSurvey::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        // 1 → 2: Settings.alarmEnabled (defaults to on).
        AutoMigration(from = 1, to = 2),
        // 2 → 3: Settings.bedtimeReminderEnabled (defaults to on).
        AutoMigration(from = 2, to = 3),
        // 3 → 4: Settings.bedsideCheckEnabled (defaults to on).
        AutoMigration(from = 3, to = 4),
        // 4 → 5: measured level, peak and band levels on sound_samples (null for older nights), and
        // screen_events. Added for the sound-model investigation (2026-11-05).
        AutoMigration(from = 4, to = 5),
        // 5 → 6: the sleep diary (night_context, wake_notes, morning_surveys) and
        // Settings.earplugsDefault (defaults to on). 2026-11-05.
        AutoMigration(from = 5, to = 6),
        // 6 → 7: Night.appVersion, the build that recorded the night (null for older nights). 2026-11-06.
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class SleepTightDatabase : RoomDatabase() {
    abstract fun nightDao(): NightDao
    abstract fun soundSampleDao(): SoundSampleDao
    abstract fun noiseEventDao(): NoiseEventDao
    abstract fun sleepStageSegmentDao(): SleepStageSegmentDao
    abstract fun heartRateSampleDao(): HeartRateSampleDao
    abstract fun screenEventDao(): ScreenEventDao
    abstract fun caffeineEntryDao(): CaffeineEntryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun diaryDao(): DiaryDao

    companion object {
        private const val NAME = "sleeptight.db"

        // No destructive migration fallback: the database holds the user's recorded nights, so a
        // schema change must ship a real Migration (schemas are exported to app/schemas/).
        fun create(context: Context): SleepTightDatabase =
            Room.databaseBuilder(context, SleepTightDatabase::class.java, NAME).build()
    }
}
