package io.github.murrayb52.sleeptight.ui.history

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.murrayb52.sleeptight.R
import io.github.murrayb52.sleeptight.ui.components.Panel
import io.github.murrayb52.sleeptight.ui.diary.drinkName
import io.github.murrayb52.sleeptight.ui.diary.label
import io.github.murrayb52.sleeptight.ui.home.rememberTimeFormatter
import io.github.murrayb52.sleeptight.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** The night's diary on its page: what was noted at bedtime, in the night and in the morning. */
@Composable
fun DiaryPanel(diary: DiaryUi, zone: ZoneId, onAnswerSurvey: () -> Unit) {
    val time = rememberTimeFormatter()
    Panel(Modifier.testTag("diary")) {
        Text(stringResource(R.string.diary_title), style = MaterialTheme.typography.titleSmall)

        val c = diary.context
        if (c == null) {
            Line(stringResource(R.string.diary_no_context), muted = true)
        } else {
            val tags = buildList {
                add(stringResource(if (c.earplugs) R.string.diary_earplugs_yes else R.string.diary_earplugs_no))
                if (c.alcohol) add(stringResource(R.string.tag_alcohol))
                if (c.partner) add(stringResource(R.string.tag_partner))
                if (c.differentBed) add(stringResource(R.string.tag_different_bed))
                if (c.readingBeforeBed) add(stringResource(R.string.tag_reading))
                c.lastScreens?.let { add(stringResource(R.string.diary_screens, stringResource(it.label))) }
            }
            Line(tags.joinToString(" · "))
        }

        if (diary.caffeineAtBedMg >= 0.5 || diary.drinks.isNotEmpty()) {
            Line(stringResource(R.string.diary_caffeine, diary.caffeineAtBedMg.roundToInt()))
            diary.drinks.forEach {
                Line("${time.format(Instant.ofEpochMilli(it.timestamp).atZone(zone))}  ${drinkName(it)} · ${it.mg.roundToInt()} mg", muted = true)
            }
        }

        if (diary.wakeNotes.isEmpty()) {
            Line(stringResource(R.string.diary_wakings_none), muted = true)
        } else {
            diary.wakeNotes.forEach { n ->
                val reasons = n.reasonList.map { stringResource(it.label) } + listOfNotNull(n.note)
                Line("${time.format(Instant.ofEpochMilli(n.at).atZone(zone))}  ${reasons.joinToString(" · ")}")
            }
        }

        val s = diary.survey
        if (s == null) {
            OutlinedButton(
                onClick = onAnswerSurvey,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("answerSurvey"),
                shape = RoundedCornerShape(24.dp),
            ) { Text(stringResource(R.string.survey_title), color = Palette.Text) }
        } else {
            Line(stringResource(R.string.diary_survey_line, s.rested, s.quality, s.wakings, s.minutesToFallAsleep, s.minutesBeforeTrying))
            s.earplugAdjustments?.let { Line(stringResource(R.string.diary_earplug_adjustments, it)) }
            s.notes?.let { Line("“$it”", muted = true) }
        }
    }
}

@Composable
private fun Line(text: String, muted: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = if (muted) Palette.Muted else Palette.TextSoft)
}
