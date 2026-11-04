package io.github.murrayb52.sleeptight.alarm

import java.time.Instant

/** What the alarm is doing right now, shared between the ring service, the alarm screen and Home. */
sealed interface AlarmState {
    data object Idle : AlarmState
    data class Ringing(val nightId: Long, val since: Instant) : AlarmState
    data class Snoozed(val nightId: Long, val until: Instant) : AlarmState
}
