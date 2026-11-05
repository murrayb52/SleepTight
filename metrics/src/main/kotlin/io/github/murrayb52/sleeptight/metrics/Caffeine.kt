package io.github.murrayb52.sleeptight.metrics

import java.time.Duration
import java.time.Instant
import kotlin.math.pow

/** One logged drink's caffeine. */
data class CaffeineDose(val at: Instant, val mg: Double)

/** Drinks further back than this are treated as gone (under 4 % is left after a day at 5 h). */
val CAFFEINE_LOOKBACK: Duration = Duration.ofHours(24)

/**
 * Caffeine still in the body at [at]: each drink in the [CAFFEINE_LOOKBACK] before it decays by
 * half every [halfLifeHours]. Drinks after [at] don't count. A rough estimate: real half-lives
 * vary from about 2 to 10 hours between people.
 */
fun caffeineAt(doses: List<CaffeineDose>, at: Instant, halfLifeHours: Double = 5.0): Double {
    require(halfLifeHours > 0) { "halfLifeHours must be positive" }
    val from = at.minus(CAFFEINE_LOOKBACK)
    return doses
        .filter { !it.at.isAfter(at) && !it.at.isBefore(from) }
        .sumOf { it.mg * 0.5.pow(Duration.between(it.at, at).toMillis() / 3_600_000.0 / halfLifeHours) }
}
