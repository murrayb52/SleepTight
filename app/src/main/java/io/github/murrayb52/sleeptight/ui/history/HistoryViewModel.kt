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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import io.github.murrayb52.sleeptight.data.CaffeineEntry
import io.github.murrayb52.sleeptight.data.MorningSurvey
import io.github.murrayb52.sleeptight.data.NightContext
import io.github.murrayb52.sleeptight.data.WakeNote
import io.github.murrayb52.sleeptight.diary.toDose
import io.github.murrayb52.sleeptight.metrics.CAFFEINE_LOOKBACK
import io.github.murrayb52.sleeptight.metrics.caffeineAt
import java.time.Instant
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
    /** The phone's own asleep/awake estimate from sound, when the score used Garmin's stages instead. */
    val phoneStages: List<SleepStageSegment> = emptyList(),
    /** Offer "Check for Garmin data": the night has no Garmin stages yet. */
    val canCheckGarmin: Boolean = false,
    val checkingGarmin: Boolean = false,
    /** The last check's outcome, shown under the button; null before any check. */
    val garminCheck: GarminImporter.Result? = null,
    /** Lowest and average heart rate in the night, from Garmin; null without heart-rate data. */
    val heartRate: HeartRateUi? = null,
    val diary: DiaryUi = DiaryUi(),
)

/** The night's diary: bed-tap context, wake notes, the morning survey and caffeine at bedtime. */
data class DiaryUi(
    val context: NightContext? = null,
    val wakeNotes: List<WakeNote> = emptyList(),
    val survey: MorningSurvey? = null,
    /** Caffeine left at the bed tap, from drinks in the day before it. */
    val caffeineAtBedMg: Double = 0.0,
    val drinks: List<CaffeineEntry> = emptyList(),
)

data class HeartRateUi(val lowest: Int, val average: Int)

class NightDetailViewModel(
    private val repository: NightRepository,
    private val nightId: Long,
    private val importer: GarminImporter? = null,
) : ViewModel() {
    private val check = MutableStateFlow<Pair<Boolean, GarminImporter.Result?>>(false to null)

    val state: StateFlow<NightDetailUi> = combine(
        repository.night(nightId),
        repository.noiseEvents(nightId),
        repository.settings,
        combine(repository.stageSegments(nightId), repository.heartRate(nightId), ::Pair),
        combine(check, diary(), ::Pair),
    ) { night, events, settings, (segments, hr), (checkState, diary) ->
        val (checking, result) = checkState
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
            phoneStages = if (source == StageSource.GARMIN) segments.filter { it.source == StageSource.ESTIMATED } else emptyList(),
            canCheckGarmin = importer != null && night?.wakeTapAt != null && score?.source != "GARMIN",
            checkingGarmin = checking,
            garminCheck = result,
            heartRate = hr.takeIf { it.isNotEmpty() }?.let { s -> HeartRateUi(s.minOf { it.bpm }, Math.round(s.map { it.bpm }.average()).toInt()) },
            diary = diary,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NightDetailUi())

    private fun diary(): Flow<DiaryUi> {
        val drinks = combine(repository.night(nightId), repository.settings, ::Pair).map { (night, settings) ->
            if (night == null) return@map 0.0 to emptyList()
            val bed = Instant.ofEpochMilli(night.bedTapAt)
            val list = repository.caffeineBetween(bed.minus(CAFFEINE_LOOKBACK), bed.plusMillis(1))
            caffeineAt(list.map { it.toDose() }, bed, settings.caffeineHalfLifeHours) to list
        }
        return combine(repository.context(nightId), repository.wakeNotes(nightId), repository.survey(nightId), drinks) { context, notes, survey, (mg, list) ->
            DiaryUi(context, notes, survey, mg, list)
        }
    }

    /** Deletes this night and everything recorded in it, then calls [onDeleted]. */
    fun onDelete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteNight(nightId)
            onDeleted()
        }
    }

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
