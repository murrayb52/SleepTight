package io.github.murrayb52.sleeptight.scoring

import android.util.Log
import androidx.room.withTransaction
import io.github.murrayb52.sleeptight.data.SleepTightDatabase
import io.github.murrayb52.sleeptight.data.StageSource
import io.github.murrayb52.sleeptight.metrics.LevelSample
import io.github.murrayb52.sleeptight.ui.history.toGoals
import io.github.murrayb52.sleeptight.data.Settings
import java.time.Duration
import java.time.Instant

/**
 * Keeps each closed night's cached score up to date. Scores are always derivable from stored data,
 * so this can run any time: after I'm up, on app start, after a Garmin import, or when goals change.
 */
class NightScorer(private val db: SleepTightDatabase) {
    private val nights = db.nightDao()
    private val segments = db.sleepStageSegmentDao()

    /** Rescores one night. Returns the new cache, or null if the night isn't closed. */
    suspend fun rescore(nightId: Long): ScoreCache? {
        val night = nights.get(nightId) ?: return null
        val wake = night.wakeTapAt ?: return null
        val goals = (db.settingsDao().get() ?: Settings()).toGoals()
        // Consistency looks back 7 nights; fetch a little more so time zones can't trim the edge.
        val history = nights.getClosedNightsBetween(night.bedTapAt - Duration.ofDays(9).toMillis(), night.bedTapAt)
        val stored = segments.getForNight(nightId)
        // The sound table is large, so only read it when there's no Garmin data and an estimate is needed.
        val sound = if (stored.any { it.source == StageSource.GARMIN }) {
            emptyList()
        } else {
            db.soundSampleDao().getForNight(nightId).map { LevelSample(Instant.ofEpochMilli(it.timestamp), it.dbRelative.toDouble()) }
        }
        val scored = scoreNight(night, stored, db.noiseEventDao().getForNight(nightId), history, goals, sound)
        db.withTransaction {
            scored.estimatedSegments?.let {
                segments.deleteForNight(nightId, StageSource.ESTIMATED)
                segments.insertAll(it)
            }
            nights.setScoreJson(nightId, scored.cache.encode())
        }
        Log.i(TAG, "Night $nightId (${Instant.ofEpochMilli(wake)}) scored ${scored.cache.total} from ${scored.cache.source}")
        return scored.cache
    }

    /** Scores nights that have no score yet, or one from an older scoring version. */
    suspend fun rescoreStale() = nights.closedNightIdsNeedingScore(ScoreCache.PREFIX).forEach { rescore(it) }

    /** Rescores every closed night, for example after the goals change. */
    suspend fun rescoreAll() = nights.closedNightIds().forEach { rescore(it) }

    private companion object {
        const val TAG = "NightScorer"
    }
}
