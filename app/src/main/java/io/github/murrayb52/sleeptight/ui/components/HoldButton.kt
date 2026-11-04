package io.github.murrayb52.sleeptight.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * A button that only fires after being held for [holdMillis], so a bump in the night can't trigger
 * it. While held it fills with amber from left to right and shows [holdingText]; letting go early
 * drains the fill. Completing it gives a short vibration. Screen readers get a normal click.
 */
@Composable
fun HoldButton(
    text: String,
    holdingText: String,
    onHeld: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = 1_200,
) {
    val progress = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(30.dp)
    val active = pressed || progress.value > 0f

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(Palette.CardDim)
            // The fill: amber, growing from the left as you hold. Drawn behind the label rather than
            // as a child sized with fillMaxHeight, which collapses to nothing inside a scrolling column.
            .drawBehind { drawRect(Palette.Ember.copy(alpha = 0.55f), size = size.copy(width = size.width * progress.value)) }
            .border(if (active) 2.dp else 1.dp, if (active) Palette.Ember else Palette.Outline, shape)
            .semantics {
                role = Role.Button
                onClick(label = text) {
                    onHeld()
                    true
                }
            }
            .pointerInput(holdMillis) {
                detectTapGestures(onPress = {
                    pressed = true
                    val fill = scope.launch {
                        progress.animateTo(1f, tween((holdMillis * (1f - progress.value)).toInt(), easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHeld()
                    }
                    tryAwaitRelease()
                    pressed = false
                    if (progress.value < 1f) fill.cancel()
                    scope.launch { progress.animateTo(0f, tween(250)) }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (active) holdingText else text, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
    }
}
