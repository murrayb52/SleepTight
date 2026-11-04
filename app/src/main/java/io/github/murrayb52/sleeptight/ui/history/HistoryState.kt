package io.github.murrayb52.sleeptight.ui.history

import io.github.murrayb52.sleeptight.data.Night
import io.github.murrayb52.sleeptight.metrics.Goals
import io.github.murrayb52.sleeptight.metrics.NightDate
import io.github.murrayb52.sleeptight.metrics.Regularity
import io.github.murrayb52.sleeptight.scoring.ScoreCache
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class HistoryRange(val days: Int) { WEEK(7), MONTH(30) }

data class NightRowUi(
    val id: Long,
    val date: LocalDate,
    val bedTapAt: Instant,
    val wakeTapAt: Instant,
    val zone: ZoneId,
    val timeInBed: Duration,
    val noiseEvents: Int,
    val snoozes: Int,
    /** Null until the night has sleep data to score. */
    val score: Int? = null,
)

data class DayUi(val date: LocalDate, val nightId: Long?, val score: Int?)

data class Averages(
    val nights: Int,
    val timeInBed: Duration,
    /** Average bed time, as a clock time (worked out relative to the bedtime goal, so midnight is fine). */
    val bedtime: LocalTime,
    /** Share of nights in bed by the bedtime goal, 0..100. */
    val byGoalPercent: Int,
    val snoozesPerNight: Double,
    val score: Int?,
)

data class HistoryUi(
    val loading: Boolean = true,
    val range: HistoryRange = HistoryRange.WEEK,
    val averages: Averages? = null,
    val week: List<DayUi> = emptyList(),
    val nights: List<NightRowUi> = emptyList(),
)

fun Night.toRow(noise: Map<Long, Int>): NightRowUi? {
    val wake = wakeTapAt ?: return null
    val zone = ZoneId.of(zoneId)
    val bed = Instant.ofEpochMilli(bedTapAt)
    val up = Instant.ofEpochMilli(wake)
    return NightRowUi(
        id, NightDate.of(bed, zone), bed, up, zone, Duration.between(bed, up), noise[id] ?: 0, snoozeCount,
        score = ScoreCache.decode(scoreJson)?.total,
    )
}

/** Averages over [rows], or null when there are none. */
fun averagesOf(rows: List<NightRowUi>, goals: Goals): Averages? {
    if (rows.isEmpty()) return null
    val late = rows.map { Regularity.minutesLate(it.bedTapAt, it.zone, goals) }
    val meanLate = late.average()
    val scores = rows.mapNotNull { it.score }
    return Averages(
        nights = rows.size,
        timeInBed = Duration.ofMillis(rows.map { it.timeInBed.toMillis() }.average().toLong()),
        bedtime = goals.bedtime.plusMinutes(Math.round(meanLate)),
        byGoalPercent = Math.round(100.0 * late.count { it <= 0 } / rows.size).toInt(),
        snoozesPerNight = rows.map { it.snoozes }.average(),
        score = if (scores.isEmpty()) null else Math.round(scores.average()).toInt(),
    )
}

/**
 * The History screen's content: closed nights in [range] ending with [today] (a night date, see
 * [NightDate]), their averages, and a strip of the last 7 dates.
 */
fun buildHistory(nights: List<Night>, noise: Map<Long, Int>, goals: Goals, range: HistoryRange, today: LocalDate): HistoryUi {
    val rows = nights.mapNotNull { it.toRow(noise) }.sortedByDescending { it.bedTapAt }
    val from = today.minusDays(range.days - 1L)
    val inRange = rows.filter { it.date in from..today }
    val week = (6 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        val night = rows.firstOrNull { it.date == date }
        DayUi(date, night?.id, night?.score)
    }
    return HistoryUi(loading = false, range = range, averages = averagesOf(inRange, goals), week = week, nights = inRange)
}
