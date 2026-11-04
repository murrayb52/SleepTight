package io.github.murrayb52.sleeptight.tracking

import kotlinx.coroutines.flow.StateFlow

/** What the overnight sound monitoring is doing right now, for the UI. */
data class TrackingStatus(
    val state: State = State.IDLE,
    val nightId: Long? = null,
    val live: LiveLevel? = null,
    val problem: Problem? = null,
    /**
     * The level of the last 100 ms relative to the room's baseline, for the live display only (it's
     * updated ten times a second; what's stored is [live], once a second).
     */
    val displayDbRelative: Double? = null,
) {
    enum class State { IDLE, STARTING, RECORDING, STOPPED_WITH_PROBLEM }

    enum class Problem {
        /** Microphone permission isn't granted. */
        MIC_PERMISSION,

        /** AudioRecord couldn't start or failed mid-night. */
        MIC_UNAVAILABLE,

        /** Android refused to start the foreground service (for example, after being killed in the background). */
        SERVICE_NOT_ALLOWED,
    }
}

/** Starts and stops overnight sound monitoring. Implemented by [ServiceNightTracker]. */
interface NightTracker {
    val status: StateFlow<TrackingStatus>
    fun start(nightId: Long)
    fun stop()
}
