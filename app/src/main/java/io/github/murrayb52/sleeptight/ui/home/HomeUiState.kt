package io.github.murrayb52.sleeptight.ui.home

import io.github.murrayb52.sleeptight.data.Night
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** After this long, an open night is probably a forgotten "I'm up" tap, so Home asks about it. */
val OPEN_NIGHT_PROMPT_AFTER: Duration = Duration.ofHours(16)

data class HomeUiState(
    val loading: Boolean = true,
    /** Zone used to show times: the device's current zone. */
    val zone: ZoneId = ZoneId.systemDefault(),
    val openNight: OpenNightUi? = null,
    val lastNight: LastNightUi? = null,
    val overduePrompt: OverduePrompt? = null,
)

data class OpenNightUi(val nightId: Long, val bedTapAt: Instant, val inBedFor: Duration)

data class LastNightUi(val bedTapAt: Instant, val wakeTapAt: Instant, val timeInBed: Duration)

/** Shown when the open night has been open for at least [OPEN_NIGHT_PROMPT_AFTER]. */
data class OverduePrompt(
    val nightId: Long,
    val bedTapAt: Instant,
    val openFor: Duration,
    /** Pre-filled wake time for the picker: bed tap plus the sleep goal. */
    val suggestedWakeTime: LocalTime,
)

fun buildHomeUiState(
    openNight: Night?,
    lastNight: Night?,
    now: Instant,
    zone: ZoneId,
    targetSleep: Duration,
    promptDismissedForNightId: Long?,
): HomeUiState {
    val open = openNight?.let {
        val bed = Instant.ofEpochMilli(it.bedTapAt)
        OpenNightUi(it.id, bed, Duration.between(bed, now).coerceAtLeast(Duration.ZERO))
    }
    val last = lastNight?.wakeTapAt?.let { wake ->
        val bed = Instant.ofEpochMilli(lastNight.bedTapAt)
        val up = Instant.ofEpochMilli(wake)
        LastNightUi(bed, up, Duration.between(bed, up))
    }
    val prompt = open
        ?.takeIf { it.inBedFor >= OPEN_NIGHT_PROMPT_AFTER && it.nightId != promptDismissedForNightId }
        ?.let { OverduePrompt(it.nightId, it.bedTapAt, it.inBedFor, it.bedTapAt.plus(targetSleep).atZone(zone).toLocalTime()) }
    return HomeUiState(loading = false, zone = zone, openNight = open, lastNight = last, overduePrompt = prompt)
}

/**
 * Turns a wake time picked on a clock face into an instant: the first time that clock time
 * occurs after [bedTapAt]. Returns null if that is still in the future.
 */
fun resolveWakeTime(bedTapAt: Instant, time: LocalTime, zone: ZoneId, now: Instant): Instant? {
    val bed = bedTapAt.atZone(zone)
    var candidate = bed.toLocalDate().atTime(time).atZone(zone)
    if (!candidate.isAfter(bed)) candidate = bed.toLocalDate().plusDays(1).atTime(time).atZone(zone)
    val instant = candidate.toInstant()
    return instant.takeUnless { it.isAfter(now) }
}
