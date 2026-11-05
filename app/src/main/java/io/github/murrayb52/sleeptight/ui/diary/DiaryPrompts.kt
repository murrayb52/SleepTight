package io.github.murrayb52.sleeptight.ui.diary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.murrayb52.sleeptight.AppContainer
import io.github.murrayb52.sleeptight.data.MorningSurvey
import io.github.murrayb52.sleeptight.data.Night
import io.github.murrayb52.sleeptight.data.WakeNote
import io.github.murrayb52.sleeptight.diary.WakePromptRule
import io.github.murrayb52.sleeptight.tracking.Notifications
import io.github.murrayb52.sleeptight.ui.home.rememberTimeFormatter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** The morning survey is offered for this long after I'm up. */
val SURVEY_OFFERED_FOR: Duration = Duration.ofHours(12)

/** Which night, if any, the morning survey should be offered for automatically. */
fun surveyNightFor(latestClosed: Night?, openNight: Night?, putOff: Set<Long>, now: Long): Long? {
    val night = latestClosed ?: return null
    val wake = night.wakeTapAt ?: return null
    return night.id.takeIf { openNight == null && now - wake in 0 until SURVEY_OFFERED_FOR.toMillis() && it !in putOff }
}

/**
 * The diary's pop-ups over the whole app: the wake note while a night is open, and the morning
 * survey after one ends. Both are skippable with one tap.
 */
@Composable
fun DiaryPrompts(container: AppContainer) {
    val context = LocalContext.current
    val repository = container.nightRepository
    val open by repository.openNight.collectAsStateWithLifecycle(null)
    val wakeAt by container.wakePromptAt.collectAsStateWithLifecycle()
    val time = rememberTimeFormatter()

    val openNight = open
    val promptAt = wakeAt
    if (openNight != null && promptAt != null && WakePromptRule.stillFresh(promptAt, container.clock.millis())) {
        val close = {
            container.wakePromptAt.value = null
            Notifications.cancelWakePrompt(context)
        }
        DiarySheet(onDismiss = close) {
            WakeNoteContent(
                at = time.format(Instant.ofEpochMilli(promptAt).atZone(container.clock.zone)),
                onSave = { reasons, note ->
                    container.appScope.launch {
                        repository.addWakeNote(
                            WakeNote(nightId = openNight.id, at = promptAt, notedAt = container.clock.millis(), reasons = WakeNote.encode(reasons), note = note),
                        )
                    }
                    close()
                },
                onNotNow = close,
            )
        }
        return
    }

    val latest by repository.latestClosedNight.collectAsStateWithLifecycle(null)
    val putOff by container.surveyPutOff.collectAsStateWithLifecycle()
    val requested by container.surveyRequested.collectAsStateWithLifecycle()
    val nightId = requested ?: surveyNightFor(latest, openNight, putOff, container.clock.millis()) ?: return

    // Loaded flag first, so the sheet doesn't flash up for a night that already has its answers.
    val answers = remember(nightId) {
        combine(repository.survey(nightId), repository.context(nightId), repository.settings) { survey, ctx, settings ->
            Triple(true, survey, ctx?.earplugs ?: settings.earplugsDefault)
        }
    }
    val loaded by answers.collectAsStateWithLifecycle(Triple(false, null, true))
    val (ready, survey, earplugs) = loaded
    if (!ready || (survey != null && requested == null)) return

    val close = {
        if (requested == null) container.surveyPutOff.value = container.surveyPutOff.value + nightId
        container.surveyRequested.value = null
    }
    DiarySheet(onDismiss = close) {
        MorningSurveyContent(
            earplugs = earplugs,
            onSave = { a ->
                container.appScope.launch {
                    repository.saveSurvey(
                        MorningSurvey(
                            nightId = nightId,
                            answeredAt = container.clock.millis(),
                            rested = a.rested,
                            quality = a.quality,
                            wakings = a.wakings,
                            minutesToFallAsleep = a.minutesToFallAsleep,
                            minutesBeforeTrying = a.minutesBeforeTrying,
                            earplugAdjustments = a.earplugAdjustments,
                            notes = a.notes,
                        ),
                    )
                }
                container.surveyPutOff.value = container.surveyPutOff.value + nightId
                container.surveyRequested.value = null
            },
            onLater = close,
        )
    }
}

