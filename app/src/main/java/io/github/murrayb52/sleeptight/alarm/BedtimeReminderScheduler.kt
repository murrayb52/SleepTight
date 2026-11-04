package io.github.murrayb52.sleeptight.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.metrics.BedtimeReminder
import java.time.Clock
import java.time.LocalTime

/** Keeps one pending alarm for the next bedtime reminder (30 minutes before the bedtime goal). */
class BedtimeReminderScheduler(private val context: Context, private val clock: Clock) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(settings: Settings) {
        val intent = pendingIntent()
        alarmManager.cancel(intent)
        if (!settings.bedtimeReminderEnabled) return
        val bedtime = LocalTime.MIDNIGHT.plusMinutes(settings.targetBedtimeMinutes.toLong())
        val at = BedtimeReminder.next(clock.instant(), clock.zone, bedtime)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
            Log.i(TAG, "Bedtime reminder set for $at")
        } catch (e: SecurityException) {
            // Exact alarms off (Android 12 only): a reminder a few minutes late is still useful.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        }
    }

    private fun pendingIntent() = PendingIntent.getBroadcast(
        context,
        REQUEST_REMINDER,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_BEDTIME_REMINDER),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val TAG = "BedtimeReminder"
        const val REQUEST_REMINDER = 13
    }
}
