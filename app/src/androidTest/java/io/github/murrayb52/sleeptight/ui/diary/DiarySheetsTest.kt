package io.github.murrayb52.sleeptight.ui.diary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.murrayb52.sleeptight.data.CaffeineEntry
import io.github.murrayb52.sleeptight.data.DrinkType
import io.github.murrayb52.sleeptight.data.LastScreens
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.data.WakeReason
import io.github.murrayb52.sleeptight.ui.home.BedsideCheckContent
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class DiarySheetsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tonightStartsWithTheEarplugsDefaultAndPassesTheTagsOn() {
        var started: TonightAnswers? = null
        compose.setContent {
            SleepTightTheme { BedsideCheckContent(showChecks = false, earplugsDefault = true) { _, a -> started = a } }
        }
        compose.onNodeWithText("Before you sleep").assertDoesNotExist()
        compose.onNodeWithText("Alcohol").performClick()
        compose.onNodeWithText("Under 1 h ago").performClick()
        compose.onNodeWithText("Start the night").performScrollTo().performClick()
        assertEquals(TonightAnswers(earplugs = true, alcohol = true, lastScreens = LastScreens.UNDER_1H), started)
    }

    @Test
    fun earplugsCanBeTurnedOffForTheNight() {
        var started: TonightAnswers? = null
        compose.setContent {
            SleepTightTheme { BedsideCheckContent(showChecks = true, earplugsDefault = true) { _, a -> started = a } }
        }
        compose.onNodeWithText("Before you sleep").assertExists()
        compose.onNodeWithTag("earplugs").performScrollTo().performClick()
        compose.onNodeWithText("Start the night").performScrollTo().performClick()
        assertEquals(false, started?.earplugs)
    }

    @Test
    fun aWakeNoteNeedsAReasonOrANote() {
        var saved: Set<WakeReason>? = null
        compose.setContent {
            SleepTightTheme { Column(Modifier.verticalScroll(rememberScrollState())) { WakeNoteContent("02:14", onSave = { r, _ -> saved = r }, onNotNow = {}) } }
        }
        compose.onNodeWithText("Awake at 02:14. Why?").assertExists()
        compose.onNodeWithTag("wakeNoteSave").assertIsNotEnabled()
        compose.onNodeWithText("Water").performClick()
        compose.onNodeWithText("Toilet").performClick()
        compose.onNodeWithTag("wakeNoteSave").assertIsEnabled().performClick()
        assertEquals(setOf(WakeReason.WATER, WakeReason.TOILET), saved)
    }

    @Test
    fun theSurveyAsksAboutEarplugsOnlyOnEarplugNights() {
        var saved: SurveyAnswers? = null
        compose.setContent {
            SleepTightTheme { Column(Modifier.verticalScroll(rememberScrollState())) { MorningSurveyContent(earplugs = false, onSave = { saved = it }, onLater = {}) } }
        }
        compose.onNodeWithText("Earplug adjustments").assertDoesNotExist()
        compose.onNodeWithContentDescription("More Times you think you woke").performClick()
        compose.onNodeWithContentDescription("More Times you think you woke").performClick()
        compose.onNodeWithTag("surveySave").performScrollTo().performClick()
        assertEquals(2, saved?.wakings)
        assertNull(saved?.earplugAdjustments)
    }

    @Test
    fun caffeineAddsTheChosenDrinkAndListsTheDay() {
        val added = mutableListOf<CaffeineEntry>()
        val now = Instant.parse("2026-11-05T12:00:00Z")
        val earlier = CaffeineEntry(timestamp = now.minusSeconds(3600).toEpochMilli(), drinkType = DrinkType.COKE, volumeMl = 500, mg = 50.0)
        compose.setContent {
            SleepTightTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CaffeineContent(listOf(earlier), Settings(), ZoneId.of("Africa/Johannesburg"), { now }, onAdd = { added += it }, onDelete = {})
                }
            }
        }
        compose.onNodeWithText("500 ml Coke", substring = true).assertExists()
        compose.onNodeWithText("Double").performClick()
        compose.onNodeWithText("2 h ago").performClick()
        compose.onNodeWithText("Add · 126 mg").performClick()
        assertEquals(126.0, added.single().mg, 1e-9)
        assertEquals(now.minusSeconds(7200).toEpochMilli(), added.single().timestamp)
    }
}
