package io.github.murrayb52.sleeptight.scoring

import io.github.murrayb52.sleeptight.data.Night
import io.github.murrayb52.sleeptight.data.NoiseEvent
import io.github.murrayb52.sleeptight.data.SleepStage
import io.github.murrayb52.sleeptight.data.SleepStageSegment
import io.github.murrayb52.sleeptight.data.StageSource
import io.github.murrayb52.sleeptight.metrics.BedTap
import io.github.murrayb52.sleeptight.metrics.Goals
import io.github.murrayb52.sleeptight.metrics.LevelSample
import io.github.murrayb52.sleeptight.metrics.NightInput
import io.github.murrayb52.sleeptight.metrics.NightMetrics
import io.github.murrayb52.sleeptight.metrics.NoisePeriod
import io.github.murrayb52.sleeptight.metrics.Regularity
import io.github.murrayb52.sleeptight.metrics.SleepDataSource
import io.github.murrayb52.sleeptight.metrics.SleepEstimator
import io.github.murrayb52.sleeptight.metrics.SleepScore
import io.github.murrayb52.sleeptight.metrics.SleepSegment
import io.github.murrayb52.sleeptight.metrics.Stage
import io.github.murrayb52.sleeptight.metrics.activityPerMinute
import java.time.Instant
import java.time.ZoneId

/** A scored night: the cache to store, plus estimated segments when there was no Garmin data. */
data class ScoredNight(val cache: ScoreCache, val estimatedSegments: List<SleepStageSegment>?)

/**
 * Scores one closed night from its stored data. Pure: the database work is in [NightScorer].
 *
 * Garmin segments win. Without them, asleep/awake is estimated from [sound] levels (when there are
 * any) and returned so the caller can store it; [sound] is ignored when Garmin segments exist, so
 * the caller can skip loading it. [history] is other closed nights, for the consistency factor.
 */
fun scoreNight(
    night: Night,
    segments: List<SleepStageSegment>,
    noise: List<NoiseEvent>,
    history: List<Night>,
    goals: Goals,
    sound: List<LevelSample>,
): ScoredNight {
    val wake = requireNotNull(night.wakeTapAt) { "Only closed nights are scored" }
    val zone = ZoneId.of(night.zoneId)
    val bed = Instant.ofEpochMilli(night.bedTapAt)
    val up = Instant.ofEpochMilli(wake)

    val garmin = segments.filter { it.source == StageSource.GARMIN }
    val estimated: List<SleepStageSegment>?
    val used: List<SleepSegment>
    val source: SleepDataSource?
    if (garmin.isNotEmpty()) {
        used = garmin.map { it.toMetrics() }
        source = SleepDataSource.GARMIN
        estimated = null
    } else {
        used = SleepEstimator.estimate(activityPerMinute(sound), bed, up)
        source = if (used.isEmpty()) null else SleepDataSource.ESTIMATED
        estimated = used.map {
            SleepStageSegment(nightId = night.id, start = it.start.toEpochMilli(), end = it.end.toEpochMilli(), stage = it.stage.toEntity(), source = StageSource.ESTIMATED)
        }
    }

    val metrics = NightMetrics.of(
        NightInput(bed, up, used, source, noise.map { NoisePeriod(Instant.ofEpochMilli(it.start), Instant.ofEpochMilli(it.end), it.peakDbRelative.toDouble()) }),
    )
    val regularity = Regularity.of(
        bedTapAt = bed,
        zone = zone,
        totalSleep = metrics.totalSleep,
        snoozeCount = night.snoozeCount,
        recentBedTaps = history.filter { it.id != night.id }.map { BedTap(Instant.ofEpochMilli(it.bedTapAt), ZoneId.of(it.zoneId)) },
        goals = goals,
    )
    val score = SleepScore.of(metrics, regularity, goals)

    val cache = ScoreCache(
        total = score?.total,
        source = source?.name,
        deepRemRedistributed = score?.deepRemRedistributed ?: false,
        parts = score?.components.orEmpty().map { ScoreCache.Part(it.component.name, it.points, it.maxPoints) },
        metrics = ScoreCache.Metrics(
            timeInBedMinutes = metrics.timeInBed.toMinutes(),
            totalSleepMinutes = metrics.totalSleep?.toMinutes(),
            timeToFallAsleepMinutes = metrics.timeToFallAsleep?.toMinutes(),
            wakeUps = metrics.wakeUps,
            efficiency = metrics.efficiency,
            deepShare = metrics.deepShare,
            remShare = metrics.remShare,
            deepRemShare = metrics.deepRemShare,
            noiseEvents = metrics.noiseEvents.size,
            noiseClusters = metrics.noiseClusters,
        ),
        regularity = ScoreCache.Factors(regularity.bedtime, regularity.duration, regularity.snoozes, regularity.consistency),
    )
    return ScoredNight(cache, estimated)
}

private fun SleepStageSegment.toMetrics() = SleepSegment(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end), Stage.valueOf(stage.name))

private fun Stage.toEntity() = SleepStage.valueOf(name)
