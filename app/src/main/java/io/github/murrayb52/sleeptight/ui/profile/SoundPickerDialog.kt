package io.github.murrayb52.sleeptight.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.alarm.AlarmPlayer
import io.github.murrayb52.sleeptight.alarm.AlarmSound
import io.github.murrayb52.sleeptight.ui.theme.Palette

/** Volume for previews: enough to judge the sound without the full alarm blast. */
private const val PREVIEW_VOLUME = 0.5f

/**
 * Picks the alarm sound. Tapping a sound selects it and plays a preview on the alarm stream (so it
 * follows the alarm volume); the preview stops on another tap or when the dialog closes.
 */
@Composable
fun SoundPickerDialog(current: AlarmSound, onPick: (String?) -> Unit, onDismiss: () -> Unit, preview: Boolean = true) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(current) }
    var player by remember { mutableStateOf<AlarmPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release() } }

    fun choose(sound: AlarmSound) {
        selected = sound
        onPick(sound.key)
        if (preview) {
            player?.release()
            player = runCatching { AlarmPlayer.create(context, sound).apply { start(PREVIEW_VOLUME) } }.getOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alarm_sound_row)) },
        text = {
            Column(Modifier.selectableGroup()) {
                AlarmSound.entries.forEach { sound ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(selected = sound == selected, role = Role.RadioButton) { choose(sound) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = sound == selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Palette.Ember))
                        Text(stringResource(sound.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).heightIn(min = 24.dp))
                    }
                }
                Text(stringResource(R.string.sound_preview_hint), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        containerColor = Palette.Card,
        titleContentColor = Palette.Text,
        textContentColor = Palette.TextSoft,
    )
}
