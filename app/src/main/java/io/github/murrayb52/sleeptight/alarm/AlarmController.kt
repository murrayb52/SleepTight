package io.github.murrayb52.sleeptight.alarm

import android.content.Context
import android.util.Log
import io.github.murrayb52.sleeptight.data.Night
import io.github.murrayb52.sleeptight.data.NightRepository
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.metrics.WakeWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Arms and disarms a night's alarm: works out the window, stores it on the night and schedules it. */
class AlarmController(
    private val context: Context,
    private val repository: NightRepository,
    private val scheduler: AlarmScheduler,
    private val mutableState: MutableStateFlow<AlarmState>,
    private val clock: Clock,
) {
    val state: StateFlow<AlarmState> = mutableState

    fun canScheduleExact() = scheduler.canScheduleExact()

    /**
     * Arms the alarm for [night] (every bed tap does; a night's alarm can then be turned off from
     * the in-bed screen). Returns the window, or null if exact alarms aren't allowed.
     */
    suspend fun arm(night: Night, settings: Settings): WakeWindow? {
        val window = WakeWindow.after(
            Instant.ofEpochMilli(night.bedTapAt),
            ZoneId.of(night.zoneId),
            LocalTime.of(0, 0).plusMinutes(settings.alarmWindowStartMinutes.toLong()),
            LocalTime.of(0, 0).plusMinutes(settings.alarmWindowEndMinutes.toLong()),
        )
        if (!scheduler.schedule(night.id, window)) return null
        repository.setAlarmWindow(night.id, window.start, window.end)
        return window
    }

    /** Cancels any pending ring and stops one that's ringing or snoozed. */
    suspend fun disarm(nightId: Long?) {
        scheduler.cancel()
        if (nightId != null) {
            val night = repository.getNight(nightId)
            // Only clear the window if it never rang, so the night keeps a record of a real alarm.
            if (night?.alarmFiredAt == null) repository.setAlarmWindow(nightId, null, null)
        }
        if (mutableState.value != AlarmState.Idle) {
            context.startService(AlarmRingService.intent(context, AlarmRingService.ACTION_DISMISS))
        }
    }

    /** After a reboot or clock change: re-arms the open night's alarm if it hasn't rung yet. */
    suspend fun rearmOpenNight() {
        val night = repository.getOpenNight()
        val end = night?.alarmWindowEnd
        if (night == null || end == null || night.alarmFiredAt != null || end <= clock.millis()) {
            Log.i(TAG, "Nothing to re-arm (open night ${night?.id}, window end $end, fired ${night?.alarmFiredAt})")
            return
        }
        val ok = scheduler.schedule(night.id, WakeWindow(Instant.ofEpochMilli(night.alarmWindowStart ?: end), Instant.ofEpochMilli(end)))
        Log.i(TAG, "Re-armed night ${night.id} for ${Instant.ofEpochMilli(end)}: $ok")
    }

    fun snooze() = context.startService(AlarmRingService.intent(context, AlarmRingService.ACTION_SNOOZE))

    /** The user stopped the alarm from the Sleep tab. The night stays open until I'm up. */
    fun stop() = context.startService(AlarmRingService.intent(context, AlarmRingService.ACTION_DISMISS))

    /** The smart trigger fired: ring now instead of at the end of the window. */
    fun ringNow(nightId: Long) = scheduler.scheduleRing(nightId, clock.instant().plusSeconds(1))

    private companion object {
        const val TAG = "AlarmController"
    }
}
