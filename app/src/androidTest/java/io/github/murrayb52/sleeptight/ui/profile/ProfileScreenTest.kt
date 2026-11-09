package io.github.murrayb52.sleeptight.ui.profile

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.metrics.SnoozeCurve
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var sleepGoal: Int? = null
    private var snoozeGoal: Int? = null
    private var snoozesAllowed: Int? = null
    private var curve: SnoozeCurve? = null

    private fun show(settings: Settings = Settings()) = compose.setContent {
        SleepTightTheme {
            ProfileScreen(
                ProfileUi(loading = false, settings = settings),
                onSleepGoal = { sleepGoal = it },
                onSnoozeGoal = { snoozeGoal = it },
                onSnoozesAllowed = { snoozesAllowed = it },
                onSnoozeCurve = { curve = it },
            )
        }
    }

    @Test
    fun goalsShowTheirCurrentValues() {
        show(Settings(targetSleepMinutes = 480, maxSnoozes = 1))
        compose.onNodeWithText("8 h").assertExists()
        compose.onNodeWithText("at most 1").assertExists()
    }

    @Test
    fun sleepGoalSliderSavesWholeHours() {
        show()
        compose.onNodeWithTag("sleepGoal").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(7.4f) }
        compose.waitForIdle()
        assertEquals(7 * 60, sleepGoal)
    }

    @Test
    fun snoozeGoalSliderSavesWholeSnoozes() {
        show()
        compose.onNodeWithTag("snoozeGoal").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        compose.waitForIdle()
        assertEquals(3, snoozeGoal)
    }

    @Test
    fun snoozesAllowedSliderSavesWholeSnoozes() {
        show()
        compose.onNodeWithTag("snoozesAllowed").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(7.3f) }
        compose.waitForIdle()
        assertEquals(7, snoozesAllowed)
    }

    @Test
    fun snoozeCurveDialogShowsEachCurvesLengthsAndSavesTheChoice() {
        show(Settings(snoozesAllowed = 5, snoozeCurve = "LINEAR"))
        compose.onNodeWithText("Snooze lengths").performScrollTo().performClick()
        compose.onNodeWithText("Constant: 9, 9, 9, 9, 9 min").assertExists()
        compose.onNodeWithText("Linear: 9, 7, 6, 4, 2 min", substring = true).assertExists()
        compose.onNodeWithText("Exponential: 9, 6, 4, 3, 2 min").performClick()
        assertEquals(SnoozeCurve.EXPONENTIAL, curve)
    }
}
