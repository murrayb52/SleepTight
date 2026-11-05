package io.github.murrayb52.sleeptight.diary

import java.time.Duration

/** When the screen turning on in the night should ask for a wake note. */
object WakePromptRule {
    /** Not while still settling: picking up the phone just after the bed tap isn't a waking. */
    val AFTER_BED: Duration = Duration.ofMinutes(15)

    /** At most one prompt this often, so checking the time twice doesn't ask twice. */
    val EVERY: Duration = Duration.ofMinutes(10)

    /** How long the prompt stays up (notification and sheet) if it's ignored. */
    val SHOW_FOR: Duration = Duration.ofMinutes(15)

    /**
     * @param alarmIdle false while the alarm rings or is snoozed: that waking has its own buttons.
     * @param lastPromptAt the previous prompt's time, or 0 if none.
     */
    fun shouldPrompt(at: Long, bedTapAt: Long, alarmIdle: Boolean, lastPromptAt: Long): Boolean =
        alarmIdle && at - bedTapAt >= AFTER_BED.toMillis() && at - lastPromptAt >= EVERY.toMillis()

    /** Whether a prompt from [promptAt] is still worth showing at [now]. */
    fun stillFresh(promptAt: Long, now: Long): Boolean = now - promptAt < SHOW_FOR.toMillis()
}
