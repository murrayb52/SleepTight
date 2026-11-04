package io.github.murrayb52.sleeptight.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.ui.theme.Palette

/**
 * A scrolling wheel of [values] (which wraps round), showing three rows with the middle one
 * selected. [onSelected] fires only after the user scrolls and the wheel settles, never just for
 * being shown. [nearest] picks the starting row when [selected] isn't one of [values].
 */
@Composable
fun <T> WheelPicker(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    selectedStyle: TextStyle,
    otherStyle: TextStyle,
    description: String,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 64.dp,
    nearest: (List<T>, T) -> Int = { list, value -> list.indexOf(value).coerceAtLeast(0) },
) {
    // A long list that repeats the values, starting in the middle, so it scrolls round in both directions.
    val repeats = 200
    val startIndex = remember(values) { values.size * (repeats / 2) + nearest(values, selected) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex - 1)
    val centred by remember { derivedStateOf { state.firstVisibleItemIndex + 1 } }

    LaunchedEffect(state, values) {
        var scrolled = false
        snapshotFlow { state.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling) {
                    scrolled = true
                } else if (scrolled) {
                    scrolled = false
                    onSelected(values[centred % values.size])
                }
            }
    }

    Box(
        modifier
            .height(rowHeight * 3)
            .semantics {
                contentDescription = description
                stateDescription = label(values[centred % values.size])
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(220.dp)
                .height(rowHeight)
                .border(1.dp, Palette.Outline, RoundedCornerShape(18.dp)),
        )
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(values.size * repeats) { i ->
                Box(Modifier.height(rowHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        label(values[i % values.size]),
                        style = if (i == centred) selectedStyle else otherStyle,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
