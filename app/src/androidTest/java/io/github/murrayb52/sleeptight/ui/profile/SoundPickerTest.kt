package io.github.murrayb52.sleeptight.ui.profile

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.murrayb52.sleeptight.alarm.AlarmSound
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundPickerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pickingASoundSavesItsKey() {
        var picked: String? = "unset"
        compose.setContent {
            SleepTightTheme { SoundPickerDialog(AlarmSound.CHIME, onPick = { picked = it }, onDismiss = {}, preview = false) }
        }
        compose.onNodeWithText("Chime").assertIsSelected()
        compose.onNodeWithText("Birdsong").performClick()
        assertEquals("birdsong", picked)
        compose.onNodeWithText("Birdsong").assertIsSelected()
        compose.onNodeWithText("Chime").performClick()
        assertEquals(null, picked)
    }
}
