package io.github.murrayb52.sleeptight.metrics

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Which calendar date a night belongs to.
 *
 * A night is named after the evening it started, so a 00:30 bed tap counts towards the previous
 * date. Anything before [DAY_STARTS_AT] (local time) is treated as still being the previous
 * evening's night.
 */
object NightDate {
    /** Bed taps before this local time belong to the previous date's night. */
    val DAY_STARTS_AT: LocalTime = LocalTime.NOON

    fun of(bedTapAt: Instant, zone: ZoneId): LocalDate {
        val local = bedTapAt.atZone(zone)
        val date = local.toLocalDate()
        return if (local.toLocalTime() < DAY_STARTS_AT) date.minusDays(1) else date
    }
}
