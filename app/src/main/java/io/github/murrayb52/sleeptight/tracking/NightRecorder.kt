package io.github.murrayb52.sleeptight.tracking

import io.github.murrayb52.sleeptight.data.NoiseEvent
import io.github.murrayb52.sleeptight.data.SoundSample
import io.github.murrayb52.sleeptight.metrics.NoiseChange
import io.github.murrayb52.sleeptight.metrics.NoiseEventDetector
import io.github.murrayb52.sleeptight.metrics.RollingMedian
import io.github.murrayb52.sleeptight.metrics.SoundConfig
import io.github.murrayb52.sleeptight.metrics.SoundLevel
import java.time.Instant

/** Where the recorder writes. Implemented over Room in the app and by a fake in tests. */
interface RecordingStore {
    suspend fun saveSamples(samples: List<SoundSample>)
    suspend fun insertNoiseEvent(event: NoiseEvent): Long
    suspend fun updateNoiseEvent(event: NoiseEvent)
}

/** One second's reading, for the live display. */
data class LiveLevel(
    val at: Instant,
    val dbfs: Double,
    val baselineDbfs: Double,
    val dbRelative: Double,
    /** True after [SILENT_SECONDS_FOR_WARNING] seconds of digital silence: the mic is probably blocked. */
    val micSilent: Boolean,
)

/**
 * The overnight sound pipeline for one night, minus the microphone: takes a level per second and
 * turns it into stored relative samples and noise events. No audio passes through here, only levels.
 *
 * Samples are written in batches of [flushEvery] to save battery and I/O. Noise events are
 * written as soon as they start and updated as they grow, so the Tonight view sees them at once.
 */
class NightRecorder(
    private val nightId: Long,
    private val store: RecordingStore,
    private val hooks: NoiseEvents = NoiseEvents.None,
    private val config: SoundConfig = SoundConfig(),
    private val flushEvery: Int = 30,
) {
    private val baseline = RollingMedian(config.baselineWindowSeconds)
    private val detector = NoiseEventDetector(config)
    private val pending = ArrayList<SoundSample>(flushEvery)
    private var currentEventId: Long? = null
    private var silentSeconds = 0

    /**
     * Records the level of the second starting at [at]. The baseline is the median of the
     * seconds *before* this one, so a loud second can't raise its own baseline.
     */
    suspend fun onLevel(at: Instant, dbfs: Double): LiveLevel {
        val base = baseline.median ?: dbfs
        val relative = dbfs - base
        baseline.add(dbfs)
        silentSeconds = if (dbfs <= SoundLevel.FLOOR_DBFS) silentSeconds + 1 else 0

        pending += SoundSample(nightId, at.toEpochMilli(), relative.toFloat())
        detector.onSample(at, relative)?.let { change ->
            val row = NoiseEvent(
                id = 0,
                nightId = nightId,
                start = change.event.start.toEpochMilli(),
                end = change.event.end.toEpochMilli(),
                peakDbRelative = change.event.peakDbRelative.toFloat(),
            )
            val id = currentEventId
            when {
                change is NoiseChange.Extended && id != null -> store.updateNoiseEvent(row.copy(id = id))
                else -> currentEventId = store.insertNoiseEvent(row)
            }
            hooks.onNoise(nightId, change)
        }
        if (pending.size >= flushEvery) flush()

        return LiveLevel(at, dbfs, base, relative, micSilent = silentSeconds >= SILENT_SECONDS_FOR_WARNING)
    }

    /** Writes any batched samples. Call when the night ends or the service stops. */
    suspend fun flush() {
        if (pending.isEmpty()) return
        val batch = pending.toList()
        pending.clear()
        store.saveSamples(batch)
    }

    companion object {
        const val SILENT_SECONDS_FOR_WARNING = 10
    }
}
