package io.github.murrayb52.sleeptight.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.murrayb52.sleeptight.metrics.Goals
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme
import org.junit.Assert.assertEquals
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

    @Test
    fun aScoredNightShowsItsPointsAndMeasurements() {
        val score = io.github.murrayb52.sleeptight.scoring.ScoreCache(
            total = 78,
            source = "ESTIMATED",
            deepRemRedistributed = true,
            parts = listOf(
                io.github.murrayb52.sleeptight.scoring.ScoreCache.Part("DURATION", 41.5, 43.75),
                io.github.murrayb52.sleeptight.scoring.ScoreCache.Part("EFFICIENCY", 26.0, 31.25),
                io.github.murrayb52.sleeptight.scoring.ScoreCache.Part("INTERRUPTIONS", 7.5, 12.5),
                io.github.murrayb52.sleeptight.scoring.ScoreCache.Part("REGULARITY", 3.0, 12.5),
            ),
            metrics = io.github.murrayb52.sleeptight.scoring.ScoreCache.Metrics(486, 456, 12, 1, 0.92, null, null, null, 2, 1),
            regularity = io.github.murrayb52.sleeptight.scoring.ScoreCache.Factors(1.0, 1.0, 1.0, null),
        )
        compose.setContent {
            SleepTightTheme { NightDetailScreen(NightDetailUi(loading = false, night = night.copy(score = 78), score = score), onBack = {}) }
        }
        compose.onNodeWithText("7 h 36 min asleep").assertIsDisplayed()
        compose.onNodeWithText("Estimated from overnight sound").assertExists()
        compose.onNodeWithText("42 / 44").performScrollTo().assertIsDisplayed()
        // Shown in the breakdown and again in How you slept.
        compose.onAllNodesWithText("92\u00A0%")[0].performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Needs Garmin stages", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 wake-up, 1 noise cluster").performScrollTo().assertIsDisplayed()

        // Morning report: measurements against reference ranges, and the goals.
        compose.onNodeWithText("How you slept").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Healthy: 7–9 h").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Your goals").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Needs 3 nights this week").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun deletingANightAsksFirst() {
        var deleted = 0
        compose.setContent {
            SleepTightTheme { NightDetailScreen(NightDetailUi(loading = false, night = night), onBack = {}, onDelete = { deleted++ }) }
        }
        compose.onNodeWithTag("deleteNight").performScrollTo().performClick()
        compose.onNodeWithText("Delete this night?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, deleted)
        compose.onNodeWithTag("deleteNight").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(1, deleted)
    }
}
