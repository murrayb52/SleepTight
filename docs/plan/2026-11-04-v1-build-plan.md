# Sleep tracker app — v1 build plan

A personal Android sleep tracker. It runs entirely on the phone and can optionally use Garmin data. Target build time is a few hours, so the scope is deliberately small. Everything listed under "Later" stays out of v1, but the code leaves hooks for it.

## Goals and principles

- The phone is the source of truth for **when I went to bed and when I got up**, via explicit button taps. Garmin data only adds detail inside that window.
- Every number the app shows can be explained. The sleep score always shows its breakdown.
- The app never stores raw audio. It keeps sound levels only.
- Personal goals (not hard rules): **in bed by 22:00**, **8 hours asleep**, **at most 1 snooze**. They are evaluated automatically from taps and alarm events, and feed the regularity metric. They are not shown as a checklist.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Room for local storage
- Android Health Connect client for reading Garmin data (Garmin Connect writes sleep stages and heart rate there)
- Foreground service (`foregroundServiceType="microphone"`) for overnight sound monitoring
- `AlarmManager.setAlarmClock` for the alarm (`USE_EXACT_ALARM` permission, since this is an alarm app)
- minSdk 28, targetSdk latest stable
- JUnit for the metrics module, which is pure Kotlin with no Android dependencies so it can be unit tested

## Project layout

```
app/
  data/        Room entities, DAOs, repository
  tracking/    NightService (mic dB logging), noise event detection
  alarm/       AlarmScheduler, AlarmReceiver, AlarmActivity, AlarmEvents hooks
  health/      HealthConnectImporter
  metrics/     Pure Kotlin: night metrics, score, regularity, caffeine, sleep debt
  caffeine/    Drink logging UI + defaults
  export/      JSON export
  ui/          Screens: Home, Tonight, Morning report, Trends, Caffeine, Settings
assets/        Alarm sounds (CC0) + ASSETS.md listing source and license
```

## Data model (Room)

- **Night**: id, bedTapAt, wakeTapAt, alarmWindowStart, alarmWindowEnd, alarmFiredAt, snoozeCount, garminImported (bool), scoreJson (cached breakdown)
- **SoundSample**: nightId, timestamp (1 per second, or 1 per 5 s if storage is a concern), dbRelative
- **NoiseEvent**: nightId, start, end, peakDbRelative
- **SleepStageSegment**: nightId, start, end, stage (AWAKE / LIGHT / DEEP / REM / UNKNOWN), source (GARMIN / ESTIMATED)
- **HeartRateSample**: nightId, timestamp, bpm
- **CaffeineEntry**: id, timestamp, drinkType, variant fields, mg (resolved at log time, editable)
- **Settings**: targetBedtime=22:00, targetSleep=8h, maxSnoozes=1, alarm window, snooze length, caffeine defaults

## Feature specs

### 1. Bed and wake taps
- Home screen: a large **Going to bed** button. Once tapped, it changes to **I'm up**.
- "Going to bed" creates a Night, starts NightService, and schedules the alarm.
- "I'm up" closes the Night, stops the service, triggers the Health Connect import and opens the Morning report.
- If a Night is left open for more than 16 hours, ask whether to close it.

### 2. Overnight sound monitoring (NightService)
- `AudioRecord` at 16 kHz mono. Compute RMS → dB once per second. No audio is written to disk.
- The phone mic isn't calibrated, so work relative to a **baseline**: the rolling median of the last 20 minutes.
- **Noise event**: dB > baseline + 15 for at least 1 s. Merge events less than 30 s apart.
- **Activity per minute**: the number of seconds above baseline + 8. This serves as a rough movement proxy for the smart alarm and the fallback sleep estimate.
- Show a persistent notification. Ask the user to exempt the app from battery optimisation on first run, since some Android brands kill background services.

### 3. Alarm
- Default wake window: **05:45–06:15** (8 h after a ~22:15 sleep onset). Editable.
- At window start, fire an `onWakeWindowOpened` event (the future Hue sunrise hooks in here) and start checking activity.
- **Smart trigger**: if activity in the last 2 minutes is above that night's 75th percentile, sound the alarm early. Otherwise sound it at window end.
- Volume ramps from 10% to 100% over 60 s.
- Snooze is 9 min (editable) and every snooze is counted. Snoozing isn't blocked; it's only recorded. *(Changed 2026-11-04: snoozes shrink and are capped at 5; see the notes at the end.)*
- Sounds: 3–5 CC0 files from Freesound, bundled in `res/raw`, with source URLs and licenses listed in `ASSETS.md`. Also include a generated sine/chime tone so the app works with zero assets.
- `AlarmEvents` interface: `onWakeWindowOpened`, `onAlarmFired`, `onSnooze`, `onDismiss`. v1 uses a no-op implementation, and the Hue integration will implement it later.

### 4. Garmin via Health Connect
- Request read permissions for `SleepSessionRecord` and `HeartRateRecord`.
- Import on "I'm up", then retry when the app opens and on a manual refresh, because Garmin only writes data after the watch syncs.
- **Trim all Garmin segments to [bedTapAt, wakeTapAt].** The taps define the night.
- When new data arrives, recompute the night's metrics and score.
- **Fallback without Garmin**: estimate sleep vs awake from activity per minute (a minute with activity above the threshold = awake, a run of 20 or more quiet minutes = asleep). Mark the stages as ESTIMATED and don't estimate deep or REM.

### 5. Night metrics (metrics module)
- **Time in bed (TIB)** = wakeTap − bedTap
- **Sleep onset** = first non-awake segment. **Time to fall asleep** = onset − bedTap
- **Total sleep (TST)** = sum of non-awake segments
- **Wake-ups** = awake segments of 2 minutes or more after onset
- **Efficiency** = TST / TIB
- **Deep + REM share** = (deep + REM) / TST (Garmin data only)
- **Noise events** count, plus a list with times

### 6. Sleep score (0–100, breakdown stored and shown every day)
| Component | Max | Calculation |
|---|---|---|
| Duration | 35 | 35 × min(TST / 8h, 1) |
| Efficiency | 25 | 0 at ≤70%, linear to 25 at ≥90% |
| Deep + REM | 20 | 20 at ≥40% of TST, linear to 0 at ≤20%. If Garmin data is missing, redistribute the weight proportionally across the other components |
| Interruptions | 10 | 10 − 2 per wake-up − 1 per noise-event cluster, floor 0 |
| Regularity | 10 | See section 7 |

All weights and thresholds live in a single `ScoreConfig` so they're easy to tune. The Morning report shows each component as a bar with its points and the raw value behind it, for example "Efficiency 21/25 — 88%".

### 7. Regularity (goal adherence)
Evaluated automatically per night:
- **Bedtime**: 1.0 if bedTap ≤ 22:00, falling linearly to 0 at 23:00
- **Duration**: 1.0 if TST ≥ 8h, falling linearly to 0 at 6h
- **Snoozes**: 1.0 at ≤1, 0.5 at 2, 0 at 3 or more
- **Consistency**: standard deviation of bedTap over the last 7 nights. Full marks at ≤15 min, 0 at ≥60 min

Nightly regularity = the mean of the four, × 10 for the score. Trends show 7-day and 30-day averages of each factor separately.

### 8. Caffeine log
- Quick-add buttons:
  - Coffee: caffeinated / decaf × home / restaurant × single / double
  - Soft drink: Coke / Coke Zero × 250 ml / 500 ml / 1 L
- Default mg values live in Settings and are editable. These are rough starting points to verify:
  - Espresso shot: home 63 mg, restaurant 75 mg (×2 for a double)
  - Decaf shot: 3 mg
  - Coke / Coke Zero: 10 mg per 100 ml → 25 / 50 / 100 mg
- **Caffeine at bedtime** = Σ mg × 0.5^(hours between drink and bedTap / 5). The half-life is a setting, default 5 h.
- Show this on the Morning report. Trends plot it against the sleep score.

### 9. Sleep debt (sleep pressure estimate)
- Quality-adjusted sleep per night: QAS = TST × (0.7 + 0.3 × score/100)
- **7-day debt** = Σ (8h − QAS) over the last 7 nights. A surplus offsets debt, capped at +1h per night.
- **30-day baseline** = the mean nightly debt over 30 nights × 7, for comparison
- Display: "Sleep debt: 3h 20m (7 days) — 40 min above your 30-day norm". Label it as an estimate.

### 10. Reference ranges (in place of "global benchmarks")
Show these next to each metric. Keep them in one constants file:
- Total sleep 7–9 h
- Efficiency ≥ 85%
- Time to fall asleep ≤ 30 min
- Deep ~13–23% of total sleep, REM ~20–25%

### 11. JSON export
- Settings → Export opens the Storage Access Framework file picker.
- The file includes a schema version, settings, all Nights with segments, noise events, heart rate, score breakdowns, and caffeine entries. Raw SoundSamples are optional (off by default because of size).

## Screens
1. **Home**: bed / up button, next alarm window, last night's score, quick caffeine add
2. **Tonight**: live relative dB, noise events so far, alarm window
3. **Morning report**: hypnogram (Garmin or estimated), score breakdown bars, metrics with reference ranges, noise events timeline, caffeine at bedtime, goal adherence for the night
4. **Trends**: 7 and 30 days of score, TST, bedtime, regularity factors, sleep debt, caffeine vs score
5. **Caffeine**: log list, quick add, edit or delete
6. **Settings**: goals, alarm window, snooze length, sound, caffeine defaults, half-life, Health Connect permissions, export

## Build order (one commit or more per step)
0. **Repo setup**: Gradle, Compose, Room, `.gitignore`, README, `CLAUDE.md` with conventions (run unit tests before each commit)
1. **Data model + bed/wake taps + Home screen**
2. **Metrics module + score + regularity + unit tests**, using synthetic nights. Do this early because it's the core logic.
3. **NightService**: mic dB logging, baseline, noise events, Tonight screen
4. **Alarm**: scheduling, smart trigger, ramp, snooze counting, `AlarmEvents` hooks, bundled sounds
5. **Health Connect import** + trimming to the tap window + fallback estimation
6. **Morning report** screen
7. **Caffeine log** + caffeine-at-bedtime calculation
8. **Trends** + sleep debt
9. **JSON export**
10. **Overnight test on a real phone**, then fix whatever breaks

## Later (not v1, but design for it)
- **Philips Hue sunrise**: implement `AlarmEvents.onWakeWindowOpened` to start a light ramp.
- **Richer Garmin data** via the unofficial Connect API: per-minute movement, restless moments, HRV, SpO2, respiration, Garmin's sleep score. Put it behind a `GarminSource` interface so it plugs into the same importer.
- **Connect IQ watch app** for live wrist movement, which would enable a watch-driven smart alarm.
- **Sound classification** (snoring, talking, coughing).
- **Falling-asleep sounds**: sleep sounds and stories.
- **Loud-moment playback** (requested 2026-11-04): play back short clips of the loudest moments of recent nights. This is the one planned exception to "never write audio to disk", and it's tightly bounded:
  - **Opt-in**, off by default, with a clear explanation in Settings (clips may capture anyone else in the room). While it's on, the tracking notification says clips are being kept.
  - **Only loud moments.** A clip is cut around a noise event: about 5 s before it (from an in-memory ring buffer) until the level falls back towards baseline, at most 20 s. Nothing else is ever written.
  - **Loudest 5 per night** by peak dB. During the night, a louder event replaces the quietest kept clip, so at most 5 clip files exist per night.
  - **Rolling 7 nights.** Clips from older nights are deleted automatically, and deleting a night deletes its clips.
  - **Hard size cap** (for example 20 MB in total) as a safety net. AAC at 16 kHz mono is roughly 4 KB/s, so 35 clips of 15 s come to about 2 MB.
  - Stored in app-private storage only, never in shared storage, never in the JSON export, never backed up.
  - Playback from the Morning report's noise timeline and a "Loud moments" list.
  - **Open question:** loudest 5 per night (up to 35 clips) or 5 across the whole 7 days. Assumed per night until confirmed.
  - **Design for it in step 3:** `NightService` reports noise events through a `NoiseEvents` interface (no-op in v1), so clip capture plugs in later without restructuring. The in-memory pre-roll buffer is added with the feature itself.

## Risks to watch
- Some Android brands kill foreground services despite exemptions. Test a full night early (after step 3).
- Health Connect data can arrive hours late, so the score must be able to recompute.
- Exact alarm and microphone foreground service permissions differ by Android version. Handle the denial paths clearly.

## Notes on departures

- **2026-11-04 (step 1):** `Night` also stores `zoneId`, the device zone at the bed tap, so local-time rules stay right for past nights after travelling. A bed tap before 12:00 belongs to the previous date's night. The 16-hour prompt offers "Set wake time" (a clock picker) and "Keep it open" rather than closing at the current time, which would record a forgotten night as 20+ hours in bed.
- **2026-11-04 (step 2):** The final awakening before "I'm up" doesn't count as a wake-up. `UNKNOWN` stage counts as asleep. A noise-event cluster is a run of events each starting within 5 min of the previous one ending. The duration component and the regularity factors use the user's goals from Settings rather than fixed 22:00, 8 h and 1 snooze. Consistency covers the 7 nights ending with the scored night and needs at least 3 of them. Without sleep data there's no score.
- **2026-11-04 (step 3):** The Tonight screen is Home's in-bed state rather than a separate screen, so there's nothing to navigate at night. The service holds a partial wake lock (20-hour timeout) and uses the `VOICE_RECOGNITION` source to avoid automatic gain control. The baseline excludes the current second. Opening the app restarts monitoring if Android stopped it. Activity per minute is derived from stored samples rather than stored. Measured storage is about 1.3 MB per 8-hour night at one sample per second, which is kept for now.
- **2026-11-04 (step 4):** Home has an alarm on/off switch (`Settings.alarmEnabled`, schema version 2), so nights without an alarm are possible. The smart trigger also needs the last 2 minutes to average at least 3 active seconds, and the night to have 30 minutes of history, so a single cough on a quiet night can't ring it early. An unanswered alarm stops after 10 minutes. Only the generated chime ships for now. The Freesound CC0 sounds need an account to download, and choosing a sound waits for the Settings screen.
- **2026-11-04 (after step 4, user request):** No vibration on the alarm. Snoozes now get shorter (9 → 7 → 5 → 3 → 2 min) and **are capped at 5**, after which the alarm can only be stopped. This replaces "Snoozing isn't blocked". A bedtime reminder notification comes 30 min before the bedtime goal. A redesign with a bottom nav (Sleep · History · Profile), an editable alarm time and a bedside check at the bed tap are agreed, pending mockup approval. Agreed and future work is tracked in [`docs/Roadmap.md`](../Roadmap.md).
- **2026-11-04 (redesign, user-approved mockups):** Bottom nav (Sleep · History · Profile), a night-sky look, an editable wake-by time and window, a bedside check at the bed tap, and press-and-hold I'm up. The Tonight screen stays the Sleep tab's in-bed state. User enhancements: twinkling stars in bed; a very slow sunrise on the alarm screen; Stop sweeps to day with a summary of the night so far (Stop only silences the alarm: **I'm up stays a separate tap**, confirmed by the user, and the summary offers it); swipe up to snooze; snooze lengths fixed (not a setting) and never shown; score dials are arcs proportional to the score. A Settings screen now exists as part of Profile. See [`docs/ui.md`](../ui.md).
- **2026-11-04 (alarm sounds):** Five CC0 Freesound recordings, picked by the user from a shortlist (sounds that start loud and fade were ruled out because they fight the volume ramp), plus the generated chime as default and fallback. Chosen in Profile with previews. Sources in `ASSETS.md`.
- **2026-11-04 (step 5):** Health Connect reads are filtered to Garmin Connect's package, so Samsung Health's sleep never doubles up. Sessions are read with a 12-hour margin either side of the taps, then trimmed. "Sleeping" and unknown stages count as asleep with no stage. Retries happen after I'm up, on app start for nights that ended in the last 3 days, and from a button on the night's page. The fallback estimate counts a minute as awake at 3 or more active seconds, keeps missing minutes as gaps, and is stored as `ESTIMATED` segments. Scores are cached in `Night.scoreJson` with a version and rebuilt on start, after imports and when goals change. Debug builds alone may write to Health Connect, for the emulator test.
- **2026-11-04 (step 6 and fixes):** The Morning report is the night's page (reached from the morning summary, Last night and History) rather than a separate screen. Caffeine at bedtime joins it in step 7. The Sleep tab's alarm on/off chip is gone: every bed tap arms the alarm, and a single night's alarm can be turned off in bed (`Settings.alarmEnabled` is no longer used). Sleep and snooze goals are notched sliders. Nights under 5 minutes are discarded as mis-taps, and any night can be deleted. The live sound display updates every 100 ms while the stored level stays once a second.
- **2026-11-05 (first overnight test, fixes):** The fallback estimate needed 20 quiet minutes after every restless minute, which scored a night Garmin measured at 7 h 1 min as 1 h 43 min. The quiet run now applies only to falling asleep (10 minutes; again after 15 minutes awake or missing), and a restless minute is 5 seconds over 12 dB (was 3 over 8). Tuned against that night's Garmin stages; see [`docs/metrics.md`](../metrics.md#sleep-estimate-without-garmin-sleepestimator). The alarm's Stop and I'm up are now one press-and-hold I'm up, which also ends the night (reversing the 2026-11-04 choice of separate taps). Chime and Music box were removed as alarm sounds (too bright, startling); Birdsong is the default, and the generated chime is kept only as a fallback for a file that won't play. The Sleep tab before bed fits on one screen: the bedtime goal, last night and the window chip are gone (they live in Profile and History), and the alarm line is one short sentence.
- **2026-11-05 (sound-model investigation):** Rather than tune thresholds night by night, the app now records what's needed to build a model later: each second's measured level, peak and three band levels (database version 5), and screen on/off/unlock events, alongside Garmin. Garmin nights are re-checked for 3 days, because Garmin rewrites them, with a swipe-away note when a score changes. The bedtime goal is back on the Sleep tab (it fits); the wake wheel previews its sentence live. See the Roadmap.
- **2026-11-05 (step 7 with the sleep diary):** Caffeine logging is a sheet from the Sleep tab rather than its own screen, with "when" as Now or 1–8 hours ago. The mg defaults stay in `Settings` but aren't editable in the app yet, and logged drinks can be deleted but not edited. Built together with a sleep diary for the sound-model investigation: earplugs and context at the bed tap (the bed-tap sheet is now always shown; its checks are single lines), a silent wake-note prompt when the screen turns on in the night, and a morning survey after I'm up (Done on the alarm's morning summary now opens the app). See [`docs/diary.md`](../diary.md). Also fixed: ending a night crashed the app as the screen-event receiver was unregistered from an already-destroyed service (it now lives on the application context).
- **2026-11-09 (alarm, user request):** The alarm now eases in from 2 % to 100 % over 3 minutes on an exponential curve (was 10 % to 100 % linearly over 60 s, which started too loud). Snoozes are configurable again, replacing the fixed ladder: how many (0–10, default 5) and how they shorten from 9 min (constant, linear to 2 min, or exponential to 2 min). Each snooze's time, length and curve is recorded (`snooze_events`, database version 8) for an investigation into ringing early when you stir during a snooze, rather than building that on guesswork.
