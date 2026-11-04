package io.github.murrayb52.sleeptight.alarm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var snoozes = 0
    private var stops = 0

    private fun show(next: Int?, used: Int) = compose.setContent {
        SleepTightTheme {
            AlarmScreen("05:52", nextSnoozeMinutes = next, snoozesUsed = used, onSnooze = { snoozes++ }, onDismiss = { stops++ })
        }
    }

    @Test
    fun offersTheNextShorterSnooze() {
        show(next = 7, used = 1)
        compose.onNodeWithText("Snooze 7 min").assertIsDisplayed().performClick()
        compose.onNodeWithText("1 of 5 snoozes used", substring = true).assertIsDisplayed()
        assertEquals(1, snoozes)
    }

    @Test
    fun afterFiveSnoozesOnlyStopIsLeft() {
        show(next = null, used = 5)
        compose.onNodeWithText("No snoozes left", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Snooze", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Dismiss").performClick()
        assertEquals(1, stops)
    }
}
