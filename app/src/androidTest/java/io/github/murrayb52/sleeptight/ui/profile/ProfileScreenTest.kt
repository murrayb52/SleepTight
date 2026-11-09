package io.github.murrayb52.sleeptight.ui.profile

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    private var opened: SettingsPageId? = null
    private var sleepGoal: Int? = null
    private var snoozeGoal: Int? = null
    private var snoozesAllowed: Int? = null
    private var curve: SnoozeCurve? = null

    private fun showProfile(settings: Settings = Settings()) = compose.setContent {
        SleepTightTheme { ProfileScreen(ProfileUi(loading = false, settings = settings), onOpenSettings = { opened = it }) }
    }

    private fun showGoals(settings: Settings = Settings()) = compose.setContent {
        SleepTightTheme { GoalSettingsScreen(settings, onSleepGoal = { sleepGoal = it }, onSnoozeGoal = { snoozeGoal = it }) }
    }

    private fun showAlarm(settings: Settings = Settings()) = compose.setContent {
        SleepTightTheme { AlarmSettingsScreen(settings, onSnoozesAllowed = { snoozesAllowed = it }, onSnoozeCurve = { curve = it }) }
    }

    @Test
    fun profileSummarisesEachSettingsPageAndOpensIt() {
        showProfile(Settings(targetSleepMinutes = 480, maxSnoozes = 1, snoozesAllowed = 5))
        compose.onNodeWithText("30 min window", substring = true).assertExists()
        compose.onNodeWithText("5 snoozes", substring = true).assertExists()
        compose.onNodeWithText("Bedtime reminder on").assertExists()
        compose.onNodeWithText("8 h", substring = true).assertExists()
        compose.onNodeWithTag("settingsAlarm").performClick()
        assertEquals(SettingsPageId.ALARM, opened)
        compose.onNodeWithTag("settingsGoals").performScrollTo().performClick()
        assertEquals(SettingsPageId.GOALS, opened)
    }

    @Test
    fun goalsShowTheirCurrentValues() {
        showGoals(Settings(targetSleepMinutes = 480, maxSnoozes = 1))
        compose.onNodeWithText("8 h").assertExists()
        compose.onNodeWithText("at most 1").assertExists()
    }

    @Test
    fun sleepGoalSliderSavesWholeHours() {
        showGoals()
        compose.onNodeWithTag("sleepGoal").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(7.4f) }
        compose.waitForIdle()
        assertEquals(7 * 60, sleepGoal)
    }

    @Test
    fun snoozeGoalSliderSavesWholeSnoozes() {
        showGoals()
        compose.onNodeWithTag("snoozeGoal").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        compose.waitForIdle()
        assertEquals(3, snoozeGoal)
    }

    @Test
    fun snoozesAllowedSliderSavesWholeSnoozes() {
        showAlarm()
        compose.onNodeWithTag("snoozesAllowed").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(7.3f) }
        compose.waitForIdle()
        assertEquals(7, snoozesAllowed)
    }

    @Test
    fun snoozeCurvesAreChartsNotListsOfMinutes() {
        showAlarm(Settings(snoozesAllowed = 5, snoozeCurve = "LINEAR"))
        compose.onNodeWithText("9, 7, 6, 4, 2", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("curve_LINEAR").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Up to 28 min of snoozing", substring = true).assertExists()
        compose.onNodeWithTag("curve_EXPONENTIAL").performClick()
        assertEquals(SnoozeCurve.EXPONENTIAL, curve)
    }

    @Test
    fun withOneSnoozeThereIsNoCurveToChoose() {
        showAlarm(Settings(snoozesAllowed = 1))
        compose.onNodeWithTag("curve_LINEAR").assertDoesNotExist()
        compose.onNodeWithText("Up to 9 min of snoozing", substring = true).assertExists()
    }

    @Test
    fun curveLabelsFitOnOneLine() {
        showAlarm(Settings(snoozesAllowed = 5))
        SnoozeCurve.entries.forEach { curve ->
            compose.onNodeWithTag("curve_${curve.name}").performScrollTo()
            assertEquals("${curve.name} label wrapped", 1, lineCountOf(labelOf(curve)))
        }
    }

    private fun labelOf(curve: SnoozeCurve): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(curve.label)

    /** How many lines the label actually laid out on, at the test device's width. */
    private fun lineCountOf(text: String): Int {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first().lineCount
    }
}
