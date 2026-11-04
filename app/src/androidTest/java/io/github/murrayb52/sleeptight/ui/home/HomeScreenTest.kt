package io.github.murrayb52.sleeptight.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.murrayb52.sleeptight.alarm.AlarmState
import io.github.murrayb52.sleeptight.tracking.LiveLevel
import io.github.murrayb52.sleeptight.tracking.TrackingStatus
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Africa/Johannesburg")
    private val bed = Instant.parse("2026-11-03T20:00:00Z") // 22:00 SAST

    private var bedTaps = 0
    private var upTaps = 0
    private var keptOpen: Long? = null
    private var closedAt: LocalTime? = null
    private var micRequests = 0
    private var restarts = 0
    private val alarmToggles = mutableListOf<Boolean>()
    private var stops = 0
    private var openedNight: Long? = null

    private fun show(state: HomeUiState, device: DeviceState = DeviceState()) = compose.setContent {
        SleepTightTheme {
            HomeScreen(
                state = state,
                device = device,
                message = null,
                twinkle = false,
                actions = HomeActions(
                    onGoingToBed = { bedTaps++ },
                    onImUp = { upTaps++ },
                    onKeepNightOpen = { keptOpen = it },
                    onCloseNightAt = { closedAt = it },
                    onAllowMicrophone = { micRequests++ },
                    onRestartTracking = { restarts++ },
                    onAlarmToggled = { alarmToggles += it },
                    onStopAlarm = { stops++ },
                    onOpenNight = { openedNight = it },
                ),
            )
        }
    }

    private val openNight = OpenNightUi(1, bed, Duration.ofMinutes(75))
    private fun inBed(tracking: TrackingStatus = TrackingStatus(), alarm: AlarmUi = AlarmUi()) =
        HomeUiState(loading = false, zone = zone, openNight = openNight, tracking = tracking, alarm = alarm)
    private fun recording(relative: Double, silent: Boolean = false) = TrackingStatus(
        TrackingStatus.State.RECORDING,
        1,
        LiveLevel(bed, -50.0 + relative, -50.0, relative, silent),
    )

    @Test
    fun beforeBedShowsGoingToBedAndTheWakeByWheel() {
        show(HomeUiState(loading = false, zone = zone))
        compose.onNodeWithTag("wakeByWheel").assertExists()
        compose.onNodeWithText("to wake you gently between", substring = true).assertExists()
        compose.onNodeWithText("Going to bed").performScrollTo().performClick()
        assertEquals(1, bedTaps)
    }

    @Test
    fun beforeBedExplainsTheAlarmIsSetAtTheBedTap() {
        show(HomeUiState(loading = false, zone = zone))
        compose.onNodeWithText("When you tap Going to bed, the alarm is set", substring = true).assertExists()
        compose.onNodeWithText("Alarm on").assertDoesNotExist()
    }

    @Test
    fun lastNightOpensItsPage() {
        val last = LastNightUi(bed, bed.plus(Duration.ofMinutes(475)), Duration.ofMinutes(475), nightId = 9)
        show(HomeUiState(loading = false, zone = zone, lastNight = last))
        compose.onNodeWithText("Last night").performScrollTo().performClick()
        assertEquals(9L, openedNight)
    }

    @Test
    fun inBedShowsTonightWithHoldToGetUp() {
        show(inBed(recording(4.2)))
        compose.onNodeWithText("Listening · sound levels only").assertIsDisplayed()
        compose.onNodeWithText("+4 dB vs your room").assertExists()
        compose.onNodeWithTag("imUp").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, upTaps)
    }

    @Test
    fun noiseEventsAreListed() {
        show(
            inBed(recording(1.0)).copy(
                noiseEvents = listOf(NoiseEventUi(bed.plus(Duration.ofMinutes(72)), Duration.ofSeconds(6), 22)),
            ),
        )
        compose.onNodeWithText("Noise events: 1").assertExists()
        // The time follows the device 12/24-hour setting, so match the rest of the line.
        compose.onNodeWithText("· 6 s · peak +22 dB", substring = true).assertExists()
    }

    @Test
    fun silentMicIsFlagged() {
        show(inBed(recording(0.0, silent = true)))
        compose.onNodeWithText("complete silence", substring = true).assertExists()
    }

    @Test
    fun deniedMicExplainsAndOffersToAllow() {
        show(inBed(), DeviceState(micGranted = false))
        compose.onNodeWithText("Sound monitoring is off").assertExists()
        compose.onNodeWithText("Allow microphone").performScrollTo().performClick()
        assertEquals(1, micRequests)
    }

    @Test
    fun killedServiceOffersToResume() {
        val stopped = TrackingStatus(TrackingStatus.State.STOPPED_WITH_PROBLEM, 1, problem = TrackingStatus.Problem.SERVICE_NOT_ALLOWED)
        show(inBed(stopped))
        compose.onNodeWithText("Resume").performScrollTo().performClick()
        assertEquals(1, restarts)
    }

    @Test
    fun tonightsAlarmShowsInBed() {
        val start = Instant.parse("2026-11-04T03:45:00Z")
        show(inBed(recording(0.0), AlarmUi(tonightStart = start, tonightEnd = start.plus(Duration.ofMinutes(30)))))
        compose.onNodeWithText("Alarm ", substring = true).assertExists()
    }

    @Test
    fun ringingAlarmCanBeStoppedFromTonight() {
        show(inBed(recording(0.0), AlarmUi(state = AlarmState.Ringing(1, bed, nextSnoozeMinutes = 9))))
        compose.onNodeWithText("Stop alarm").performScrollTo().performClick()
        assertEquals(1, stops)
    }

    @Test
    fun batteryCardShowsSamsungAdviceOnlyOnSamsung() {
        show(HomeUiState(loading = false, zone = zone), DeviceState(batteryUnrestricted = false, isSamsung = true))
        compose.onNodeWithText("Keep tracking running overnight").assertExists()
        compose.onNodeWithText("Deep sleeping apps", substring = true).assertExists()
    }

    @Test
    fun alarmVolumeOffIsFlagged() {
        show(HomeUiState(loading = false, zone = zone), DeviceState(alarmVolumeOff = true))
        compose.onNodeWithText("alarm volume is off", substring = true).assertExists()
    }

    @Test
    fun overduePromptCanBeKeptOpen() {
        show(inBed().copy(openNight = OpenNightUi(7, bed, Duration.ofHours(17)), overduePrompt = OverduePrompt(7, bed, Duration.ofHours(17), LocalTime.of(6, 0))))
        compose.onNodeWithText("Still in bed?").assertIsDisplayed()
        compose.onNodeWithText("Keep it open").performClick()
        assertEquals(7L, keptOpen)
    }

    @Test
    fun overduePromptClosesAtThePickedTime() {
        show(inBed().copy(openNight = OpenNightUi(7, bed, Duration.ofHours(17)), overduePrompt = OverduePrompt(7, bed, Duration.ofHours(17), LocalTime.of(6, 0))))
        compose.onNodeWithText("Set wake time").performClick()
        compose.onNodeWithText("When did you get up?").assertIsDisplayed()
        compose.onNodeWithText("Close night").performClick()
        assertEquals(LocalTime.of(6, 0), closedAt)
    }
}
