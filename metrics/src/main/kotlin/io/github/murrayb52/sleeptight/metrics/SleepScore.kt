package io.github.murrayb52.sleeptight.metrics

enum class ScoreComponent { DURATION, EFFICIENCY, DEEP_REM, INTERRUPTIONS, REGULARITY }

/** One bar of the breakdown: [points] out of [maxPoints], where [fraction] = points / maxPoints. */
data class ComponentScore(
    val component: ScoreComponent,
    val fraction: Double,
    val maxPoints: Double,
) {
    val points: Double get() = fraction * maxPoints
}

/**
 * The 0–100 sleep score and its breakdown. When the night has no deep/REM data (not from
 * Garmin), that component is left out and its weight is spread proportionally across the others
 * ([deepRemRedistributed]), so the maximum is still 100.
 */
data class SleepScore(
    val total: Int,
    val components: List<ComponentScore>,
    val deepRemRedistributed: Boolean,
) {
    fun component(c: ScoreComponent): ComponentScore? = components.firstOrNull { it.component == c }

    companion object {
        /** Returns null if the night has no sleep data to score. */
        fun of(
            metrics: NightMetrics,
            regularity: Regularity,
            goals: Goals = Goals(),
            config: ScoreConfig = ScoreConfig(),
        ): SleepScore? {
            val totalSleep = metrics.totalSleep ?: return null
            val efficiency = metrics.efficiency ?: return null
            val wakeUps = metrics.wakeUps ?: return null

            val deepRem = metrics.deepRemShare
            val redistributed = deepRem == null
            val scale = if (redistributed) config.totalWeight / (config.totalWeight - config.deepRemWeight) else 1.0

            val penalty = config.pointsPerWakeUp * wakeUps + config.pointsPerNoiseCluster * metrics.noiseClusters
            val components = listOfNotNull(
                ComponentScore(
                    ScoreComponent.DURATION,
                    (totalSleep.toMillis().toDouble() / goals.sleep.toMillis()).coerceIn(0.0, 1.0),
                    config.durationWeight * scale,
                ),
                ComponentScore(
                    ScoreComponent.EFFICIENCY,
                    ramp(efficiency, config.efficiencyZeroAt, config.efficiencyFullAt),
                    config.efficiencyWeight * scale,
                ),
                deepRem?.let {
                    ComponentScore(ScoreComponent.DEEP_REM, ramp(it, config.deepRemZeroAt, config.deepRemFullAt), config.deepRemWeight)
                },
                ComponentScore(
                    ScoreComponent.INTERRUPTIONS,
                    (1.0 - penalty / config.interruptionsWeight).coerceAtLeast(0.0),
                    config.interruptionsWeight * scale,
                ),
                ComponentScore(ScoreComponent.REGULARITY, regularity.mean.coerceIn(0.0, 1.0), config.regularityWeight * scale),
            )
            return SleepScore(Math.round(components.sumOf { it.points }).toInt(), components, redistributed)
        }
    }
}
