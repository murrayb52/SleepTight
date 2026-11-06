package io.github.murrayb52.sleeptight.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.media.AudioManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

fun readDeviceState(context: Context): DeviceState {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return DeviceState(
        micGranted = granted(Manifest.permission.RECORD_AUDIO),
        // Covers both the Android 13+ permission and the app's switch in Settings.
        notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
        isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
        alarmVolumeOff = context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) == 0,
        exactAlarmsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        fullScreenAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
    )
}

/** Android 12 only: the "Alarms & reminders" setting for this app. */
fun exactAlarmSettingsIntent(context: Context) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    } else {
        appSettingsIntent(context)
    }

/** Android 14+: the full-screen notification setting for this app. */
fun fullScreenSettingsIntent(context: Context) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
    } else {
        appSettingsIntent(context)
    }

/** This app's notification settings, where notifications can be switched back on. */
fun notificationSettingsIntent(context: Context) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** Permissions asked for at the first bed tap. */
val bedTimePermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }

// Allowed for this app: it's a sleep tracker whose core function needs to run overnight.
@SuppressLint("BatteryLife")
fun batteryExemptionIntent(context: Context) =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

fun appSettingsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
