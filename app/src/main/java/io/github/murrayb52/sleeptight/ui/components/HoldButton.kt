package io.github.murrayb52.sleeptight.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * A button that only fires after being held for [holdMillis], filling as it goes, so a bump in the
 * night can't trigger it. Screen readers get a normal click action.
 */
@Composable
fun HoldButton(text: String, onHeld: () -> Unit, modifier: Modifier = Modifier, holdMillis: Int = 1_200) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(Palette.CardDim)
            .border(1.dp, Palette.Outline, shape)
            .semantics {
                role = Role.Button
                onClick(label = text) {
                    onHeld()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val fill = scope.launch {
                        progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
                        onHeld()
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f) fill.cancel()
                    scope.launch { progress.animateTo(0f, tween(250)) }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(Palette.Outline),
        )
        Text(text, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
    }
}
