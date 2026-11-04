package io.github.murrayb52.sleeptight.tracking

import io.github.murrayb52.sleeptight.metrics.NoiseChange

/**
 * Extension point for things that react to noise as it happens. v1 uses [None]. The planned
 * opt-in loud-moment clips (see the plan's "Later" list) will implement this.
 */
interface NoiseEvents {
    fun onNoise(nightId: Long, change: NoiseChange)

    object None : NoiseEvents {
        override fun onNoise(nightId: Long, change: NoiseChange) = Unit
    }
}
