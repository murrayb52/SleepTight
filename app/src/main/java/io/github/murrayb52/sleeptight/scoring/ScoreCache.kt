package io.github.murrayb52.sleeptight.scoring

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What `Night.scoreJson` holds: the score, its breakdown and the measurements behind it, so screens
 * can show a night without recomputing. It's a cache: [NightScorer] can rebuild it from stored data
 * at any time, and does whenever data or goals change or [VERSION] goes up.
 */
@Serializable
data class ScoreCache(
    val version: Int = VERSION,
    /** Null when the night has no sleep data to score. */
    val total: Int?,
    /** "GARMIN", "ESTIMATED" or null (no sleep data). */
    val source: String?,
    val deepRemRedistributed: Boolean,
    val parts: List<Part>,
    val metrics: Metrics,
    val regularity: Factors,
    /**
     * The phone's own measurements from overnight sound, when the score used Garmin's stages
     * instead; null otherwise (without Garmin, [metrics] already are the phone's). Since version 3.
     */
    val phone: Metrics? = null,
) {
    @Serializable
    data class Part(val component: String, val points: Double, val maxPoints: Double)

    @Serializable
    data class Metrics(
        val timeInBedMinutes: Long,
        val totalSleepMinutes: Long?,
        val timeToFallAsleepMinutes: Long?,
        val wakeUps: Int?,
        val efficiency: Double?,
        val deepShare: Double?,
        val remShare: Double?,
        val deepRemShare: Double?,
        val noiseEvents: Int,
        val noiseClusters: Int,
    )

    @Serializable
    data class Factors(val bedtime: Double, val duration: Double?, val snoozes: Double, val consistency: Double?)

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        /** Bump when the scoring changes, so every cached score is rebuilt on the next app start. */
        const val VERSION = 3

        /** Every cache JSON starts with this, which lets the database find out-of-date ones. */
        const val PREFIX = "{\"version\":$VERSION,"

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Parses a cached score; null for none, an old version, or anything unreadable. */
        fun decode(text: String?): ScoreCache? =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }?.takeIf { it.version == VERSION }
    }
}
