package io.github.murrayb52.sleeptight.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

/**
 * Material's standard display sizes, for components such as TimePicker that size their boxes for
 * them. SleepTight's own display type is bigger (96 sp clock numerals), which overflows those boxes
 * and clips the digits, especially at large font scales.
 */
@Composable
fun StandardPickerType(content: @Composable () -> Unit) {
    val t = MaterialTheme.typography
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = t.copy(
            displayLarge = t.displayLarge.copy(fontSize = 57.sp, lineHeight = 64.sp),
            displayMedium = t.displayMedium.copy(fontSize = 45.sp, lineHeight = 52.sp),
            displaySmall = t.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp),
        ),
        content = content,
    )
}
