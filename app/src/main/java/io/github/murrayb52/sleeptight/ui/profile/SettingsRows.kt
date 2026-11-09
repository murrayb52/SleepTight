package io.github.murrayb52.sleeptight.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.ui.components.SectionLabel
import io.github.murrayb52.sleeptight.ui.theme.Palette

/** A settings page: a back button and title, then scrolling [content]. */
@Composable
internal fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
            Text(title, style = MaterialTheme.typography.headlineLarge)
        }
        content()
    }
}

/** A titled group of rows on one card. */
@Composable
internal fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) SectionLabel(title)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Card), content = content)
    }
}

@Composable
private fun RowDivider(last: Boolean) {
    if (!last) HorizontalDivider(color = Palette.Line, modifier = Modifier.padding(start = 16.dp))
}

/** A label with its current value; tapping opens a picker. */
@Composable
internal fun ValueRow(label: String, value: String, enabled: Boolean = true, last: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = if (enabled) Palette.TextSoft else Palette.Muted)
            if (enabled) Text("›", style = MaterialTheme.typography.titleLarge, color = Palette.Muted)
        }
    }
    RowDivider(last)
}

/** A row that opens another settings page, with a one-line summary of what's in it. */
@Composable
internal fun NavRow(label: String, summary: String, tag: String, last: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = Palette.Muted, modifier = Modifier.padding(start = 8.dp))
    }
    RowDivider(last)
}

/**
 * A setting with a notched slider (one notch per whole unit). The value shown follows the thumb;
 * [onDone] saves it when you let go, so moving it doesn't save (and rescore) on every notch.
 */
@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: @Composable (Float) -> String,
    onDone: (Float) -> Unit,
    tag: String,
    last: Boolean = false,
    onChange: (Float) -> Unit = {},
) {
    var current by remember(value) { mutableFloatStateOf(value.coerceIn(range)) }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(valueText(current), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSoft)
        }
        Slider(
            value = current,
            onValueChange = {
                current = it
                onChange(it)
            },
            onValueChangeFinished = { onDone(current) },
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = Palette.Ember,
                activeTrackColor = Palette.Ember,
                inactiveTrackColor = Palette.Outline,
                activeTickColor = Palette.OnEmber,
                inactiveTickColor = Palette.Muted,
            ),
            modifier = Modifier.testTag(tag),
        )
    }
    RowDivider(last)
}

/** An on/off setting, with an optional line saying what it does. */
@Composable
internal fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit, detail: String? = null, last: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Switch(
            checked = on,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Ember, checkedThumbColor = Palette.OnEmber),
        )
    }
    RowDivider(last)
}

/** Pick one of [options]; the current one is ticked. */
@Composable
internal fun <T> ChoiceDialog(title: String, options: List<T>, current: T, label: @Composable (T) -> String, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    TextButton(onClick = { onPick(o) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label(o) + if (o == current) "  ✓" else "", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = Palette.Card,
    )
}
