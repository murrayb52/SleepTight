package io.github.murrayb52.sleeptight.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.ui.theme.Palette

/** A small ⓘ that opens an explanation. The touch target is 48 dp even though the icon is small. */
@Composable
fun InfoButton(title: String, body: String, modifier: Modifier = Modifier, tint: Color = Palette.Muted) {
    var open by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.info_about, title)
    IconButton(onClick = { open = true }, modifier = modifier.size(48.dp).semantics { contentDescription = description }) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(18.dp)) {
                val w = 1.6.dp.toPx()
                val r = size.minDimension / 2
                drawCircle(tint, radius = r - w / 2, style = Stroke(w))
                drawLine(tint, Offset(r, r * 0.85f), Offset(r, r * 1.45f), w, StrokeCap.Round)
                drawCircle(tint, radius = w * 0.75f, center = Offset(r, r * 0.52f))
            }
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(body, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.got_it)) } },
            containerColor = Palette.Card,
            titleContentColor = Palette.Text,
            textContentColor = Palette.TextSoft,
        )
    }
}
