package io.github.murrayb52.sleeptight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.ui.theme.Palette

/** The rounded panel used throughout. [warning] tints it for problems that need attention. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    colour: Color = Palette.Card,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (warning) MaterialTheme.colorScheme.errorContainer else colour)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/** Text that stays centred when it wraps onto several lines on narrow screens. */
@Composable
fun CentredText(text: String, style: TextStyle, modifier: Modifier = Modifier, colour: Color = Color.Unspecified) {
    Text(text = text, style = style, textAlign = TextAlign.Center, color = colour, modifier = modifier)
}

/** Small uppercase section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Palette.Muted,
        modifier = modifier.padding(start = 4.dp),
    )
}
