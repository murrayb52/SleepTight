package io.github.murrayb52.sleeptight.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.data.Settings

@Composable
fun NotificationSettingsRoute(onBack: () -> Unit, viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotificationSettingsScreen(
        state.settings,
        onBack = onBack,
        onReminder = viewModel::onReminder,
        onBedsideCheck = viewModel::onBedsideCheck,
        onEarplugsDefault = viewModel::onEarplugsDefault,
    )
}

/** What the app tells or asks you: the bedtime reminder, and what happens when you tap to go to bed. */
@Composable
fun NotificationSettingsScreen(
    settings: Settings,
    onBack: () -> Unit = {},
    onReminder: (Boolean) -> Unit = {},
    onBedsideCheck: (Boolean) -> Unit = {},
    onEarplugsDefault: (Boolean) -> Unit = {},
) {
    SettingsPage(stringResource(R.string.settings_notifications), onBack) {
        Section(null) {
            ToggleRow(
                stringResource(R.string.reminder_row),
                settings.bedtimeReminderEnabled,
                onReminder,
                detail = stringResource(R.string.reminder_detail),
                last = true,
            )
        }
        Section(stringResource(R.string.section_at_bed_tap)) {
            ToggleRow(stringResource(R.string.bedside_row), settings.bedsideCheckEnabled, onBedsideCheck, detail = stringResource(R.string.bedside_detail))
            ToggleRow(
                stringResource(R.string.earplugs_default_row),
                settings.earplugsDefault,
                onEarplugsDefault,
                detail = stringResource(R.string.earplugs_default_detail),
                last = true,
            )
        }
    }
}
