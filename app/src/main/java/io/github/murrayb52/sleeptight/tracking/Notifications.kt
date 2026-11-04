package io.github.murrayb52.sleeptight.tracking

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.text.format.DateFormat
import io.github.murrayb52.sleeptight.MainActivity
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.alarm.AlarmActivity
import io.github.murrayb52.sleeptight.alarm.AlarmRingService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Notifications {
    const val TRACKING_ID = 1
    private const val STOPPED_ID = 2
    const val ALARM_ID = 3
    private const val SNOOZED_ID = 4
    private const val CHANNEL_TRACKING = "tracking"
    private const val CHANNEL_ALERTS = "alerts"
    private const val CHANNEL_ALARM = "alarm"

    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_TRACKING, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.channel_tracking))
                .setDescription(context.getString(R.string.channel_tracking_description))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_alerts))
                .setDescription(context.getString(R.string.channel_alerts_description))
                .build(),
        )
        // The ring service plays the sound itself (with the volume ramp), so the channel is silent.
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALARM, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_alarm))
                .setDescription(context.getString(R.string.channel_alarm_description))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .build(),
        )
    }

    /** The ringing alarm: opens the full-screen alarm, with Snooze and Dismiss actions. */
    fun alarmRinging(context: Context): Notification {
        val fullScreen = PendingIntent.getActivity(
            context,
            REQUEST_ALARM_SCREEN,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_tracking)
            .setContentTitle(context.getString(R.string.alarm_notification_title))
            .setContentText(context.getString(R.string.alarm_good_morning))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .addAction(0, context.getString(R.string.alarm_snooze_short), serviceAction(context, AlarmRingService.ACTION_SNOOZE))
            .addAction(0, context.getString(R.string.alarm_dismiss), serviceAction(context, AlarmRingService.ACTION_DISMISS))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun showSnoozed(context: Context, until: Instant) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        val time = DateTimeFormatter.ofPattern(pattern).format(until.atZone(ZoneId.systemDefault()))
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_tracking)
            .setContentTitle(context.getString(R.string.alarm_snoozed_title, time))
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .addAction(0, context.getString(R.string.alarm_dismiss), serviceAction(context, AlarmRingService.ACTION_DISMISS))
            .build()
        try {
            manager.notify(SNOOZED_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked; the snooze still happens.
        }
    }

    fun cancelSnoozed(context: Context) = NotificationManagerCompat.from(context).cancel(SNOOZED_ID)

    private fun serviceAction(context: Context, action: String) = PendingIntent.getService(
        context,
        action.hashCode(),
        AlarmRingService.intent(context, action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private const val REQUEST_ALARM_SCREEN = 20

    fun tracking(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_stat_tracking)
            .setContentTitle(context.getString(R.string.notification_tracking_title))
            .setContentText(context.getString(R.string.notification_tracking_text))
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /** Tells the user tracking stopped, if notifications are allowed. The Tonight view shows it too. */
    fun showTrackingStopped(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_tracking)
            .setContentTitle(context.getString(R.string.notification_stopped_title))
            .setContentText(context.getString(R.string.notification_stopped_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_stopped_text)))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(STOPPED_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; the Tonight view still shows it.
        }
    }

    fun cancelTrackingStopped(context: Context) = NotificationManagerCompat.from(context).cancel(STOPPED_ID)

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
