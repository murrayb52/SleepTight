package io.github.murrayb52.sleeptight.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.murrayb52.sleeptight.SleepTightApplication
import io.github.murrayb52.sleeptight.data.NightRepository
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.health.GarminImporter
import io.github.murrayb52.sleeptight.health.HealthConnectGarminSource
import io.github.murrayb52.sleeptight.health.SourceStatus
import io.github.murrayb52.sleeptight.metrics.AlarmConfig
import io.github.murrayb52.sleeptight.metrics.SnoozeCurve
import io.github.murrayb52.sleeptight.ui.history.Averages
import io.github.murrayb52.sleeptight.ui.history.averagesOf
import io.github.murrayb52.sleeptight.ui.history.toGoals
import io.github.murrayb52.sleeptight.ui.history.toRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Garmin's connection, as Profile shows it. [denied] is set after the permission request came back without both permissions. */
data class GarminUi(val status: SourceStatus? = null, val denied: Boolean = false)

data class ProfileUi(
    val loading: Boolean = true,
    val settings: Settings = Settings(),
    val allTime: Averages? = null,
    val garmin: GarminUi = GarminUi(),
)

class ProfileViewModel(
    private val repository: NightRepository,
    private val importer: GarminImporter? = null,
    private val appScope: CoroutineScope? = null,
) : ViewModel() {
    private val garmin = MutableStateFlow(GarminUi())

    val state: StateFlow<ProfileUi> = combine(repository.closedNights, repository.noiseCounts, repository.settings, garmin) { nights, noise, settings, g ->
        ProfileUi(false, settings, averagesOf(nights.mapNotNull { it.toRow(noise) }, settings.toGoals()), g)
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
    fun onEarplugsDefault(on: Boolean) = update { it.copy(earplugsDefault = on) }
    fun onSound(key: String?) = update { it.copy(alarmSound = key) }
    fun onSnoozesAllowed(n: Int) = update { it.copy(snoozesAllowed = n.coerceIn(0, AlarmConfig().maxSnoozesSetting)) }
    fun onSnoozeCurve(curve: SnoozeCurve) = update { it.copy(snoozeCurve = curve.name) }

    /** Re-reads Garmin's connection; called whenever Profile comes into view (e.g. back from Health Connect). */
    fun refreshGarmin() {
        val importer = importer ?: return
        viewModelScope.launch { garmin.value = garmin.value.copy(status = importer.status()) }
    }

    /** The Health Connect permission screen closed with [granted]. Imports recent nights once connected. */
    fun onGarminPermissions(granted: Set<String>) {
        val importer = importer ?: return
        viewModelScope.launch {
            val ok = granted.containsAll(HealthConnectGarminSource.PERMISSIONS)
            garmin.value = GarminUi(status = importer.status(), denied = !ok)
            // Outlives this screen: the import and rescoring can take a few seconds.
            if (ok) appScope?.launch { importer.importRecent() }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SleepTightApplication
                ProfileViewModel(app.container.nightRepository, app.container.garminImporter, app.container.appScope)
            }
        }
    }
}
