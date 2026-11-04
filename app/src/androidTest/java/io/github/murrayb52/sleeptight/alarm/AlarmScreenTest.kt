package io.github.murrayb52.sleeptight.alarm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class AlarmScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var snoozes = 0
    private var stops = 0
    private var done = 0
    private var seen: Long? = null
    private val since = Instant.parse("2026-11-04T03:52:00Z")

    private fun show(next: Int?, used: Int, stopped: Boolean = false, morning: MorningUi? = null) = compose.setContent {
        SleepTightTheme {
            AlarmExperience(
                ringingSince = since,
                nextSnoozeMinutes = next,
                snoozesUsed = used,
                stopped = stopped,
                morning = morning,
                onSnooze = { snoozes++ },
                onStop = { stops++ },
                onSeeNight = { seen = it },
                onDone = { done++ },
                animate = false,
            )
        }
    }

    @Test
    fun swipeUpSnoozesWithoutShowingDurations() {
        show(next = 7, used = 1)
        compose.onNodeWithText("Swipe up to snooze").assertIsDisplayed()
        compose.onNodeWithText("7 min", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("alarmScreen").performTouchInput { swipeUp() }
        assertEquals(1, snoozes)
    }

    @Test
    fun stopIsTheAmberButton() {
        show(next = 9, used = 0)
        compose.onNodeWithText("Stop alarm").performClick()
        assertEquals(1, stops)
    }

    @Test
    fun afterFiveSnoozesOnlyStopIsLeft() {
        show(next = null, used = 5)
        compose.onNodeWithText("No snoozes left", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Swipe up to snooze").assertDoesNotExist()
        compose.onNodeWithTag("alarmScreen").performTouchInput { swipeUp() }
        assertEquals(0, snoozes)
    }

    @Test
    fun afterStopTheMorningSummaryShowsLastNight() {
        val zone = ZoneId.of("Africa/Johannesburg")
        val morning = MorningUi(4, Instant.parse("2026-11-03T20:04:00Z"), Instant.parse("2026-11-04T03:58:00Z"), zone, noiseEvents = 3, snoozes = 1)
        show(next = 7, used = 1, stopped = true, morning = morning)
        compose.onNodeWithText("Here's last night").assertIsDisplayed()
        compose.onNodeWithText("7 h 54 min").assertIsDisplayed()
        compose.onNodeWithText("noise events").assertIsDisplayed()
        compose.onNodeWithText("See the night").performClick()
        assertEquals(4L, seen)
    }
}
