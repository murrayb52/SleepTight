package io.github.murrayb52.sleeptight.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.murrayb52.sleeptight.metrics.Goals
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class NightDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Africa/Johannesburg")
    private val bed = Instant.parse("2026-11-03T19:52:00Z")
    private val night = NightRowUi(1, LocalDate.parse("2026-11-03"), bed, bed.plus(Duration.ofMinutes(486)), zone, Duration.ofMinutes(486), 0, 1)

    private fun show(goals: Goals = Goals()) = compose.setContent {
        SleepTightTheme { NightDetailScreen(NightDetailUi(loading = false, night = night, goals = goals), onBack = {}) }
    }

    @Test
    fun everyScorePartIsListedWithItsMaximum() {
        show()
        compose.onNodeWithText("How the score adds up").assertExists()
        compose.onNodeWithText("– / 35").assertExists()
        compose.onNodeWithText("– / 25").assertExists()
        compose.onNodeWithText("– / 20").assertExists()
    }

    // The explainers use a non-breaking space before "%" so "90" and "%" never split across lines.
    @Test
    fun efficiencyInfoExplainsItsThresholds() {
        show()
        compose.onNodeWithTag("info-EFFICIENCY").performScrollTo().performClick()
        compose.onNodeWithText("0 points at 70\u00A0% or less", substring = true).assertIsDisplayed()
        compose.onNodeWithText("90\u00A0% or more", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("0 points at 70\u00A0% or less", substring = true).assertDoesNotExist()
    }

    @Test
    fun regularityInfoUsesTheUsersGoals() {
        show(Goals(bedtime = LocalTime.of(22, 30), sleep = Duration.ofMinutes(450), maxSnoozes = 2))
        compose.onNodeWithTag("info-REGULARITY").performScrollTo().performClick()
        compose.onNodeWithText("7 h 30 min", substring = true).assertIsDisplayed()
        compose.onNodeWithText("at most 2 snoozes", substring = true).assertIsDisplayed()
    }

    @Test
    fun theScoreItselfHasAnExplanation() {
        show()
        compose.onNodeWithTag("info-score").performClick()
        compose.onNodeWithText("a full circle is 100", substring = true).assertIsDisplayed()
    }
}
