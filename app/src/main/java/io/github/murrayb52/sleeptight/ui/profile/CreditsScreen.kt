package io.github.murrayb52.sleeptight.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.alarm.AlarmSound
import io.github.murrayb52.sleeptight.ui.components.Panel
import io.github.murrayb52.sleeptight.ui.components.SectionLabel
import io.github.murrayb52.sleeptight.ui.theme.Palette

/** The font's licence text, bundled from the repo's `licences/` folder as an app asset. */
private const val FONT_LICENCE_ASSET = "Outfit-OFL.txt"

/**
 * Credits for everything third-party the app ships: the alarm sounds (from each sound's
 * [io.github.murrayb52.sleeptight.alarm.SoundCredit], so a bundled sound can't go uncredited) and
 * the typeface, whose full licence text can be read here.
 */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    var showLicence by rememberSaveable { mutableStateOf(false) }
    val backLabel = stringResource(R.string.back)

    Column(
        Modifier.fillMaxSize().background(Palette.Sky).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledIconButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = backLabel },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.Card, contentColor = Palette.Text),
            ) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
            Text(stringResource(R.string.credits_title), style = MaterialTheme.typography.headlineLarge)
        }
        Text(stringResource(R.string.credits_intro), style = MaterialTheme.typography.bodyLarge, color = Palette.TextSoft)

        SectionLabel(stringResource(R.string.credits_sounds))
        AlarmSound.entries.mapNotNull { s -> s.credit?.let { s to it } }.forEach { (sound, credit) ->
            Panel {
                Text(stringResource(sound.label), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.credits_sound_line, credit.title, credit.author), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSoft)
                Text(stringResource(R.string.credits_cc0), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                TextButton(onClick = { uri.openUri(credit.url) }, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text(stringResource(R.string.credits_open_link), color = Palette.Moon)
                }
            }
        }

        SectionLabel(stringResource(R.string.credits_font))
        Panel {
            Text(stringResource(R.string.credits_font_line), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.credits_font_licence), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            TextButton(onClick = { showLicence = true }, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(stringResource(R.string.credits_view_licence), color = Palette.Moon)
            }
        }

        SectionLabel(stringResource(R.string.credits_made_here))
        Panel {
            Text(stringResource(R.string.credits_made_here_body), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSoft)
        }
    }

    if (showLicence) {
        val text = remember { runCatching { context.assets.open(FONT_LICENCE_ASSET).bufferedReader().use { it.readText() } }.getOrDefault("") }
        AlertDialog(
            onDismissRequest = { showLicence = false },
            title = { Text(stringResource(R.string.credits_font_licence)) },
            text = {
                Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.verticalScroll(rememberScrollState()))
            },
            confirmButton = { TextButton(onClick = { showLicence = false }) { Text(stringResource(R.string.done)) } },
            containerColor = Palette.Card,
            titleContentColor = Palette.Text,
            textContentColor = Palette.TextSoft,
        )
    }
}
