package io.github.murrayb52.sleeptight.ui.home

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.ui.theme.Palette

/**
 * Shown at the bed tap (unless turned off): plug in the charger, put the phone on the nightstand
 * next to you, and point the mic end towards you. "Start the night" carries on with the bed tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedsideCheckSheet(onStart: (dontShowAgain: Boolean) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF10173A),
        contentColor = Palette.Text,
    ) {
        BedsideCheckContent(onStart)
    }
}

@Composable
fun BedsideCheckContent(onStart: (dontShowAgain: Boolean) -> Unit) {
    var dontShow by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.bedside_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Image(painterResource(R.drawable.ic_bedside), contentDescription = null, modifier = Modifier.size(width = 240.dp, height = 194.dp))
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CheckItem(R.string.bedside_charger_title, R.string.bedside_charger_body, Palette.Ember)
            CheckItem(R.string.bedside_nightstand_title, R.string.bedside_nightstand_body, Palette.Moon)
            CheckItem(R.string.bedside_mic_title, R.string.bedside_mic_body, Palette.Moon)
        }
        Spacer(Modifier.height(12.dp))
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
        Button(
            onClick = { onStart(dontShow) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Ember, contentColor = Palette.OnEmber),
        ) { Text(stringResource(R.string.bedside_start), style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun CheckItem(title: Int, body: Int, dot: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Sky)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.padding(top = 7.dp).size(10.dp).clip(RoundedCornerShape(5.dp)).background(dot))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        }
    }
}
