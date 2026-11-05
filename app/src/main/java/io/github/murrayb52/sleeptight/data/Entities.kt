package io.github.murrayb52.sleeptight.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// All instants are epoch milliseconds (UTC). Local-time rules use the night's zoneId.

/**
 * One night, bounded by the bed tap and the wake tap. A night with no [wakeTapAt] is open.
 *
 * [zoneId] is the device time zone at the bed tap, so local-time rules (bedtime goal, which date
 * the night belongs to) stay correct for past nights after travelling.
 */
@Entity(tableName = "nights", indices = [Index("bedTapAt"), Index("wakeTapAt")])
data class Night(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bedTapAt: Long,
    val wakeTapAt: Long? = null,
    val zoneId: String,
    val alarmWindowStart: Long? = null,
    val alarmWindowEnd: Long? = null,
    val alarmFiredAt: Long? = null,
    val snoozeCount: Int = 0,
    val garminImported: Boolean = false,
    /** Cached score breakdown. Derived data: it can always be recomputed from the rest. */
    val scoreJson: String? = null,
)

val Night.isOpen: Boolean get() = wakeTapAt == null

/** Relative sound level, roughly once a second. Never audio, only the level. */
@Entity(
    tableName = "sound_samples",
    primaryKeys = ["nightId", "timestamp"],
    foreignKeys = [
        ForeignKey(entity = Night::class, parentColumns = ["id"], childColumns = ["nightId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SoundSample(
    val nightId: Long,
    val timestamp: Long,
    /** dB relative to the rolling baseline. */
    val dbRelative: Float,
    // The second as measured, kept for later analysis (from database version 5, 2026-11-05; null in
    // older nights). All dBFS: relative to the loudest the mic can report, not calibrated dB SPL.
    /** RMS level of the whole second. */
    @ColumnInfo(defaultValue = "NULL") val dbfs: Float? = null,
    /** The loudest single sample in the second. */
    @ColumnInfo(defaultValue = "NULL") val peakDbfs: Float? = null,
    /** Level below 300 Hz (thumps, movement, snoring's rumble). */
    @ColumnInfo(defaultValue = "NULL") val lowDbfs: Float? = null,
    /** Level from 300 to 2,000 Hz (most of the voice range). */
    @ColumnInfo(defaultValue = "NULL") val midDbfs: Float? = null,
    /** Level above 2,000 Hz (rustling, hiss). */
    @ColumnInfo(defaultValue = "NULL") val highDbfs: Float? = null,
)

/** What the screen did during a night: a strong sign of being awake, for later analysis. */
enum class ScreenEventType { ON, OFF, UNLOCKED }

@Entity(
    tableName = "screen_events",
    indices = [Index("nightId")],
    foreignKeys = [
        ForeignKey(entity = Night::class, parentColumns = ["id"], childColumns = ["nightId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ScreenEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nightId: Long,
    val timestamp: Long,
    val type: ScreenEventType,
)

@Entity(
    tableName = "noise_events",
    indices = [Index("nightId")],
    foreignKeys = [
        ForeignKey(entity = Night::class, parentColumns = ["id"], childColumns = ["nightId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class NoiseEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nightId: Long,
    val start: Long,
    val end: Long,
    val peakDbRelative: Float,
)

enum class SleepStage { AWAKE, LIGHT, DEEP, REM, UNKNOWN }

enum class StageSource { GARMIN, ESTIMATED }

@Entity(
    tableName = "sleep_stage_segments",
    indices = [Index("nightId")],
    foreignKeys = [
        ForeignKey(entity = Night::class, parentColumns = ["id"], childColumns = ["nightId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SleepStageSegment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nightId: Long,
    val start: Long,
    val end: Long,
    val stage: SleepStage,
    val source: StageSource,
)

@Entity(
    tableName = "heart_rate_samples",
    primaryKeys = ["nightId", "timestamp"],
    foreignKeys = [
        ForeignKey(entity = Night::class, parentColumns = ["id"], childColumns = ["nightId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class HeartRateSample(
    val nightId: Long,
    val timestamp: Long,
    val bpm: Int,
)

enum class DrinkType { COFFEE, COKE, COKE_ZERO }

enum class CoffeeVenue { HOME, RESTAURANT }

/**
 * A logged drink. The variant fields that apply depend on [drinkType]: coffee uses [decaf],
 * [venue] and [shots]; soft drinks use [volumeMl]. [mg] is resolved from the defaults in
 * [Settings] when the drink is logged, and can be edited afterwards.
 */
@Entity(tableName = "caffeine_entries", indices = [Index("timestamp")])
data class CaffeineEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val drinkType: DrinkType,
    val decaf: Boolean? = null,
    val venue: CoffeeVenue? = null,
    val shots: Int? = null,
    val volumeMl: Int? = null,
    val mg: Double,
)

// ---- The sleep diary (database version 6, 2026-11-05): labels and context for the sound model.

/** When the last 30+ minutes of screen time ended, before the bed tap. */
enum class LastScreens { JUST_NOW, UNDER_1H, H1_TO_2, OVER_2H }

/** What was true at the bed tap, from the "Tonight" sheet. One row per night. */
@Entity(
    tableName = "night_context",
    foreignKeys = [
        ForeignKey(entity = Night::class, parentColumns = ["id"], childColumns = ["nightId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class NightContext(
    @PrimaryKey val nightId: Long,
    val earplugs: Boolean,
    val alcohol: Boolean = false,
    val partner: Boolean = false,
    val differentBed: Boolean = false,
    val readingBeforeBed: Boolean = false,
    val lastScreens: LastScreens? = null,
)

/** Why you woke, picked on the wake note. */
enum class WakeReason { TOILET, WATER, NOISE, TOO_HOT, TOO_COLD, MIND_RACING, EARPLUGS, CHECKING_TIME, OTHER }

/**
 * A waking you noted during the night. [at] is the screen-on that prompted it (or the moment you
 * opened the note yourself), [notedAt] when you saved it.
 */
@Entity(
    tableName = "wake_notes",
    indices = [Index("nightId")],
    foreignKeys = [
        ForeignKey(entity = Night::class, parentColumns = ["id"], childColumns = ["nightId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class WakeNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nightId: Long,
    val at: Long,
    val notedAt: Long,
    /** Comma-separated [WakeReason] names; see [reasonList]. */
    val reasons: String,
    val note: String? = null,
) {
    val reasonList: List<WakeReason>
        get() = reasons.split(',').mapNotNull { name -> WakeReason.entries.firstOrNull { it.name == name.trim() } }

    companion object {
        fun encode(reasons: Collection<WakeReason>) = reasons.sortedBy { it.ordinal }.joinToString(",") { it.name }
    }
}

/** "How did you sleep?", answered after I'm up. One row per night. */
@Entity(
    tableName = "morning_surveys",
    foreignKeys = [
        ForeignKey(entity = Night::class, parentColumns = ["id"], childColumns = ["nightId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class MorningSurvey(
    @PrimaryKey val nightId: Long,
    val answeredAt: Long,
    /** 1–10. */
    val rested: Int,
    /** 1–10. */
    val quality: Int,
    /** How many times you think you woke. */
    val wakings: Int,
    val minutesToFallAsleep: Int,
    /** In bed before trying to sleep (reading, phone…). */
    val minutesBeforeTrying: Int,
    /** Only asked on earplug nights. */
    val earplugAdjustments: Int? = null,
    val notes: String? = null,
)

/**
 * User settings, stored as a single row (id 0). The defaults here are the app's defaults.
 * Times of day are minutes after local midnight.
 */
@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val targetBedtimeMinutes: Int = 22 * 60,
    val targetSleepMinutes: Int = 8 * 60,
    val maxSnoozes: Int = 1,
    /** Unused since 2026-11-04: every bed tap arms the alarm. Kept so old data reads. Added in schema version 2. */
    @ColumnInfo(defaultValue = "1") val alarmEnabled: Boolean = true,
    /** A notification 30 minutes before the bedtime goal. Added in schema version 3. */
    @ColumnInfo(defaultValue = "1") val bedtimeReminderEnabled: Boolean = true,
    /** The bedside check (charger, nightstand, mic) at the bed tap. Added in schema version 4. */
    @ColumnInfo(defaultValue = "1") val bedsideCheckEnabled: Boolean = true,
    /** Whether "Earplugs in" starts switched on at the bed tap. Added in schema version 6. */
    @ColumnInfo(defaultValue = "1") val earplugsDefault: Boolean = true,
    val alarmWindowStartMinutes: Int = 5 * 60 + 45,
    val alarmWindowEndMinutes: Int = 6 * 60 + 15,
    /** Unused since 2026-11-04: snooze lengths are fixed (see SnoozeLadder). Kept so old data reads. */
    val snoozeMinutes: Int = 9,
    /** Alarm sound resource name; null means the generated chime. */
    val alarmSound: String? = null,
    val espressoHomeMg: Double = 63.0,
    val espressoRestaurantMg: Double = 75.0,
    val decafShotMg: Double = 3.0,
    val colaMgPer100Ml: Double = 10.0,
    val caffeineHalfLifeHours: Double = 5.0,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
