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

/**
 * A scored night: the cache to store, plus the phone's own asleep/awake estimate from sound, to
 * store (replacing any older one) whether or not Garmin data was used for the score. Empty when
 * there's no sound to estimate from.
 */
data class ScoredNight(val cache: ScoreCache, val estimatedSegments: List<SleepStageSegment>)

/**
 * Scores one closed night from its stored data. Pure: the database work is in [NightScorer].
 *
 * Garmin segments win for the score. Asleep/awake is always estimated from [sound] levels too (when
 * there are any) and returned so the caller can store it: without Garmin it is what's scored, and
 * with Garmin its measurements go in [ScoreCache.phone], so the phone's own record of the night
 * can still be seen (2026-11-09). [history] is other closed nights, for the consistency factor.
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
    val fromSound = SleepEstimator.estimate(activityPerMinute(sound), bed, up)
    val estimated = fromSound.map {
        SleepStageSegment(nightId = night.id, start = it.start.toEpochMilli(), end = it.end.toEpochMilli(), stage = it.stage.toEntity(), source = StageSource.ESTIMATED)
    }
    val used: List<SleepSegment>
    val source: SleepDataSource?
    if (garmin.isNotEmpty()) {
        used = garmin.map { it.toMetrics() }
        source = SleepDataSource.GARMIN
    } else {
        used = fromSound
        source = if (used.isEmpty()) null else SleepDataSource.ESTIMATED
    }

    val noisePeriods = noise.map { NoisePeriod(Instant.ofEpochMilli(it.start), Instant.ofEpochMilli(it.end), it.peakDbRelative.toDouble()) }
    val metrics = NightMetrics.of(NightInput(bed, up, used, source, noisePeriods))
    // The phone's own measurements, kept beside Garmin's. Without Garmin they're the main ones already.
    val phone = if (source == SleepDataSource.GARMIN && fromSound.isNotEmpty()) {
        NightMetrics.of(NightInput(bed, up, fromSound, SleepDataSource.ESTIMATED, noisePeriods)).toCache()
    } else {
        null
    }
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
        metrics = metrics.toCache(),
        regularity = ScoreCache.Factors(regularity.bedtime, regularity.duration, regularity.snoozes, regularity.consistency),
        phone = phone,
    )
    return ScoredNight(cache, estimated)
}

private fun NightMetrics.toCache() = ScoreCache.Metrics(
    timeInBedMinutes = timeInBed.toMinutes(),
    totalSleepMinutes = totalSleep?.toMinutes(),
    timeToFallAsleepMinutes = timeToFallAsleep?.toMinutes(),
    wakeUps = wakeUps,
    efficiency = efficiency,
    deepShare = deepShare,
    remShare = remShare,
    deepRemShare = deepRemShare,
    noiseEvents = noiseEvents.size,
    noiseClusters = noiseClusters,
)

private fun SleepStageSegment.toMetrics() = SleepSegment(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end), Stage.valueOf(stage.name))

private fun Stage.toEntity() = SleepStage.valueOf(name)
