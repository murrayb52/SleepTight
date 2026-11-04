package io.github.murrayb52.sleeptight.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.SleepTightApplication
import io.github.murrayb52.sleeptight.data.NightRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalTime

class HomeViewModel(
    private val repository: NightRepository,
    private val clock: Clock,
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

    val state: StateFlow<HomeUiState> = combine(
        repository.openNight,
        repository.latestClosedNight,
        repository.settings,
        minuteTicker,
        promptDismissedFor,
    ) { open, last, settings, now, dismissed ->
        buildHomeUiState(
            openNight = open,
            lastNight = last,
            now = now,
            zone = clock.zone,
            targetSleep = Duration.ofMinutes(settings.targetSleepMinutes.toLong()),
            promptDismissedForNightId = dismissed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onGoingToBed() {
        viewModelScope.launch { repository.goToBed() }
    }

    fun onImUp() {
        viewModelScope.launch {
            try {
                repository.wakeUp()
            } catch (e: IllegalArgumentException) {
                // Only possible if the phone's clock moved back past the bed tap.
                _message.value = R.string.error_wake_before_bed
            }
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
        viewModelScope.launch { repository.wakeUp(wake) }
    }

    fun onMessageShown() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SleepTightApplication
                HomeViewModel(app.container.nightRepository, app.container.clock)
            }
        }
    }
}
