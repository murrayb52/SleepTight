package io.github.murrayb52.sleeptight.alarm

/**
 * Hooks for things that react to the alarm. v1 uses [None]; the planned Philips Hue sunrise will
 * implement [onWakeWindowOpened] to start a light ramp.
 */
interface AlarmEvents {
    fun onWakeWindowOpened(nightId: Long)
    fun onAlarmFired(nightId: Long)
    fun onSnooze(nightId: Long, snoozeCount: Int)
    fun onDismiss(nightId: Long)

    object None : AlarmEvents {
        override fun onWakeWindowOpened(nightId: Long) = Unit
        override fun onAlarmFired(nightId: Long) = Unit
        override fun onSnooze(nightId: Long, snoozeCount: Int) = Unit
        override fun onDismiss(nightId: Long) = Unit
    }
}
