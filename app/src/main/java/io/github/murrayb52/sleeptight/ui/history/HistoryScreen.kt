package io.github.murrayb52.sleeptight.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.ui.components.CentredText
import io.github.murrayb52.sleeptight.ui.components.ScoreDial
import io.github.murrayb52.sleeptight.ui.home.formatDuration
import io.github.murrayb52.sleeptight.ui.home.rememberTimeFormatter
import io.github.murrayb52.sleeptight.ui.theme.Palette
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryRoute(onOpenNight: (Long) -> Unit, viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(state, onRange = viewModel::onRange, onOpenNight = onOpenNight)
}

@Composable
fun HistoryScreen(state: HistoryUi, onRange: (HistoryRange) -> Unit, onOpenNight: (Long) -> Unit, modifier: Modifier = Modifier) {
    val time = rememberTimeFormatter()
    LazyColumn(
        modifier.fillMaxSize().background(Palette.Sky).statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tab_history), style = MaterialTheme.typography.headlineLarge)
                RangeToggle(state.range, onRange)
            }
        }
        if (state.loading) return@LazyColumn
        val avg = state.averages
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(avg?.score?.toString() ?: "–", stringResource(R.string.avg_score), Modifier.weight(1f))
                Tile(avg?.let { formatDuration(it.timeInBed) } ?: "–", stringResource(R.string.avg_in_bed), Modifier.weight(1f))
                Tile(avg?.let { time.format(it.bedtime) } ?: "–", stringResource(R.string.avg_bedtime), Modifier.weight(1f))
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Card).padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                state.week.forEach { day ->
                    Column(
                        Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .then(if (day.nightId != null) Modifier.clickable { onOpenNight(day.nightId) } else Modifier)
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                        ScoreDial(
                            day.score,
                            size = 34.dp,
                            textStyle = MaterialTheme.typography.labelMedium.copy(color = if (day.nightId == null) Palette.Faint else Palette.Text),
                        )
                    }
                }
            }
        }
        if (state.nights.isEmpty()) {
            item {
                CentredText(stringResource(R.string.history_empty), MaterialTheme.typography.bodyMedium, Modifier.fillMaxWidth().padding(top = 24.dp), Palette.Muted)
            }
        }
        items(state.nights, key = { it.id }) { n ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Card).clickable { onOpenNight(n.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ScoreDial(n.score, size = 48.dp, textStyle = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(DateTimeFormatter.ofPattern("EEE d MMM").format(n.date), style = MaterialTheme.typography.titleMedium)
                        Text(formatDuration(n.timeInBed), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSoft)
                    }
                    Text(
                        stringResource(
                            R.string.night_row_detail,
                            time.format(n.bedTapAt.atZone(n.zone)),
                            time.format(n.wakeTapAt.atZone(n.zone)),
                            pluralStringResource(R.plurals.noise_count, n.noiseEvents, n.noiseEvents),
                            pluralStringResource(R.plurals.snooze_count, n.snoozes, n.snoozes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeToggle(range: HistoryRange, onRange: (HistoryRange) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Palette.Card).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        HistoryRange.entries.forEach { r ->
            val on = r == range
            Text(
                stringResource(if (r == HistoryRange.WEEK) R.string.range_7 else R.string.range_30),
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) Palette.Text else Palette.Muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Palette.Outline else Palette.Card)
                    .semantics { role = Role.Tab; selected = on }
                    .clickable { onRange(r) }
                    .heightIn(min = 40.dp)
                    .widthIn(min = 64.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun Tile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Card).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }
}
