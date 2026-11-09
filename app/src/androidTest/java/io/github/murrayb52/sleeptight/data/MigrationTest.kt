package io.github.murrayb52.sleeptight.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun version4To5KeepsSoundSamplesAndAddsScreenEvents() {
        helper.createDatabase(dbName, 4).use { db ->
            db.execSQL("INSERT INTO nights (id, bedTapAt, wakeTapAt, zoneId, snoozeCount, garminImported) VALUES (6, 1000, 2000, 'Africa/Johannesburg', 0, 0)")
            db.execSQL("INSERT INTO sound_samples (nightId, timestamp, dbRelative) VALUES (6, 1000, 3.5)")
        }
        helper.runMigrationsAndValidate(dbName, 5, true).use { db ->
            db.query("SELECT dbRelative, dbfs, peakDbfs, lowDbfs, midDbfs, highDbfs FROM sound_samples").use { c ->
                c.moveToFirst()
                assertEquals(3.5, c.getDouble(0), 0.0)
                (1..5).forEach { assertTrue(c.isNull(it)) } // older nights have no measured levels
            }
            db.execSQL("INSERT INTO screen_events (nightId, timestamp, type) VALUES (6, 1500, 'UNLOCKED')")
        }
    }

    @Test
    fun version5To6AddsTheDiaryAndEarplugsDefaultOn() {
        helper.createDatabase(dbName, 5).use { db ->
            db.execSQL("INSERT INTO nights (id, bedTapAt, wakeTapAt, zoneId, snoozeCount, garminImported) VALUES (6, 1000, 2000, 'Africa/Johannesburg', 0, 0)")
            db.execSQL(
                "INSERT INTO settings (id, targetBedtimeMinutes, targetSleepMinutes, maxSnoozes, alarmEnabled, " +
                    "bedtimeReminderEnabled, bedsideCheckEnabled, alarmWindowStartMinutes, alarmWindowEndMinutes, snoozeMinutes, alarmSound, " +
                    "espressoHomeMg, espressoRestaurantMg, decafShotMg, colaMgPer100Ml, caffeineHalfLifeHours) " +
                    "VALUES (0, 1320, 480, 1, 1, 1, 1, 345, 375, 9, NULL, 63, 75, 3, 10, 5)",
            )
        }
        helper.runMigrationsAndValidate(dbName, 6, true).use { db ->
            db.query("SELECT earplugsDefault FROM settings").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
            db.execSQL("INSERT INTO night_context (nightId, earplugs, alcohol, partner, differentBed, readingBeforeBed, lastScreens) VALUES (6, 1, 0, 0, 0, 1, 'UNDER_1H')")
            db.execSQL("INSERT INTO wake_notes (nightId, at, notedAt, reasons, note) VALUES (6, 1500, 1520, 'TOILET,WATER', NULL)")
            db.execSQL(
                "INSERT INTO morning_surveys (nightId, answeredAt, rested, quality, wakings, minutesToFallAsleep, minutesBeforeTrying, " +
                    "earplugAdjustments, notes) VALUES (6, 2100, 7, 6, 2, 15, 10, 1, NULL)",
            )
        }
    }

    @Test
    fun version6To7KeepsNightsWithNoAppVersion() {
        helper.createDatabase(dbName, 6).use { db ->
            db.execSQL("INSERT INTO nights (id, bedTapAt, wakeTapAt, zoneId, snoozeCount, garminImported) VALUES (7, 1000, 2000, 'Africa/Johannesburg', 5, 1)")
        }
        helper.runMigrationsAndValidate(dbName, 7, true).use { db ->
            db.query("SELECT snoozeCount, appVersion FROM nights WHERE id = 7").use { c ->
                c.moveToFirst()
                assertEquals(5, c.getInt(0))
                assertTrue(c.isNull(1)) // older nights don't know their version
            }
            db.execSQL("INSERT INTO nights (id, bedTapAt, zoneId, snoozeCount, garminImported, appVersion) VALUES (8, 3000, 'Africa/Johannesburg', 0, 0, '0.7.0')")
        }
    }

    @Test
    fun version7To8DefaultsToFiveLinearSnoozesAndAddsSnoozeEvents() {
        helper.createDatabase(dbName, 7).use { db ->
            db.execSQL("INSERT INTO nights (id, bedTapAt, wakeTapAt, zoneId, snoozeCount, garminImported) VALUES (9, 1000, 2000, 'Africa/Johannesburg', 2, 0)")
            db.execSQL(
                "INSERT INTO settings (id, targetBedtimeMinutes, targetSleepMinutes, maxSnoozes, alarmEnabled, " +
                    "bedtimeReminderEnabled, bedsideCheckEnabled, earplugsDefault, alarmWindowStartMinutes, alarmWindowEndMinutes, snoozeMinutes, " +
                    "alarmSound, espressoHomeMg, espressoRestaurantMg, decafShotMg, colaMgPer100Ml, caffeineHalfLifeHours) " +
                    "VALUES (0, 1320, 480, 1, 1, 1, 1, 1, 345, 375, 9, NULL, 63, 75, 3, 10, 5)",
            )
        }
        helper.runMigrationsAndValidate(dbName, 8, true).use { db ->
            db.query("SELECT snoozesAllowed, snoozeCurve FROM settings").use { c ->
                c.moveToFirst()
                assertEquals(5, c.getInt(0))
                assertEquals("LINEAR", c.getString(1))
            }
            db.query("SELECT snoozeCount FROM nights WHERE id = 9").use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0)) // older snoozes keep their count, without times
            }
            db.execSQL("INSERT INTO snooze_events (nightId, timestamp, minutes, curve) VALUES (9, 1500, 9, 'LINEAR')")
        }
    }
}
