package io.github.murrayb52.sleeptight.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.github.murrayb52.sleeptight.MainActivity
import io.github.murrayb52.sleeptight.metrics.WakeWindow
import java.time.Instant

/**
 * Talks to AlarmManager. The ring uses `setAlarmClock`, which is exact, survives Doze and shows the
 * alarm icon in the status bar. The window-open event uses an exact idle-allowed alarm.
 */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** False only on Android 12 when the user has turned off "Alarms & reminders" for the app. */
    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Schedules the window-open event and the ring at the end of the window. Returns false if not allowed. */
    fun schedule(nightId: Long, window: WakeWindow): Boolean = try {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, window.start.toEpochMilli(), windowOpenIntent(nightId))
        scheduleRing(nightId, window.end)
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "Exact alarms not allowed", e)
        false
    }

    /** (Re)schedules the ring. Used for the window end, a smart early ring and snoozes. */
    fun scheduleRing(nightId: Long, at: Instant) {
        val info = AlarmManager.AlarmClockInfo(at.toEpochMilli(), openAppIntent())
        alarmManager.setAlarmClock(info, ringIntent(nightId))
    }

    fun cancel() {
        alarmManager.cancel(ringIntent(0))
        alarmManager.cancel(windowOpenIntent(0))
    }

    // PendingIntents match on action and request code, not extras, so cancel() finds them with any night id.
    private fun ringIntent(nightId: Long) = broadcast(AlarmReceiver.ACTION_RING, REQUEST_RING, nightId)
    private fun windowOpenIntent(nightId: Long) = broadcast(AlarmReceiver.ACTION_WINDOW_OPEN, REQUEST_WINDOW, nightId)

    private fun broadcast(action: String, requestCode: Int, nightId: Long) = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_NIGHT_ID, nightId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openAppIntent() = PendingIntent.getActivity(
        context,
        REQUEST_SHOW,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val TAG = "AlarmScheduler"
        const val EXTRA_NIGHT_ID = "nightId"
        private const val REQUEST_RING = 10
        private const val REQUEST_WINDOW = 11
        private const val REQUEST_SHOW = 12
    }
}
