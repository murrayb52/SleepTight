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
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Imports Garmin sleep and heart rate for closed nights, trims them to the taps, stores them and
 * rescores the night. Garmin only writes to Health Connect after the watch syncs, so this runs after
 * I'm up, again on app start for recent nights still waiting, and when asked from the night's page.
 */
class GarminImporter(
    private val db: SleepTightDatabase,
    private val source: GarminSource,
    private val scorer: NightScorer,
    private val clock: Clock,
) {
    enum class Result { IMPORTED, NO_DATA, NOT_CONNECTED, FAILED }

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
            if (segments.isEmpty()) return Result.NO_DATA
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
            Log.i(TAG, "Night $nightId: imported ${segments.size} Garmin segments and ${heartRate.size} heart-rate samples")
            Result.IMPORTED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Permission revoked mid-read, Health Connect busy or rate-limited: try again later.
            Log.w(TAG, "Night $nightId: Garmin import failed", e)
            Result.FAILED
        }
    }

    /** Tries recent nights (closed in the last [RETRY_FOR]) that don't have Garmin data yet. */
    suspend fun importRecent() {
        if (status() != SourceStatus.READY) return
        val since = clock.instant().minus(RETRY_FOR).toEpochMilli()
        db.nightDao().closedNightIdsAwaitingGarmin(since).forEach { import(it) }
    }

    private companion object {
        const val TAG = "GarminImporter"
        val MARGIN: Duration = Duration.ofHours(12)
        val RETRY_FOR: Duration = Duration.ofDays(3)
    }
}
