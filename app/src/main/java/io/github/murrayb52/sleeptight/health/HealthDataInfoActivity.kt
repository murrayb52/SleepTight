package io.github.murrayb52.sleeptight.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.ui.components.Panel
import io.github.murrayb52.sleeptight.ui.theme.Palette
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme

/**
 * Health Connect opens this when you ask why SleepTight wants your health data: what it reads, what
 * it does with it, and that nothing leaves the phone.
 */
class HealthDataInfoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { SleepTightTheme { HealthDataInfo(onClose = { finish() }) } }
    }
}

@Composable
fun HealthDataInfo(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.Sky).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.hc_info_title), style = MaterialTheme.typography.headlineMedium)
        Panel {
            Text(stringResource(R.string.hc_info_reads_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.hc_info_reads_body), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSoft)
        }
        Panel {
            Text(stringResource(R.string.hc_info_use_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.hc_info_use_body), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSoft)
        }
        Panel {
            Text(stringResource(R.string.hc_info_private_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.hc_info_private_body), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSoft)
        }
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Ember, contentColor = Palette.OnEmber),
        ) { Text(stringResource(R.string.done)) }
    }
}
