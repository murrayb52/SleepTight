package io.github.murrayb52.sleeptight.health

import io.github.murrayb52.sleeptight.metrics.SleepSegment
import java.time.Instant

/** Whether Garmin data can be read right now. */
enum class SourceStatus {
    /** Health Connect isn't available on this phone. */
    UNAVAILABLE,

    /** Health Connect needs installing or updating first. */
    UPDATE_REQUIRED,

    /** Health Connect is there, but SleepTight isn't allowed to read sleep and heart rate. */
    NEEDS_PERMISSION,

    READY,
}

data class HeartRatePoint(val at: Instant, val bpm: Int)

/**
 * Where Garmin data comes from. v1 reads it from Health Connect ([HealthConnectGarminSource]); the
 * unofficial Garmin API could implement this later without touching the importer.
 */
interface GarminSource {
    suspend fun status(): SourceStatus

    /** Sleep segments overlapping [from, to], untrimmed: the importer trims them to the taps. */
    suspend fun sleep(from: Instant, to: Instant): List<SleepSegment>

    suspend fun heartRate(from: Instant, to: Instant): List<HeartRatePoint>
}
