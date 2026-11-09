package io.github.murrayb52.sleeptight.alarm

import io.github.murrayb52.sleeptight.data.Settings
import io.github.murrayb52.sleeptight.metrics.AlarmConfig
import io.github.murrayb52.sleeptight.metrics.SnoozeCurve

/** The stored curve, or linear if the stored name isn't one we know (it never should be). */
fun Settings.snoozeCurveOrDefault(): SnoozeCurve =
    SnoozeCurve.entries.firstOrNull { it.name == snoozeCurve } ?: SnoozeCurve.LINEAR

/** The alarm thresholds with this night's snooze settings applied. */
fun Settings.alarmConfig(): AlarmConfig = AlarmConfig(firstSnoozeMinutes = firstSnoozeMinutes)
