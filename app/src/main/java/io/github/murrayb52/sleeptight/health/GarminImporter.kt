package io.github.murrayb52.sleeptight.health

import android.util.Log
import androidx.room.withTransaction
import io.github.murrayb52.sleeptight.data.HeartRateSample
import io.github.murrayb52.sleeptight.data.SleepStage
import io.github.murrayb52.sleeptight.data.SleepStageSegment
import io.github.murrayb52.sleeptight.data.SleepTightDatabase
import io.github.murrayb52.sleeptight.data.StageSource
import io.github.murrayb52.sleeptight.metrics.trimToNight
import io.github.murrayb52.sleeptight.scoring.NightScorer
import io.github.murrayb52.sleeptight.scoring.ScoreCache
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Imports Garmin sleep and heart rate for closed nights, trims them to the taps, stores them and
 * rescores the night. Garmin only writes to Health Connect after the watch syncs (sometimes an hour
 * after you get up) and can rewrite a night later, so this runs after I'm up, again on app start
 * for every recent night (picking up rewrites too), and when asked from the night's page.
 */
class GarminImporter(
    private val db: SleepTightDatabase,
    private val source: GarminSource,
    private val scorer: NightScorer,
    private val clock: Clock,
) {
    enum class Result {
        /** Garmin data stored for the first time. */
        IMPORTED,

        /** Garmin rewrote a night that was already imported; the new version replaced it. */
        UPDATED,

        /** Already imported and Garmin hasn't changed it. */
        UNCHANGED,
        NO_DATA,
        NOT_CONNECTED,
        FAILED,
    }

    /** A night whose Garmin data changed after it was first imported, with its score either side. */
    data class Update(val nightId: Long, val bedTapAt: Instant, val zone: ZoneId, val scoreBefore: Int?, val scoreAfter: Int?)

    suspend fun status(): SourceStatus = runCatching { source.status() }.getOrDefault(SourceStatus.UNAVAILABLE)

    suspend fun import(nightId: Long): Result {
        val night = db.nightDao().get(nightId) ?: return Result.NO_DATA
        val wake = night.wakeTapAt ?: return Result.NO_DATA
        if (status() != SourceStatus.READY) return Result.NOT_CONNECTED
        val bed = Instant.ofEpochMilli(night.bedTapAt)
        val up = Instant.ofEpochMilli(wake)
        return try {
            // A session can start before the bed tap or end after the wake tap: read a margin, then trim.
            val segments = trimToNight(source.sleep(bed.minus(MARGIN), up.plus(MARGIN)), bed, up)
            if (segments.isEmpty()) {
                Log.i(TAG, "Night $nightId: no Garmin sleep between $bed and $up")
                return if (night.garminImported) Result.UNCHANGED else Result.NO_DATA
            }
            if (night.garminImported) {
                val stored = db.sleepStageSegmentDao().getForNight(nightId)
                    .filter { it.source == StageSource.GARMIN }
                    .map { Triple(it.start, it.end, it.stage.name) }
                if (stored == segments.map { Triple(it.start.toEpochMilli(), it.end.toEpochMilli(), it.stage.name) }) return Result.UNCHANGED
            }
            val heartRate = source.heartRate(bed, up).filter { !it.at.isBefore(bed) && it.at.isBefore(up) }
            db.withTransaction {
                db.sleepStageSegmentDao().deleteForNight(nightId, StageSource.GARMIN)
                db.sleepStageSegmentDao().insertAll(
                    segments.map {
                        SleepStageSegment(
                            nightId = nightId,
                            start = it.start.toEpochMilli(),
                            end = it.end.toEpochMilli(),
                            stage = SleepStage.valueOf(it.stage.name),
                            source = StageSource.GARMIN,
                        )
                    },
                )
                db.heartRateSampleDao().deleteForNight(nightId)
                db.heartRateSampleDao().insertAll(heartRate.map { HeartRateSample(nightId, it.at.toEpochMilli(), it.bpm) })
                db.nightDao().setGarminImported(nightId, true)
            }
            scorer.rescore(nightId)
            Log.i(TAG, "Night $nightId: imported ${segments.size} Garmin segments and ${heartRate.size} heart-rate samples (update: ${night.garminImported})")
            if (night.garminImported) Result.UPDATED else Result.IMPORTED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Permission revoked mid-read, Health Connect busy or rate-limited: try again later.
            Log.w(TAG, "Night $nightId: Garmin import failed", e)
            Result.FAILED
        }
    }

    /**
     * Imports recent nights (closed in the last [RETRY_FOR]): new Garmin data, and rewrites of nights
     * already imported. Returns the rewrites, so the app can say a night's score changed.
     */
    suspend fun importRecent(): List<Update> {
        if (status() != SourceStatus.READY) return emptyList()
        val since = clock.instant().minus(RETRY_FOR).toEpochMilli()
        return db.nightDao().closedNightIdsSince(since).mapNotNull { id ->
            val before = db.nightDao().get(id) ?: return@mapNotNull null
            if (import(id) != Result.UPDATED) return@mapNotNull null
            val after = db.nightDao().get(id)
            Update(id, Instant.ofEpochMilli(before.bedTapAt), ZoneId.of(before.zoneId), ScoreCache.decode(before.scoreJson)?.total, ScoreCache.decode(after?.scoreJson)?.total)
        }
    }

    private companion object {
        const val TAG = "GarminImporter"
        val MARGIN: Duration = Duration.ofHours(12)
        val RETRY_FOR: Duration = Duration.ofDays(3)
    }
}
