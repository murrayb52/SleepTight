package io.github.murrayb52.sleeptight.data

import io.github.murrayb52.sleeptight.tracking.RecordingStore

class RoomRecordingStore(db: SleepTightDatabase) : RecordingStore {
    private val samples = db.soundSampleDao()
    private val noise = db.noiseEventDao()

    override suspend fun saveSamples(samples: List<SoundSample>) = this.samples.insertAll(samples)
    override suspend fun insertNoiseEvent(event: NoiseEvent): Long = noise.insert(event)
    override suspend fun updateNoiseEvent(event: NoiseEvent) = noise.update(event)
}
