package io.github.murrayb52.sleeptight.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.murrayb52.sleeptight.SleepTightApplication
import io.github.murrayb52.sleeptight.data.NightRepository
import io.github.murrayb52.sleeptight.data.NoiseEvent
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.metrics.Goals
import io.github.murrayb52.sleeptight.metrics.NightDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Duration
import java.time.LocalTime

fun Settings.toGoals() = Goals(
    bedtime = LocalTime.MIDNIGHT.plusMinutes(targetBedtimeMinutes.toLong()),
    sleep = Duration.ofMinutes(targetSleepMinutes.toLong()),
    maxSnoozes = maxSnoozes,
)

class HistoryViewModel(repository: NightRepository, private val clock: Clock) : ViewModel() {
    private val range = MutableStateFlow(HistoryRange.WEEK)

    val state: StateFlow<HistoryUi> = combine(repository.closedNights, repository.noiseCounts, repository.settings, range) { nights, noise, settings, r ->
        buildHistory(nights, noise, settings.toGoals(), r, NightDate.of(clock.instant(), clock.zone))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())

    fun onRange(r: HistoryRange) {
        range.value = r
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SleepTightApplication
                HistoryViewModel(app.container.nightRepository, app.container.clock)
            }
        }
    }
}

data class NightDetailUi(val loading: Boolean = true, val night: NightRowUi? = null, val events: List<NoiseEvent> = emptyList(), val alarmFiredAt: Long? = null, val windowStart: Long? = null, val windowEnd: Long? = null)

class NightDetailViewModel(repository: NightRepository, nightId: Long) : ViewModel() {
    val state: StateFlow<NightDetailUi> = combine(repository.night(nightId), repository.noiseEvents(nightId)) { night, events ->
        NightDetailUi(
            loading = false,
            night = night?.toRow(mapOf(nightId to events.size)),
            events = events,
            alarmFiredAt = night?.alarmFiredAt,
            windowStart = night?.alarmWindowStart,
            windowEnd = night?.alarmWindowEnd,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NightDetailUi())

    companion object {
        fun factory(nightId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SleepTightApplication
                NightDetailViewModel(app.container.nightRepository, nightId)
            }
        }
    }
}
