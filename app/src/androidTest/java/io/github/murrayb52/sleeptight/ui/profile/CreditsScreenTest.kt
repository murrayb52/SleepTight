package io.github.murrayb52.sleeptight.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreditsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show() = compose.setContent { SleepTightTheme { CreditsScreen(onBack = {}) } }

    @Test
    fun everyBundledSoundIsCredited() {
        show()
        listOf("F1tz", "f-r-a-g-i-l-e", "bassimat", "Tetoszka").forEach { author ->
            compose.onNodeWithText("by $author", substring = true).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun theFontLicenceTextShipsWithTheApp() {
        show()
        compose.onNodeWithText("Outfit, by the Outfit Project Authors").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("View licence").performScrollTo().performClick()
        compose.onNodeWithText("SIL OPEN FONT LICENSE Version 1.1", substring = true).assertExists()
    }
}
