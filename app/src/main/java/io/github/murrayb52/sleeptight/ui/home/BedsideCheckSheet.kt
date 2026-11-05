package io.github.murrayb52.sleeptight.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.data.LastScreens
import io.github.murrayb52.sleeptight.ui.diary.TonightAnswers
import io.github.murrayb52.sleeptight.ui.diary.TonightQuestions
import androidx.compose.runtime.saveable.Saver
import io.github.murrayb52.sleeptight.ui.theme.Palette

/**
 * Shown at every bed tap: the bedside checks (unless turned off: plug in the charger, put the phone
 * on the nightstand next to you, point the mic end towards you), then tonight's diary questions
 * (earplugs and context). "Start the night" carries on with the bed tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedsideCheckSheet(
    showChecks: Boolean,
    earplugsDefault: Boolean,
    onStart: (dontShowAgain: Boolean, answers: TonightAnswers) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF10173A),
        contentColor = Palette.Text,
    ) {
        BedsideCheckContent(showChecks, earplugsDefault, onStart)
    }
}

@Composable
fun BedsideCheckContent(
    showChecks: Boolean,
    earplugsDefault: Boolean,
    onStart: (dontShowAgain: Boolean, answers: TonightAnswers) -> Unit,
) {
    var dontShow by rememberSaveable { mutableStateOf(false) }
    var answers by rememberSaveable(stateSaver = TonightAnswersSaver) { mutableStateOf(TonightAnswers(earplugs = earplugsDefault)) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showChecks) {
            Text(stringResource(R.string.bedside_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CheckItem(R.string.bedside_charger_title, Palette.Ember)
                CheckItem(R.string.bedside_nightstand_title, Palette.Moon)
                CheckItem(R.string.bedside_mic_title, Palette.Moon)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = dontShow, role = Role.Checkbox, onValueChange = { dontShow = it }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Checkbox(checked = dontShow, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Palette.Ember, checkmarkColor = Palette.OnEmber))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.bedside_dont_show), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSoft)
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(stringResource(R.string.tonight_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        TonightQuestions(answers) { answers = it }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onStart(dontShow, answers) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Ember, contentColor = Palette.OnEmber),
        ) { Text(stringResource(R.string.bedside_start), style = MaterialTheme.typography.titleLarge) }
    }
}

/**
 * One check as a single line, so the checks and tonight's questions fit on one screen (the longer
 * explanations were dropped on 2026-11-05 to make room; they're in docs/ui.md).
 */
@Composable
private fun CheckItem(title: Int, dot: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Sky)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(dot))
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
    }
}

/** Keeps the answers across rotation: a list of the flags plus the screens choice. */
private val TonightAnswersSaver = Saver<TonightAnswers, List<Any?>>(
    save = { listOf(it.earplugs, it.alcohol, it.partner, it.differentBed, it.readingBeforeBed, it.lastScreens?.name) },
    restore = {
        TonightAnswers(
            earplugs = it[0] as Boolean,
            alcohol = it[1] as Boolean,
            partner = it[2] as Boolean,
            differentBed = it[3] as Boolean,
            readingBeforeBed = it[4] as Boolean,
            lastScreens = (it[5] as String?)?.let(LastScreens::valueOf),
        )
    },
)
