package io.github.murrayb52.sleeptight.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

fun readDeviceState(context: Context): DeviceState {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return DeviceState(
        micGranted = granted(Manifest.permission.RECORD_AUDIO),
        notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            granted(Manifest.permission.POST_NOTIFICATIONS),
        batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
        isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
    )
}

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
