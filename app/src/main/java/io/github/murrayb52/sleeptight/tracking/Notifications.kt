package io.github.murrayb52.sleeptight.tracking

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.murrayb52.sleeptight.MainActivity
import io.github.murrayb52.sleeptight.R

object Notifications {
    const val TRACKING_ID = 1
    private const val STOPPED_ID = 2
    private const val CHANNEL_TRACKING = "tracking"
    private const val CHANNEL_ALERTS = "alerts"

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
    }

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
