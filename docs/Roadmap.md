# Roadmap

What SleepTight does today, what's being built next, and ideas for later. **This is the place to record intentions** so a fresh session (or a future you) knows them. When an idea is agreed, add it here. When it's built, move it to "Current features" and document how it works in its own page in `docs/`.

The v1 build order is in the [v1 plan](plan/2026-11-04-v1-build-plan.md), and progress is ticked off in the [README](../README.md#build-order).

## Current features (v0.4.x)

| Feature | How it works |
|---|---|
| **Bed and wake taps** define each night. A double tap can't open two nights, and a night left open 16 h prompts for the real wake time | [nights.md](nights.md) |
| **Overnight sound monitoring**: relative sound levels once a second, noise events, activity per minute. Levels only, never audio | [sound-monitoring.md](sound-monitoring.md) |
| **Smart alarm** in a wake window (default 05:45–06:15). It rings early when you stir, otherwise at the end of the window. A generated chime rises from 10 % to 100 % over 60 s, with no vibration, over the lock screen. On/off switch on Home | [alarm.md](alarm.md) |
| **Shrinking snoozes**: 9 → 7 → 5 → 3 → 2 minutes, at most 5, then only Stop. Every snooze is counted | [alarm.md](alarm.md#snooze-and-dismiss) |
| **Bedtime reminder** 30 minutes before the bedtime goal (default 21:30 for 22:00). Skipped once you're in bed | [alarm.md](alarm.md#bedtime-reminder) |
| **Sleep score maths** (0–100 with breakdown), night metrics and regularity, unit tested. Not shown in the app until there's sleep data (step 5) and a report (step 6) | [metrics.md](metrics.md) |
| Denial paths for microphone, notifications, battery restrictions (with Samsung advice), exact alarms, full-screen alarms and alarm volume | each page above |

## Next: v1 build order (remaining)

5. Garmin import via Health Connect, trimmed to the taps, plus a sleep estimate from activity when there's no Garmin data
6. Morning report (one night: hypnogram, score breakdown, metrics with reference ranges, noise timeline, caffeine at bedtime)
7. Caffeine log and caffeine at bedtime
8. Trends and sleep debt
9. JSON export
10. Overnight test on the phone, then fixes

## Agreed, not built yet

- **Redesign with a bottom nav bar (Sleep · History · Profile).** Requested 2026-11-04, inspired by the mood of the user's previous sleep app but with SleepTight's own look: a deep night sky with stars, a moon, big thin clock numerals, and warm amber for the main action. Mockups are in the [redesign canvas](https://claude.ai/artifact/6XdQ4FWBxVReKzJEH77tet), awaiting approval. Screens: Sleep (wake-by wheel, window chip, alarm on/off, Going to bed, last night), Tonight (dimmed, no nav, press-and-hold I'm up), Alarm, History (7/30 days, week strip, list of nights), One night (the step 6 morning report), Profile (stats, goals, alarm, reminders, data).
- **Editable alarm time**: a wake-by wheel picker and window length on the Sleep screen. Comes with the redesign.
- **Bedside check before each night**: a sheet at the bed tap reminding you to plug in the charger, put the phone on the nightstand next to you, and point the mic (bottom edge) towards you. Includes a "Don't show again" option. Comes with the redesign.
- **Settings in Profile**: goals (bedtime, sleep, snoozes), alarm window, sound, base snooze length, reminder and bedside-check toggles, Health Connect, export.
- **Bundled alarm sounds**: 3–5 CC0 sounds from Freesound (the user is getting an account), listed with sources and licences in `ASSETS.md`, plus a sound picker. The generated chime stays as the fallback.
- **Opt-in loud-moment playback**: the loudest 5 short clips per night, kept for 7 nights, with a hard size cap. It's the one planned exception to "never store audio". Spec in the [v1 plan's "Later" list](plan/2026-11-04-v1-build-plan.md#later-not-v1-but-design-for-it).

## Future ideas

- **Philips Hue sunrise** through `AlarmEvents.onWakeWindowOpened`
- **Richer Garmin data** (HRV, SpO2, respiration, restless moments) behind `GarminSource`, possibly via the unofficial Garmin API
- **Connect IQ watch app** for a wrist-driven smart alarm
- **Sound classification**: snoring, talking, coughing
- **Falling-asleep sounds and stories**
- **Press-and-hold "I'm up"**, so a bump in the night can't end it (shown in the mockups)
- **Thinner storage for old nights**: per-minute summaries instead of per-second levels after a while (about 1.3 MB per night today)
- **Smart-trigger tuning** once there are real nights to compare against Garmin stages
