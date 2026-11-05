package io.github.murrayb52.sleeptight.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.SleepTightApplication
import io.github.murrayb52.sleeptight.alarm.AlarmController
import io.github.murrayb52.sleeptight.data.NightRepository
import io.github.murrayb52.sleeptight.tracking.NightTracker
import io.github.murrayb52.sleeptight.tracking.TrackingStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: NightRepository,
    private val tracker: NightTracker,
    private val alarm: AlarmController,
    private val clock: Clock,
    private val onNightClosed: (Long) -> Unit = {},
) : ViewModel() {

    /** Ticks on each minute boundary so the "in bed for" time and the 16-hour check stay current. */
    private val minuteTicker = flow {
        while (true) {
            val now = clock.instant()
            emit(now)
            delay(60_000 - now.toEpochMilli() % 60_000)
        }
    }

    private val promptDismissedFor = MutableStateFlow<Long?>(null)

    private val _message = MutableStateFlow<Int?>(null)

    /** A one-off message (string resource) for the snackbar, or null. */
    val message: StateFlow<Int?> = _message.asStateFlow()

    private val nights = combine(repository.openNight, repository.settings, ::Pair)

    private val openNightNoise = repository.openNight
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.noiseEvents(id) }

    val state: StateFlow<HomeUiState> = combine(
        nights,
        minuteTicker,
        promptDismissedFor,
        combine(tracker.status, alarm.state, ::Pair),
        openNightNoise,
    ) { (open, settings), now, dismissed, (tracking, alarmState), noise ->
        buildHomeUiState(
            openNight = open,
            now = now,
            zone = clock.zone,
            targetSleep = Duration.ofMinutes(settings.targetSleepMinutes.toLong()),
            promptDismissedForNightId = dismissed,
        ).copy(
            tracking = tracking,
            noiseEvents = noise.map { it.toUi() }.sortedByDescending { it.start },
            alarm = alarmUi(settings, open, alarmState),
            goals = goalsUi(settings),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Opens the night; starts sound monitoring if the microphone is allowed. */
    fun onGoingToBed(trackSound: Boolean) {
        viewModelScope.launch {
            val night = repository.goToBed()
            if (trackSound) tracker.start(night.id)
            if (night.alarmWindowEnd == null) alarm.arm(night, repository.getSettings())
        }
    }

    /** Turns the alarm on or off; also arms or disarms tonight's if you're already in bed. */
    /** In bed: turns tonight's alarm on or off. Settings are untouched; the next bed tap arms as usual. */
    fun onAlarmToggled(enabled: Boolean) {
        viewModelScope.launch {
            val open = repository.getOpenNight() ?: return@launch
            if (enabled) alarm.arm(open, repository.getSettings()) else alarm.disarm(open.id)
        }
    }

    fun onSnoozeAlarm() {
        alarm.snooze()
    }

    /** The wake-by wheel settled on a new time: the window keeps its length and ends there. */
    fun onWakeByChanged(time: LocalTime) {
        viewModelScope.launch {
            val s = repository.getSettings()
            val end = time.hour * 60 + time.minute
            if (end == s.alarmWindowEndMinutes) return@launch
            val length = Math.floorMod(s.alarmWindowEndMinutes - s.alarmWindowStartMinutes, 24 * 60)
            repository.updateSettings(s.copy(alarmWindowEndMinutes = end, alarmWindowStartMinutes = Math.floorMod(end - length, 24 * 60)))
        }
    }

    fun onBedsideCheckDontShow() {
        viewModelScope.launch {
            repository.updateSettings(repository.getSettings().copy(bedsideCheckEnabled = false))
        }
    }

    fun onImUp() {
        viewModelScope.launch {
            try {
                val closed = repository.wakeUp()
                if (closed != null) {
                    tracker.stop()
                    alarm.disarm(closed.id)
                    if (closed.wakeTapAt!! - closed.bedTapAt < SHORTEST_NIGHT.toMillis()) {
                        // Going to bed and I'm up within minutes: a mis-tap, so don't keep it.
                        repository.deleteNight(closed)
                        _message.value = R.string.night_too_short
                    } else {
                        onNightClosed(closed.id)
                    }
                }
            } catch (e: IllegalArgumentException) {
                // Only possible if the phone's clock moved back past the bed tap.
                _message.value = R.string.error_wake_before_bed
            }
        }
    }

    /**
     * Called when the app comes to the foreground with the microphone allowed: restarts monitoring
     * for an open night if it isn't running, including after Android killed it in the background
     * (opening the app is when a restart is allowed again). A microphone failure isn't retried
     * automatically; that waits for the user to tap "Try again".
     */
    fun onEnsureTracking() {
        viewModelScope.launch {
            val open = repository.getOpenNight() ?: return@launch
            val status = tracker.status.value
            val stalled = status.state == TrackingStatus.State.IDLE ||
                status.problem == TrackingStatus.Problem.MIC_PERMISSION ||
                status.problem == TrackingStatus.Problem.SERVICE_NOT_ALLOWED
            if (stalled) tracker.start(open.id)
        }
    }

    fun onRestartTracking() {
        viewModelScope.launch {
            repository.getOpenNight()?.let { tracker.start(it.id) }
        }
    }

    fun onKeepNightOpen(nightId: Long) {
        promptDismissedFor.value = nightId
    }

    /** Closes the overdue night at a picked wake time. */
    fun onCloseNightAt(time: LocalTime) {
        val prompt = state.value.overduePrompt ?: return
        val wake = resolveWakeTime(prompt.bedTapAt, time, clock.zone, clock.instant())
        if (wake == null) {
            _message.value = R.string.error_wake_time_in_future
            return
        }
        viewModelScope.launch {
            repository.wakeUp(wake)?.let {
                alarm.disarm(it.id)
                onNightClosed(it.id)
            }
            tracker.stop()
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SleepTightApplication
                HomeViewModel(
                    app.container.nightRepository,
                    app.container.nightTracker,
                    app.container.alarmController,
                    app.container.clock,
                    app.container::onNightClosed,
                )
            }
        }
    }
}
