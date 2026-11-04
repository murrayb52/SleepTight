package io.github.murrayb52.sleeptight.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.metrics.Goals
import io.github.murrayb52.sleeptight.metrics.ScoreComponent
import io.github.murrayb52.sleeptight.metrics.ScoreConfig
import io.github.murrayb52.sleeptight.ui.home.formatDuration
import io.github.murrayb52.sleeptight.ui.home.rememberTimeFormatter
import kotlin.math.roundToInt

/** Display name of a score part. */
@Composable
fun componentName(c: ScoreComponent): String = stringResource(
    when (c) {
        ScoreComponent.DURATION -> R.string.part_duration
        ScoreComponent.EFFICIENCY -> R.string.part_efficiency
        ScoreComponent.DEEP_REM -> R.string.part_deep_rem
        ScoreComponent.INTERRUPTIONS -> R.string.part_interruptions
        ScoreComponent.REGULARITY -> R.string.part_regularity
    },
)

/** Full points for a score part, before any redistribution. */
fun maxPoints(c: ScoreComponent, config: ScoreConfig = ScoreConfig()): Int = when (c) {
    ScoreComponent.DURATION -> config.durationWeight
    ScoreComponent.EFFICIENCY -> config.efficiencyWeight
    ScoreComponent.DEEP_REM -> config.deepRemWeight
    ScoreComponent.INTERRUPTIONS -> config.interruptionsWeight
    ScoreComponent.REGULARITY -> config.regularityWeight
}.roundToInt()

private fun pct(x: Double) = (x * 100).roundToInt()

/**
 * What a score part measures and how its points are earned. The numbers come from [ScoreConfig]
 * and the user's [goals], so the explanation always matches the maths.
 */
@Composable
fun componentExplainer(c: ScoreComponent, goals: Goals, config: ScoreConfig = ScoreConfig()): String {
    val time = rememberTimeFormatter()
    return when (c) {
        ScoreComponent.DURATION -> stringResource(R.string.info_duration_body, formatDuration(goals.sleep), maxPoints(c, config))
        ScoreComponent.EFFICIENCY -> stringResource(
            R.string.info_efficiency_body,
            pct(config.efficiencyZeroAt),
            maxPoints(c, config),
            pct(config.efficiencyFullAt),
            pct(io.github.murrayb52.sleeptight.metrics.ReferenceRanges.EFFICIENCY_MIN),
        )
        ScoreComponent.DEEP_REM -> stringResource(
            R.string.info_deep_rem_body,
            pct(config.deepRemZeroAt),
            maxPoints(c, config),
            pct(config.deepRemFullAt),
        )
        ScoreComponent.INTERRUPTIONS -> stringResource(
            R.string.info_interruptions_body,
            maxPoints(c, config),
            config.minWakeUp.toMinutes().toInt(),
            config.pointsPerWakeUp.roundToInt(),
            config.noiseClusterGap.toMinutes().toInt(),
            config.pointsPerNoiseCluster.roundToInt(),
        )
        ScoreComponent.REGULARITY -> stringResource(
            R.string.info_regularity_body,
            time.format(goals.bedtime),
            formatDuration(goals.sleep),
            goals.maxSnoozes,
            config.consistencyWindowNights,
            maxPoints(c, config),
        )
    }
}
