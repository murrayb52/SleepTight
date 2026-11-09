package io.github.murrayb52.sleeptight.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.alarm.AlarmSound
import io.github.murrayb52.sleeptight.health.HealthConnectGarminSource
import io.github.murrayb52.sleeptight.health.SourceStatus
import io.github.murrayb52.sleeptight.ui.history.Tile
import io.github.murrayb52.sleeptight.ui.home.formatDuration
import io.github.murrayb52.sleeptight.ui.home.rememberTimeFormatter
import io.github.murrayb52.sleeptight.ui.theme.Palette
import java.time.Duration
import java.time.LocalTime

/** The settings pages Profile leads to. */
enum class SettingsPageId { ALARM, NOTIFICATIONS, GOALS }

@Composable
fun ProfileRoute(
    onOpenCredits: () -> Unit,
    onOpenSettings: (SettingsPageId) -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val askHealthConnect = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.onGarminPermissions(it)
    }
    // Back from Health Connect (or anywhere else): the connection may have changed.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshGarmin()
        onPauseOrDispose {}
    }
    ProfileScreen(
        state,
        onOpenSettings = onOpenSettings,
        onOpenCredits = onOpenCredits,
        onGarmin = {
            when (state.garmin.status) {
                SourceStatus.UPDATE_REQUIRED -> context.startActivity(healthConnectInstallIntent())
                // After repeated refusals Health Connect stops asking, so send people to its settings instead.
                SourceStatus.NEEDS_PERMISSION ->
                    if (state.garmin.denied) {
                        context.startActivity(HealthConnectClient.getHealthConnectManageDataIntent(context))
                    } else {
                        askHealthConnect.launch(HealthConnectGarminSource.PERMISSIONS)
                    }
                SourceStatus.READY -> context.startActivity(HealthConnectClient.getHealthConnectManageDataIntent(context))
                SourceStatus.UNAVAILABLE, null -> Unit
            }
        },
    )
}

/** The Play Store page for Health Connect (needed on Android 13 and earlier, where it's a separate app). */
private fun healthConnectInstallIntent() = Intent(
    Intent.ACTION_VIEW,
    Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"),
).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * Profile: all-time stats, then the settings grouped into pages (Alarm, Notifications, Goals),
 * Garmin and export, and About. Each settings row shows a one-line summary of its page.
 */
@Composable
fun ProfileScreen(
    state: ProfileUi,
    onOpenSettings: (SettingsPageId) -> Unit = {},
    onOpenCredits: () -> Unit = {},
    onGarmin: () -> Unit = {},
) {
    val time = rememberTimeFormatter()
    val s = state.settings
    val bedtime = LocalTime.MIDNIGHT.plusMinutes(s.targetBedtimeMinutes.toLong())
    val window = Math.floorMod(s.alarmWindowEndMinutes - s.alarmWindowStartMinutes, 24 * 60)
    val context = LocalContext.current
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }

    Column(
        Modifier.fillMaxSize().background(Palette.Sky).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.tab_profile), style = MaterialTheme.typography.headlineLarge)
        val avg = state.allTime
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile((avg?.nights ?: 0).toString(), stringResource(R.string.stat_nights), Modifier.weight(1f))
            Tile(avg?.let { "${it.byGoalPercent} %" } ?: "–", stringResource(R.string.stat_by_goal, time.format(bedtime)), Modifier.weight(1f))
            Tile(avg?.let { "%.1f".format(it.snoozesPerNight) } ?: "–", stringResource(R.string.stat_snoozes), Modifier.weight(1f))
        }

        Section(stringResource(R.string.section_settings)) {
            NavRow(
                stringResource(R.string.settings_alarm),
                stringResource(
                    R.string.settings_alarm_summary,
                    stringResource(R.string.window_option, window),
                    stringResource(AlarmSound.of(s.alarmSound).label),
                    if (s.snoozesAllowed == 0) {
                        stringResource(R.string.snoozes_none_summary)
                    } else {
                        pluralStringResource(R.plurals.snooze_count_from, s.snoozesAllowed, s.snoozesAllowed, s.firstSnoozeMinutes)
                    },
                ),
                tag = "settingsAlarm",
            ) { onOpenSettings(SettingsPageId.ALARM) }
            NavRow(
                stringResource(R.string.settings_notifications),
                stringResource(if (s.bedtimeReminderEnabled) R.string.settings_notifications_summary_on else R.string.settings_notifications_summary_off),
                tag = "settingsNotifications",
            ) { onOpenSettings(SettingsPageId.NOTIFICATIONS) }
            NavRow(
                stringResource(R.string.settings_goals),
                stringResource(
                    R.string.settings_goals_summary,
                    time.format(bedtime),
                    formatDuration(Duration.ofMinutes(s.targetSleepMinutes.toLong())),
                    pluralStringResource(R.plurals.goal_row_snoozes, s.maxSnoozes, s.maxSnoozes),
                ),
                tag = "settingsGoals",
                last = true,
            ) { onOpenSettings(SettingsPageId.GOALS) }
        }
        Section(stringResource(R.string.section_data)) {
            val g = state.garmin
            ValueRow(
                stringResource(R.string.data_garmin),
                stringResource(
                    when (g.status) {
                        null -> R.string.garmin_checking
                        SourceStatus.UNAVAILABLE -> R.string.garmin_unavailable
                        SourceStatus.UPDATE_REQUIRED -> R.string.garmin_install
                        SourceStatus.NEEDS_PERMISSION -> if (g.denied) R.string.garmin_open_settings else R.string.garmin_connect
                        SourceStatus.READY -> R.string.garmin_connected
                    },
                ),
                enabled = g.status != null && g.status != SourceStatus.UNAVAILABLE,
                onClick = onGarmin,
            )
            ValueRow(stringResource(R.string.data_export), stringResource(R.string.coming_soon), enabled = false, last = true) {}
        }
        Text(
            stringResource(
                when {
                    state.garmin.denied && state.garmin.status == SourceStatus.NEEDS_PERMISSION -> R.string.garmin_denied_note
                    state.garmin.status == SourceStatus.READY -> R.string.garmin_connected_note
                    else -> R.string.garmin_note
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (state.garmin.denied && state.garmin.status == SourceStatus.NEEDS_PERMISSION) Palette.Poor else Palette.Muted,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
        )
        Section(stringResource(R.string.section_about)) {
            ValueRow(stringResource(R.string.credits_row), "", last = true, onClick = onOpenCredits)
        }
        Text(stringResource(R.string.version, version), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(start = 4.dp))
    }
}
