# Roadmap

What SleepTight does today, what's being built next, and ideas for later. **This is the place to record intentions** so a fresh session (or a future you) knows them. When an idea is agreed, add it here. When it's built, move it to "Current features" and document how it works in its own page in `docs/`.

The v1 build order is in the [v1 plan](plan/2026-11-04-v1-build-plan.md), and progress is ticked off in the [README](../README.md#build-order).

## Current features (v0.4.x)

| Feature | How it works |
|---|---|
| **Redesign**: night-sky look, bottom nav (Sleep · History · Profile, hidden in bed), twinkling stars, arc score dials | [ui.md](ui.md) |
| **Editable alarm**: wake-by wheel and window length on the Sleep tab, on/off chip | [ui.md](ui.md#sleep-tab-before-bed) |
| **Bedside check** at the bed tap (charger, nightstand, mic towards you), with Don't show again | [ui.md](ui.md#bedside-check) |
| **Alarm screen**: a very slow sunrise while ringing, swipe up to snooze, press-and-hold I'm up silences the alarm and ends the night, then sweeps to day with last night's summary | [ui.md](ui.md#alarm-screen) |
| **Alarm sounds**: four CC0 Freesound recordings (birdsong by default, piano, two kalimbas), chosen and previewed in Profile. The chime and music box were removed as too startling | [alarm.md](alarm.md#sounds) |
| **Credits in the app**: Profile → About lists every bundled sound and the typeface, with links and the font's licence text | [ui.md](ui.md#profile), [ASSETS.md](../ASSETS.md) |
| **A sleep score for every night**: from Garmin stages when there are some, otherwise estimated from overnight sound; breakdown with the measurements behind each part and a sleep-stage chart | [metrics.md](metrics.md#scoring-in-the-app), [ui.md](ui.md#one-night) |
| **Garmin via Health Connect**: sleep stages and heart rate from Garmin Connect only, trimmed to the taps; connect in Profile; retried after syncs; nights without Garmin are estimated from sound | [garmin.md](garmin.md) |
| **Morning report**: each night's page shows the score breakdown, stages, measurements against healthy ranges, how you did against your goals, heart rate, noise, and lets you delete a night | [ui.md](ui.md#one-night) |
| **Score explainers**: ⓘ icons on the sleep score and each of its five parts, with numbers taken from `ScoreConfig` and your goals | [ui.md](ui.md#one-night) |
| **History** (7/30 days, averages, week strip, night list), **one night's page**, **Profile** with goals, alarm and reminder settings | [ui.md](ui.md) |
| **Press-and-hold I'm up** | [ui.md](ui.md#tonight-sleep-tab-in-bed) |
| **Bed and wake taps** define each night. A double tap can't open two nights, and a night left open 16 h prompts for the real wake time | [nights.md](nights.md) |
| **Overnight sound monitoring**: relative sound levels once a second, noise events, activity per minute. Levels only, never audio | [sound-monitoring.md](sound-monitoring.md) |
| **Smart alarm** in a wake window (default 05:45–06:15). It rings early when you stir, otherwise at the end of the window. The sound eases in from 2 % to 100 % over 3 minutes, with no vibration, over the lock screen. On/off switch on Home | [alarm.md](alarm.md) |
| **Snooze settings**: how many snoozes (0–10, default 5), then only I'm up, and how they shorten from 9 minutes: constant, linear (default, 9 → 2) or exponential. Every snooze is counted and its time and length recorded | [alarm.md](alarm.md#snooze-and-im-up) |
| **Bedtime reminder** 30 minutes before the bedtime goal (default 21:30 for 22:00). Skipped once you're in bed | [alarm.md](alarm.md#bedtime-reminder) |
| **Sleep score maths** (0–100 with breakdown), night metrics and regularity, unit tested. Not shown in the app until there's sleep data (step 5) and a report (step 6) | [metrics.md](metrics.md) |
| **Sleep diary**: earplugs and context tags at the bed tap, a silent "Awake?" prompt with one-tap reasons when the screen wakes in the night, a "How did you sleep?" survey after I'm up, all shown on the night's page | [diary.md](diary.md) |
| **Caffeine log**: quick-add coffee, decaf and Coke from the Sleep tab, with caffeine projected to bedtime and caffeine at bedtime on the night's page | [diary.md](diary.md#caffeine-build-step-7) |
| Denial paths for microphone, notifications, battery restrictions (with Samsung advice), exact alarms, full-screen alarms and alarm volume | each page above |

## Next: v1 build order (remaining)

8. Trends and sleep debt
9. JSON export
10. Overnight test on the phone, then fixes

## Agreed, not built yet

- **A better sleep model from sound (investigation, started 2026-11-05).** The threshold estimate disagreed badly with Garmin on the first night: much of the noise was made in deep sleep. Plan: collect 1–4 weeks of nights with the per-second measured level, peak and three frequency bands, plus screen on/off/unlock events, alongside Garmin's stages (all stored since database version 5; see [sound-monitoring.md](sound-monitoring.md#what-each-stored-second-holds)). Then pull the database, graph sound against Garmin, and build a model from the data rather than assumptions, for example testing hysteresis (it may take more noise to wake you than to keep you awake). Data comes off the phone over `adb` until JSON export (step 9). Don't retune `SoundConfig` from single nights in the meantime.
- **Investigation phases (agreed 2026-11-05).** Phase 1: about a week of normal nights with earplugs, the S21 set up as on the first night, the Garmin and the diary; treat the first 2–3 nights as settling-in. Phase 2: 5 consecutive nights without earplugs, then at least 5 back with them, as the control. Later: the laptop with the Brio and the laptop's own mic recording whole nights (near and far), empty-room nights on the A17, and a separate sleep-video study (an infrared camera, or an mmWave radar such as the LD2410 on the user's ESP32-C2s). Design: [sleep diary](superpowers/specs/2026-11-05-sleep-diary-design.md).
- **Snooze investigation (agreed 2026-11-09).** Would ringing early when you stir during a snooze wake you more gently? Snooze times, lengths and curves are recorded from database version 8, alongside the sound and Garmin's stages. After a week or two of mornings, look at what happens inside each snooze before building anything. Ringing early when you go quiet was ruled out: everyone lies still straight after snoozing. Details in [alarm.md](alarm.md#snooze-investigation).
- **Editing in the diary**: change a drink's mg, the caffeine defaults (Profile) and past diary entries.
- **Mic calibration to dB SPL**: a few minutes beside a sound-meter app gives the S21's offset from dBFS, so the Sleep screen can show the room against WHO's 40 dB guideline. Not a blocker for collecting: the offset is one constant, applied afterwards to every stored second. **While collecting, keep the audio path fixed** so that one offset stays valid: same phone, `VOICE_RECOGNITION` source (no automatic gain), 16 kHz mono. A change means a separate calibration for nights after it.
- **Recordings of noise events** only if the band levels prove too coarse and an actual classifier is wanted (the user's call, 2026-11-05).

- **Opt-in loud-moment playback**: the loudest 5 short clips per night, kept for 7 nights, with a hard size cap. It's the one planned exception to "never store audio". Spec in the [v1 plan's "Later" list](plan/2026-11-04-v1-build-plan.md#later-not-v1-but-design-for-it).

## Future ideas

- **Philips Hue sunrise** through `AlarmEvents.onWakeWindowOpened`
- **Richer Garmin data** (HRV, SpO2, respiration, restless moments) behind `GarminSource`, possibly via the unofficial Garmin API
- **Connect IQ watch app** for a wrist-driven smart alarm
- **Sound classification**: snoring, talking, coughing
- **Falling-asleep sounds and stories**
- **Release signing and developer verification.** Play Protect blocks the debug-signed APK as "harmful" (2026-11-04), most likely because of background microphone use, a boot receiver and the battery-exemption request from an unknown developer. For now the user taps "Install anyway", and permissions stay as they are (user's decision). Revisit a release key when Google's developer verification for sideloaded apps reaches South Africa, or once JSON export (step 9) gives a safe backup: switching keys needs one uninstall, which would wipe the nights otherwise. Installs over `adb` are reportedly exempt from verification.
- **Thinner storage for old nights**: per-minute summaries instead of per-second levels after a while (about 1.3 MB per night today)
- **Smart-trigger tuning** once there are real nights to compare against Garmin stages
