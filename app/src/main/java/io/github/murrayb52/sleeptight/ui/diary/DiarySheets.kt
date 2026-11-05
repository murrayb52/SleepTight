package io.github.murrayb52.sleeptight.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.data.CaffeineEntry
import io.github.murrayb52.sleeptight.data.CoffeeVenue
import io.github.murrayb52.sleeptight.data.DrinkType
import io.github.murrayb52.sleeptight.data.LastScreens
import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.data.WakeReason
import io.github.murrayb52.sleeptight.diary.DrinkChoice
import io.github.murrayb52.sleeptight.diary.SOFT_DRINK_SIZES
import io.github.murrayb52.sleeptight.diary.caffeineEntry
import io.github.murrayb52.sleeptight.ui.home.rememberTimeFormatter
import io.github.murrayb52.sleeptight.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

// ---------------------------------------------------------------- shared pieces

private val SheetColour = Color(0xFF10173A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiarySheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColour,
        contentColor = Palette.Text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            content = content,
        )
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        modifier = modifier.heightIn(min = 40.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Palette.Card,
            labelColor = Palette.TextSoft,
            selectedContainerColor = Palette.Ember.copy(alpha = 0.28f),
            selectedLabelColor = Palette.Text,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Palette.Outline,
            selectedBorderColor = Palette.Ember,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipChoice(options: List<T>, selected: (T) -> Boolean, label: @Composable (T) -> String, onClick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> Chip(label(option), selected(option), { onClick(option) }) }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.TextSoft, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Ember, contentColor = Palette.OnEmber),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun NoteField(value: String, onChange: (String) -> Unit, hint: String, singleLine: Boolean, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(500)) },
        placeholder = { Text(hint, color = Palette.Muted) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Ember,
            unfocusedBorderColor = Palette.Outline,
            focusedTextColor = Palette.Text,
            unfocusedTextColor = Palette.Text,
            cursorColor = Palette.Ember,
        ),
    )
}

// ---------------------------------------------------------------- at the bed tap

/** What the "Tonight" part of the bed-tap sheet collects. */
data class TonightAnswers(
    val earplugs: Boolean,
    val alcohol: Boolean = false,
    val partner: Boolean = false,
    val differentBed: Boolean = false,
    val readingBeforeBed: Boolean = false,
    val lastScreens: LastScreens? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TonightQuestions(answers: TonightAnswers, onChange: (TonightAnswers) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = answers.earplugs, role = Role.Switch) { onChange(answers.copy(earplugs = it)) }
                .testTag("earplugs"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.earplugs_in), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = answers.earplugs,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = Palette.Ember, checkedThumbColor = Palette.OnEmber),
            )
        }
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.tag_alcohol), answers.alcohol, { onChange(answers.copy(alcohol = !answers.alcohol)) })
            Chip(stringResource(R.string.tag_partner), answers.partner, { onChange(answers.copy(partner = !answers.partner)) })
            Chip(stringResource(R.string.tag_different_bed), answers.differentBed, { onChange(answers.copy(differentBed = !answers.differentBed)) })
            Chip(stringResource(R.string.tag_reading), answers.readingBeforeBed, { onChange(answers.copy(readingBeforeBed = !answers.readingBeforeBed)) })
        }
        Label(stringResource(R.string.last_screens_title))
        ChipChoice(
            LastScreens.entries,
            selected = { it == answers.lastScreens },
            label = { stringResource(it.label) },
            onClick = { onChange(answers.copy(lastScreens = if (answers.lastScreens == it) null else it)) },
        )
    }
}

val LastScreens.label: Int
    get() = when (this) {
        LastScreens.JUST_NOW -> R.string.screens_just_now
        LastScreens.UNDER_1H -> R.string.screens_under_1h
        LastScreens.H1_TO_2 -> R.string.screens_1_2h
        LastScreens.OVER_2H -> R.string.screens_over_2h
    }

// ---------------------------------------------------------------- in the night

val WakeReason.label: Int
    get() = when (this) {
        WakeReason.TOILET -> R.string.reason_toilet
        WakeReason.WATER -> R.string.reason_water
        WakeReason.NOISE -> R.string.reason_noise
        WakeReason.TOO_HOT -> R.string.reason_too_hot
        WakeReason.TOO_COLD -> R.string.reason_too_cold
        WakeReason.MIND_RACING -> R.string.reason_mind_racing
        WakeReason.EARPLUGS -> R.string.reason_earplugs
        WakeReason.CHECKING_TIME -> R.string.reason_checking_time
        WakeReason.OTHER -> R.string.reason_other
    }

/** The wake note: why you woke, picked with a tap or two, and an optional note. */
@Composable
fun WakeNoteContent(at: String, onSave: (Set<WakeReason>, String?) -> Unit, onNotNow: () -> Unit) {
    var picked by rememberSaveable { mutableStateOf(emptySet<WakeReason>()) }
    var note by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.wake_note_title, at), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    ChipChoice(
        WakeReason.entries,
        selected = { it in picked },
        label = { stringResource(it.label) },
        onClick = { picked = if (it in picked) picked - it else picked + it },
    )
    Spacer(Modifier.height(12.dp))
    NoteField(note, { note = it }, stringResource(R.string.wake_note_hint), singleLine = true, tag = "wakeNoteText")
    Spacer(Modifier.height(16.dp))
    PrimaryButton(
        stringResource(R.string.save),
        onClick = { onSave(picked, note.trim().ifEmpty { null }) },
        enabled = picked.isNotEmpty() || note.isNotBlank(),
        modifier = Modifier.testTag("wakeNoteSave"),
    )
    TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.not_now), color = Palette.Muted)
    }
}

// ---------------------------------------------------------------- in the morning

/** The survey's answers, before they're stored. */
data class SurveyAnswers(
    val rested: Int,
    val quality: Int,
    val wakings: Int,
    val minutesToFallAsleep: Int,
    val minutesBeforeTrying: Int,
    val earplugAdjustments: Int?,
    val notes: String?,
)

private const val MAX_MINUTES = 90

@Composable
fun MorningSurveyContent(earplugs: Boolean, onSave: (SurveyAnswers) -> Unit, onLater: () -> Unit) {
    var rested by rememberSaveable { mutableIntStateOf(5) }
    var quality by rememberSaveable { mutableIntStateOf(5) }
    var wakings by rememberSaveable { mutableIntStateOf(0) }
    var toSleep by rememberSaveable { mutableIntStateOf(15) }
    var beforeTrying by rememberSaveable { mutableIntStateOf(0) }
    var adjustments by rememberSaveable { mutableIntStateOf(0) }
    var notes by rememberSaveable { mutableStateOf("") }

    Text(stringResource(R.string.survey_title), style = MaterialTheme.typography.headlineSmall)
    ScaleSlider(stringResource(R.string.survey_rested), rested, 1, 10, { stringResource(R.string.out_of_ten, it) }, "surveyRested") { rested = it }
    ScaleSlider(stringResource(R.string.survey_quality), quality, 1, 10, { stringResource(R.string.out_of_ten, it) }, "surveyQuality") { quality = it }
    Stepper(stringResource(R.string.survey_wakings), wakings, "surveyWakings") { wakings = it }
    ScaleSlider(stringResource(R.string.survey_to_sleep), toSleep, 0, MAX_MINUTES, { minutesLabel(it) }, "surveyToSleep", step = 5) { toSleep = it }
    ScaleSlider(stringResource(R.string.survey_before_trying), beforeTrying, 0, MAX_MINUTES, { minutesLabel(it) }, "surveyBeforeTrying", step = 5) { beforeTrying = it }
    if (earplugs) Stepper(stringResource(R.string.survey_earplug_adjustments), adjustments, "surveyEarplugs") { adjustments = it }
    Spacer(Modifier.height(12.dp))
    NoteField(notes, { notes = it }, stringResource(R.string.survey_notes), singleLine = false, tag = "surveyNotes")
    Spacer(Modifier.height(16.dp))
    PrimaryButton(
        stringResource(R.string.save),
        onClick = {
            onSave(SurveyAnswers(rested, quality, wakings, toSleep, beforeTrying, adjustments.takeIf { earplugs }, notes.trim().ifEmpty { null }))
        },
        modifier = Modifier.testTag("surveySave"),
    )
    TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.survey_later), color = Palette.Muted)
    }
}

@Composable
private fun minutesLabel(m: Int) = if (m >= MAX_MINUTES) stringResource(R.string.minutes_value_max, m) else stringResource(R.string.minutes_value, m)

@Composable
private fun ScaleSlider(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    valueLabel: @Composable (Int) -> String,
    tag: String,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    var position by rememberSaveable(value) { mutableFloatStateOf(value.toFloat()) }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.TextSoft, modifier = Modifier.weight(1f))
        Text(valueLabel(position.roundToInt()), style = MaterialTheme.typography.titleSmall, color = Palette.Ember)
    }
    Slider(
        value = position,
        onValueChange = { position = (Math.round(it / step) * step).toFloat() },
        onValueChangeFinished = { onChange(position.roundToInt()) },
        valueRange = min.toFloat()..max.toFloat(),
        steps = (max - min) / step - 1,
        modifier = Modifier.testTag(tag),
        colors = SliderDefaults.colors(thumbColor = Palette.Ember, activeTrackColor = Palette.Ember, inactiveTrackColor = Palette.Outline),
    )
}

@Composable
private fun Stepper(title: String, value: Int, tag: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.TextSoft, modifier = Modifier.weight(1f))
        val less = stringResource(R.string.decrease)
        val more = stringResource(R.string.increase)
        OutlinedButton(
            onClick = { onChange((value - 1).coerceAtLeast(0)) },
            modifier = Modifier.size(48.dp).semantics { contentDescription = "$less $title" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("−", style = MaterialTheme.typography.titleLarge, color = Palette.Text) }
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(48.dp).testTag(tag), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedButton(
            onClick = { onChange((value + 1).coerceAtMost(20)) },
            modifier = Modifier.size(48.dp).semantics { contentDescription = "$more $title" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("+", style = MaterialTheme.typography.titleLarge, color = Palette.Text) }
    }
}

// ---------------------------------------------------------------- caffeine

private val HOURS_AGO = listOf(0, 1, 2, 3, 4, 6, 8)

/** Log a drink, and see (or delete) the last day's. */
@Composable
fun CaffeineContent(
    recent: List<CaffeineEntry>,
    settings: Settings,
    zone: ZoneId,
    now: () -> Instant,
    onAdd: (CaffeineEntry) -> Unit,
    onDelete: (CaffeineEntry) -> Unit,
) {
    var drink by rememberSaveable { mutableStateOf(DrinkChoice.COFFEE) }
    var venue by rememberSaveable { mutableStateOf(CoffeeVenue.HOME) }
    var shots by rememberSaveable { mutableIntStateOf(1) }
    var volume by rememberSaveable { mutableIntStateOf(SOFT_DRINK_SIZES.first()) }
    var hoursAgo by rememberSaveable { mutableIntStateOf(0) }
    val time = rememberTimeFormatter()

    Text(stringResource(R.string.caffeine_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    ChipChoice(DrinkChoice.entries, { it == drink }, { stringResource(it.label) }, { drink = it })
    Spacer(Modifier.height(8.dp))
    if (drink == DrinkChoice.COFFEE || drink == DrinkChoice.DECAF) {
        ChipChoice(CoffeeVenue.entries, { it == venue }, { stringResource(if (it == CoffeeVenue.HOME) R.string.venue_home else R.string.venue_restaurant) }, { venue = it })
        Spacer(Modifier.height(8.dp))
        ChipChoice(listOf(1, 2), { it == shots }, { stringResource(if (it == 1) R.string.shots_single else R.string.shots_double) }, { shots = it })
    } else {
        ChipChoice(SOFT_DRINK_SIZES, { it == volume }, { if (it == 1000) stringResource(R.string.size_litre) else stringResource(R.string.size_ml, it) }, { volume = it })
    }
    Spacer(Modifier.height(8.dp))
    ChipChoice(HOURS_AGO, { it == hoursAgo }, { if (it == 0) stringResource(R.string.when_now) else stringResource(R.string.hours_ago, it) }, { hoursAgo = it })
    Spacer(Modifier.height(16.dp))
    val preview = caffeineEntry(drink, now(), settings, venue, shots, volume)
    PrimaryButton(
        stringResource(R.string.caffeine_add_mg, preview.mg.roundToInt()),
        onClick = { onAdd(caffeineEntry(drink, now().minusSeconds(hoursAgo * 3600L), settings, venue, shots, volume)) },
        modifier = Modifier.testTag("caffeineAdd"),
    )
    if (recent.isNotEmpty()) {
        Label(stringResource(R.string.caffeine_last_day))
        recent.sortedByDescending { it.timestamp }.forEach { entry ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${time.format(Instant.ofEpochMilli(entry.timestamp).atZone(zone))}  ${drinkName(entry)}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.caffeine_entry_mg, entry.mg.roundToInt()), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                val delete = stringResource(R.string.delete)
                IconButton(onClick = { onDelete(entry) }, modifier = Modifier.semantics { contentDescription = delete }) {
                    Text("×", style = MaterialTheme.typography.titleLarge, color = Palette.Muted)
                }
            }
        }
    }
}

val DrinkChoice.label: Int
    get() = when (this) {
        DrinkChoice.COFFEE -> R.string.drink_coffee
        DrinkChoice.DECAF -> R.string.drink_decaf
        DrinkChoice.COKE -> R.string.drink_coke
        DrinkChoice.COKE_ZERO -> R.string.drink_coke_zero
    }

/** "Double Coffee", "500 ml Coke Zero" and so on. */
@Composable
fun drinkName(entry: CaffeineEntry): String = when (entry.drinkType) {
    DrinkType.COFFEE -> {
        val shots = stringResource(if ((entry.shots ?: 1) >= 2) R.string.shots_double else R.string.shots_single)
        val kind = stringResource(if (entry.decaf == true) R.string.drink_decaf else R.string.drink_coffee)
        "$shots $kind"
    }
    DrinkType.COKE, DrinkType.COKE_ZERO -> {
        val size = entry.volumeMl?.let { if (it == 1000) stringResource(R.string.size_litre) else stringResource(R.string.size_ml, it) } ?: ""
        val kind = stringResource(if (entry.drinkType == DrinkType.COKE) R.string.drink_coke else R.string.drink_coke_zero)
        "$size $kind".trim()
    }
}
