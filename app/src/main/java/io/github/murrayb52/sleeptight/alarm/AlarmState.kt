package io.github.murrayb52.sleeptight.alarm

import java.time.Instant

/** What the alarm is doing right now, shared between the ring service, the alarm screen and Home. */
sealed interface AlarmState {
    data object Idle : AlarmState
    /**
     * @param nextSnoozeMinutes length of the next snooze, or null when none are left (or not yet known).
     * @param snoozesUsed snoozes so far tonight.
     */
    data class Ringing(
        val nightId: Long,
        val since: Instant,
        val nextSnoozeMinutes: Int? = null,
        val snoozesUsed: Int = 0,
    ) : AlarmState
    data class Snoozed(val nightId: Long, val until: Instant) : AlarmState
}
