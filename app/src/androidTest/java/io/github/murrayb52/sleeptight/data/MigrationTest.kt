package io.github.murrayb52.sleeptight.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every schema change must keep recorded nights. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SleepTightDatabase::class.java)

    @Test
    fun version1To2KeepsNightsAndTurnsTheAlarmOn() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO nights (id, bedTapAt, wakeTapAt, zoneId, snoozeCount, garminImported) " +
                    "VALUES (1, 1000, 2000, 'Africa/Johannesburg', 2, 0)",
            )
            db.execSQL("INSERT INTO sound_samples (nightId, timestamp, dbRelative) VALUES (1, 1500, 3.5)")
            db.execSQL(
                "INSERT INTO settings (id, targetBedtimeMinutes, targetSleepMinutes, maxSnoozes, alarmWindowStartMinutes, " +
                    "alarmWindowEndMinutes, snoozeMinutes, alarmSound, espressoHomeMg, espressoRestaurantMg, decafShotMg, " +
                    "colaMgPer100Ml, caffeineHalfLifeHours) VALUES (0, 1350, 450, 1, 345, 375, 9, NULL, 63, 75, 3, 10, 5)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 2, true).use { db ->
            db.query("SELECT bedTapAt, wakeTapAt, snoozeCount FROM nights WHERE id = 1").use { c ->
                c.moveToFirst()
                assertEquals(1000L, c.getLong(0))
                assertEquals(2000L, c.getLong(1))
                assertEquals(2, c.getInt(2))
            }
            db.query("SELECT COUNT(*) FROM sound_samples").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
            db.query("SELECT targetBedtimeMinutes, alarmEnabled FROM settings").use { c ->
                c.moveToFirst()
                assertEquals(1350, c.getInt(0)) // the user's own setting survives
                assertEquals(1, c.getInt(1)) // the new column defaults to on
            }
        }
    }

    @Test
    fun version2To3KeepsSettingsAndTurnsTheBedtimeReminderOn() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL(
                "INSERT INTO nights (id, bedTapAt, wakeTapAt, zoneId, snoozeCount, garminImported) " +
                    "VALUES (1, 1000, 2000, 'Africa/Johannesburg', 1, 0)",
            )
            db.execSQL(
                "INSERT INTO settings (id, targetBedtimeMinutes, targetSleepMinutes, maxSnoozes, alarmEnabled, " +
                    "alarmWindowStartMinutes, alarmWindowEndMinutes, snoozeMinutes, alarmSound, espressoHomeMg, " +
                    "espressoRestaurantMg, decafShotMg, colaMgPer100Ml, caffeineHalfLifeHours) " +
                    "VALUES (0, 1320, 480, 1, 0, 345, 375, 9, NULL, 63, 75, 3, 10, 5)",
            )
        }
        helper.runMigrationsAndValidate(dbName, 3, true).use { db ->
            db.query("SELECT alarmEnabled, bedtimeReminderEnabled FROM settings").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0)) // the user's alarm-off choice survives
                assertEquals(1, c.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM nights").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
        }
    }

    @Test
    fun version3To4TurnsTheBedsideCheckOn() {
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL(
                "INSERT INTO settings (id, targetBedtimeMinutes, targetSleepMinutes, maxSnoozes, alarmEnabled, " +
                    "bedtimeReminderEnabled, alarmWindowStartMinutes, alarmWindowEndMinutes, snoozeMinutes, alarmSound, " +
                    "espressoHomeMg, espressoRestaurantMg, decafShotMg, colaMgPer100Ml, caffeineHalfLifeHours) " +
                    "VALUES (0, 1320, 480, 1, 1, 0, 345, 375, 9, NULL, 63, 75, 3, 10, 5)",
            )
        }
        helper.runMigrationsAndValidate(dbName, 4, true).use { db ->
            db.query("SELECT bedtimeReminderEnabled, bedsideCheckEnabled FROM settings").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
                assertEquals(1, c.getInt(1))
            }
        }
    }
}
