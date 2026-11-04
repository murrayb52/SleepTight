package io.github.murrayb52.sleeptight.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.murrayb52.sleeptight.SleepTightApplication
import io.github.murrayb52.sleeptight.tracking.Notifications
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Receives the scheduled alarms, plus reboots and clock changes (which clear or shift alarms). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as SleepTightApplication).container
        val nightId = intent.getLongExtra(AlarmScheduler.EXTRA_NIGHT_ID, 0)
        when (intent.action) {
            ACTION_WINDOW_OPEN -> {
                Log.i(TAG, "Wake window opened for night $nightId")
                container.alarmEvents.onWakeWindowOpened(nightId)
            }
            ACTION_RING -> {
                Log.i(TAG, "Ring for night $nightId")
                // Starting a foreground service from an exact alarm is allowed in the background.
                ContextCompat.startForegroundService(
                    context,
                    AlarmRingService.intent(context, AlarmRingService.ACTION_RING).putExtra(AlarmScheduler.EXTRA_NIGHT_ID, nightId),
                )
            }
            ACTION_BEDTIME_REMINDER -> {
                val pending = goAsync()
                container.appScope.launch {
                    try {
                        val repo = container.nightRepository
                        val settings = repo.getSettings()
                        // Already in bed: no need to remind.
                        if (settings.bedtimeReminderEnabled && repo.getOpenNight() == null) {
                            val bedtime = LocalTime.MIDNIGHT.plusMinutes(settings.targetBedtimeMinutes.toLong())
                            val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
                            Notifications.showBedtimeReminder(context, DateTimeFormatter.ofPattern(pattern).format(bedtime))
                        }
                        container.bedtimeReminders.schedule(settings) // tomorrow's
                    } finally {
                        pending.finish()
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.i(TAG, "${intent.action}: re-arming any pending alarm and the bedtime reminder")
                val pending = goAsync()
                container.appScope.launch {
                    try {
                        container.alarmController.rearmOpenNight()
                        container.bedtimeReminders.schedule(container.nightRepository.getSettings())
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_RING = "io.github.murrayb52.sleeptight.action.RING"
        const val ACTION_WINDOW_OPEN = "io.github.murrayb52.sleeptight.action.WINDOW_OPEN"
        const val ACTION_BEDTIME_REMINDER = "io.github.murrayb52.sleeptight.action.BEDTIME_REMINDER"
    }
}
