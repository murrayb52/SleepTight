package io.github.murrayb52.sleeptight.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.murrayb52.sleeptight.tracking.LiveLevel
import io.github.murrayb52.sleeptight.tracking.TrackingStatus
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    private fun show(state: HomeUiState, device: DeviceState = DeviceState()) = compose.setContent {
        SleepTightTheme {
            HomeScreen(
                state = state,
                device = device,
                message = null,
                actions = HomeActions(
                    onGoingToBed = { bedTaps++ },
                    onImUp = { upTaps++ },
                    onKeepNightOpen = { keptOpen = it },
                    onCloseNightAt = { closedAt = it },
                    onAllowMicrophone = { micRequests++ },
                    onRestartTracking = { restarts++ },
                ),
            )
        }
    }

    private val openNight = OpenNightUi(1, bed, Duration.ofMinutes(75))
    private fun recording(relative: Double, silent: Boolean = false) = TrackingStatus(
        TrackingStatus.State.RECORDING,
        1,
        LiveLevel(bed, -50.0 + relative, -50.0, relative, silent),
    )

    @Test
    fun recordingShowsTheLiveLevelAndNoiseEvents() {
        show(
            HomeUiState(
                loading = false,
                zone = zone,
                openNight = openNight,
                tracking = recording(4.2),
                noiseEvents = listOf(NoiseEventUi(bed.plus(Duration.ofMinutes(72)), Duration.ofSeconds(6), 22)),
            ),
        )
        compose.onNodeWithText("+4 dB vs your room").assertIsDisplayed()
        compose.onNodeWithText("Noise events: 1").assertIsDisplayed()
        // The time follows the device 12/24-hour setting, so match the rest of the line.
        compose.onNodeWithText("· 6 s · peak +22 dB", substring = true).assertExists()
    }

    @Test
    fun silentMicIsFlagged() {
        show(HomeUiState(loading = false, zone = zone, openNight = openNight, tracking = recording(0.0, silent = true)))
        compose.onNodeWithText("complete silence", substring = true).assertExists()
    }

    @Test
    fun deniedMicExplainsAndOffersToAllow() {
        show(HomeUiState(loading = false, zone = zone, openNight = openNight), DeviceState(micGranted = false))
        compose.onNodeWithText("Sound monitoring is off").assertIsDisplayed()
        compose.onNodeWithText("Allow microphone").performScrollTo().performClick()
        assertEquals(1, micRequests)
    }

    @Test
    fun killedServiceOffersToResume() {
        val stopped = TrackingStatus(TrackingStatus.State.STOPPED_WITH_PROBLEM, 1, problem = TrackingStatus.Problem.SERVICE_NOT_ALLOWED)
        show(HomeUiState(loading = false, zone = zone, openNight = openNight, tracking = stopped))
        compose.onNodeWithText("Resume").performScrollTo().performClick()
        assertEquals(1, restarts)
    }

    @Test
    fun batteryCardShowsSamsungAdviceOnlyOnSamsung() {
        show(HomeUiState(loading = false, zone = zone), DeviceState(batteryUnrestricted = false, isSamsung = true))
        compose.onNodeWithText("Keep tracking running overnight").assertExists()
        compose.onNodeWithText("Deep sleeping apps", substring = true).assertExists()
    }

    @Test
    fun noOpenNightShowsGoingToBed() {
        show(HomeUiState(loading = false, zone = zone))
        compose.onNodeWithText("Going to bed").assertIsDisplayed().performClick()
        assertEquals(1, bedTaps)
    }

    @Test
    fun openNightShowsImUp() {
        show(HomeUiState(loading = false, zone = zone, openNight = OpenNightUi(1, bed, Duration.ofMinutes(75))))
        compose.onNodeWithText("1 h 15 min so far").assertIsDisplayed()
        compose.onNodeWithText("I'm up").assertIsDisplayed().performClick()
        assertEquals(1, upTaps)
    }

    @Test
    fun lastNightCardShowsTimeInBed() {
        show(
            HomeUiState(
                loading = false,
                zone = zone,
                lastNight = LastNightUi(bed, bed.plus(Duration.ofMinutes(475)), Duration.ofMinutes(475)),
            ),
        )
        compose.onNodeWithText("Last night").assertIsDisplayed()
        compose.onNodeWithText("7 h 55 min in bed").assertIsDisplayed()
    }

    @Test
    fun overduePromptCanBeKeptOpen() {
        val open = OpenNightUi(7, bed, Duration.ofHours(17))
        show(
            HomeUiState(
                loading = false,
                zone = zone,
                openNight = open,
                overduePrompt = OverduePrompt(7, bed, Duration.ofHours(17), LocalTime.of(6, 0)),
            ),
        )
        compose.onNodeWithText("Still in bed?").assertIsDisplayed()
        compose.onNodeWithText("Keep it open").performClick()
        assertEquals(7L, keptOpen)
    }

    @Test
    fun overduePromptClosesAtThePickedTime() {
        show(
            HomeUiState(
                loading = false,
                zone = zone,
                openNight = OpenNightUi(7, bed, Duration.ofHours(17)),
                overduePrompt = OverduePrompt(7, bed, Duration.ofHours(17), LocalTime.of(6, 0)),
            ),
        )
        compose.onNodeWithText("Set wake time").performClick()
        compose.onNodeWithText("When did you get up?").assertIsDisplayed()
        compose.onNodeWithText("Close night").performClick()
        assertEquals(LocalTime.of(6, 0), closedAt)
    }
}
