package io.github.murrayb52.sleeptight.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HoldButtonTest {
    @get:Rule
    val compose = createComposeRule()

    private var held = 0

    private fun show() = compose.setContent {
        SleepTightTheme { HoldButton("I'm up", holdingText = "Keep holding…", onHeld = { held++ }, modifier = Modifier.testTag("hold")) }
    }

    @Test
    fun holdingLongEnoughFiresAndShowsFeedbackWhileHeld() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("hold").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Keep holding…").assertExists() // feedback while holding
        assertEquals(0, held)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(1, held)
        compose.onNodeWithTag("hold").performTouchInput { up() }
    }

    @Test
    fun aQuickTapDoesNothing() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("hold").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("hold").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(0, held)
        compose.onNodeWithText("I'm up").assertExists()
    }
}
