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
import io.github.murrayb52.sleeptight.data.SleepStageSegment
import io.github.murrayb52.sleeptight.data.StageSource
import io.github.murrayb52.sleeptight.health.GarminImporter
import io.github.murrayb52.sleeptight.metrics.Goals
import io.github.murrayb52.sleeptight.metrics.NightDate
import io.github.murrayb52.sleeptight.scoring.ScoreCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

data class NightDetailUi(
    val loading: Boolean = true,
    val night: NightRowUi? = null,
    val events: List<NoiseEvent> = emptyList(),
    val alarmFiredAt: Long? = null,
    val goals: Goals = Goals(),
    /** The cached score and the measurements behind it; null until the night has been scored. */
    val score: ScoreCache? = null,
    val stages: List<SleepStageSegment> = emptyList(),
    /** Offer "Check for Garmin data": the night has no Garmin stages yet. */
    val canCheckGarmin: Boolean = false,
    val checkingGarmin: Boolean = false,
    /** The last check's outcome, shown under the button; null before any check. */
    val garminCheck: GarminImporter.Result? = null,
)

class NightDetailViewModel(
    repository: NightRepository,
    private val nightId: Long,
    private val importer: GarminImporter? = null,
) : ViewModel() {
    private val check = MutableStateFlow<Pair<Boolean, GarminImporter.Result?>>(false to null)

    val state: StateFlow<NightDetailUi> = combine(
        repository.night(nightId),
        repository.noiseEvents(nightId),
        repository.settings,
        repository.stageSegments(nightId),
        check,
    ) { night, events, settings, segments, (checking, result) ->
        val score = ScoreCache.decode(night?.scoreJson)
        // Show the segments the score used: Garmin's if there are any, otherwise the estimate.
        val source = if (score?.source == "GARMIN") StageSource.GARMIN else StageSource.ESTIMATED
        NightDetailUi(
            loading = false,
            night = night?.toRow(mapOf(nightId to events.size)),
            events = events,
            alarmFiredAt = night?.alarmFiredAt,
            goals = settings.toGoals(),
            score = score,
            stages = segments.filter { it.source == source },
            canCheckGarmin = importer != null && night?.wakeTapAt != null && score?.source != "GARMIN",
            checkingGarmin = checking,
            garminCheck = result,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NightDetailUi())

    /** Looks for Garmin data for this night now (it only appears after the watch syncs). */
    fun onCheckGarmin() {
        val importer = importer ?: return
        if (check.value.first) return
        viewModelScope.launch {
            check.value = true to check.value.second
            check.value = false to importer.import(nightId)
        }
    }

    companion object {
        fun factory(nightId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SleepTightApplication
                NightDetailViewModel(app.container.nightRepository, nightId, app.container.garminImporter)
            }
        }
    }
}
