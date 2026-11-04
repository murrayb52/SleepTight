package io.github.murrayb52.sleeptight.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.murrayb52.sleeptight.SleepTightApplication
import io.github.murrayb52.sleeptight.data.NightRepository
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.ui.history.Averages
import io.github.murrayb52.sleeptight.ui.history.averagesOf
import io.github.murrayb52.sleeptight.ui.history.toGoals
import io.github.murrayb52.sleeptight.ui.history.toRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

data class ProfileUi(val loading: Boolean = true, val settings: Settings = Settings(), val allTime: Averages? = null)

class ProfileViewModel(private val repository: NightRepository) : ViewModel() {
    val state: StateFlow<ProfileUi> = combine(repository.closedNights, repository.noiseCounts, repository.settings) { nights, noise, settings ->
        ProfileUi(false, settings, averagesOf(nights.mapNotNull { it.toRow(noise) }, settings.toGoals()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUi())

    private fun update(change: (Settings) -> Settings) {
        viewModelScope.launch { repository.updateSettings(change(repository.getSettings())) }
    }

    fun onBedtime(t: LocalTime) = update { it.copy(targetBedtimeMinutes = t.hour * 60 + t.minute) }
    fun onSleepGoal(minutes: Int) = update { it.copy(targetSleepMinutes = minutes) }
    fun onSnoozeGoal(n: Int) = update { it.copy(maxSnoozes = n) }
    fun onWindow(minutes: Int) = update { it.copy(alarmWindowStartMinutes = Math.floorMod(it.alarmWindowEndMinutes - minutes, 24 * 60)) }
    fun onReminder(on: Boolean) = update { it.copy(bedtimeReminderEnabled = on) }
    fun onBedsideCheck(on: Boolean) = update { it.copy(bedsideCheckEnabled = on) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SleepTightApplication
                ProfileViewModel(app.container.nightRepository)
            }
        }
    }
}
