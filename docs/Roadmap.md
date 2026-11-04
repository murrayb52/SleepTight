# Roadmap

What SleepTight does today, what's being built next, and ideas for later. **This is the place to record intentions** so a fresh session (or a future you) knows them. When an idea is agreed, add it here. When it's built, move it to "Current features" and document how it works in its own page in `docs/`.

The v1 build order is in the [v1 plan](plan/2026-11-04-v1-build-plan.md), and progress is ticked off in the [README](../README.md#build-order).

## Current features (v0.4.x)

| Feature | How it works |
|---|---|
| **Redesign**: night-sky look, bottom nav (Sleep · History · Profile, hidden in bed), twinkling stars, arc score dials | [ui.md](ui.md) |
| **Editable alarm**: wake-by wheel and window length on the Sleep tab, on/off chip | [ui.md](ui.md#sleep-tab-before-bed) |
| **Bedside check** at the bed tap (charger, nightstand, mic towards you), with Don't show again | [ui.md](ui.md#bedside-check) |
| **Alarm screen**: a very slow sunrise while ringing, swipe up to snooze, Stop sweeps to day with the night so far and an I'm up button (a separate tap) | [ui.md](ui.md#alarm-screen) |
| **Score explainers**: ⓘ icons on the sleep score and each of its five parts, with numbers taken from `ScoreConfig` and your goals | [ui.md](ui.md#one-night) |
| **History** (7/30 days, averages, week strip, night list), **one night's page**, **Profile** with goals, alarm and reminder settings | [ui.md](ui.md) |
| **Press-and-hold I'm up** | [ui.md](ui.md#tonight-sleep-tab-in-bed) |
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

- **Bundled alarm sounds**: 3–5 CC0 sounds from Freesound (account `sound_the_alarm` created 2026-11-04; downloading needs an API key), listed with sources and licences in `ASSETS.md`, plus a sound picker in Profile. The generated chime stays as the fallback.
- **Sleep stages and score on the night page and dials** once step 5 provides sleep data (Garmin or the activity estimate).
- **Opt-in loud-moment playback**: the loudest 5 short clips per night, kept for 7 nights, with a hard size cap. It's the one planned exception to "never store audio". Spec in the [v1 plan's "Later" list](plan/2026-11-04-v1-build-plan.md#later-not-v1-but-design-for-it).

## Future ideas

- **Philips Hue sunrise** through `AlarmEvents.onWakeWindowOpened`
- **Richer Garmin data** (HRV, SpO2, respiration, restless moments) behind `GarminSource`, possibly via the unofficial Garmin API
- **Connect IQ watch app** for a wrist-driven smart alarm
- **Sound classification**: snoring, talking, coughing
- **Falling-asleep sounds and stories**
- **Thinner storage for old nights**: per-minute summaries instead of per-second levels after a while (about 1.3 MB per night today)
- **Smart-trigger tuning** once there are real nights to compare against Garmin stages
