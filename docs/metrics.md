# Night metrics, sleep score, regularity

How the numbers behind a night are worked out. All of it lives in the pure-Kotlin `:metrics` module (`metrics/src/main/kotlin/.../metrics/`) and is unit tested against synthetic nights. Every weight and threshold is in `ScoreConfig`, the user's goals come in as `Goals`, and the reference ranges are in `ReferenceRanges`.

## Scoring in the app

`scoreNight` (in `app/.../scoring/`) turns one closed night's stored data into a score; `NightScorer` does the database work around it.

- **Which sleep data**: Garmin segments if the night has any; otherwise the estimate from sound (below). The estimate is made and stored as `ESTIMATED` segments **either way** (since 2026-11-09), so the phone's own record of a Garmin night can still be seen: its measurements go in the cache's `phone` block, and the night's page shows them under "Recorded by this phone". The score never uses them when Garmin has the night. Without either, the night has no score but keeps its time in bed and noise events.
- **Inputs**: the taps, those segments, the noise events, the snooze count, the bed taps of the nights before (for consistency) and the goals from Settings.
- **Cache**: the result (score, parts with points and maximum, the measurements behind them, the regularity factors and the data source) is stored as JSON in `Night.scoreJson` (`ScoreCache`). Screens read the cache, never recompute. It carries a `version`; raising `ScoreCache.VERSION` makes every night rescore on the next app start.
- **When it runs**: straight after I'm up (from Tonight, the alarm screen, or closing an overdue night); on every app start for nights with no score or an old version; and for every night when the bedtime, sleep or snooze goal changes. It's always derivable from stored data, so running it again is safe.
- The sound table is read for every night scored, because every night gets an estimate. `ScoreCache` version 3 (2026-11-09) adds `phone`, so every night is rescored once on the next app start, which also gives older Garmin nights their phone record.

## Inputs

A `NightInput` holds the bed and wake taps, the sleep segments (each with a stage), where those segments came from (`GARMIN`, `ESTIMATED`, or null when there's no sleep data at all), and the noise events.

Stages are `AWAKE`, `LIGHT`, `DEEP`, `REM` and `UNKNOWN`. **`UNKNOWN` counts as asleep, stage not known.** The activity-based estimate produces `AWAKE` and `UNKNOWN` only.

## Sleep estimate without Garmin (`SleepEstimator`)

When a night has no Garmin stages, asleep and awake are estimated from the sound monitor's activity per minute (seconds more than 12 dB above the room's baseline; see [sound-monitoring.md](sound-monitoring.md#activity)):

- A minute with **5 or more active seconds** is **restless** (`SoundConfig.estimateAwakeActiveSeconds`); fewer is quiet.
- **Falling asleep** takes a run of **10 or more consecutive quiet minutes** (`estimateMinQuietMinutes`); the whole run counts as asleep. Every minute before that is awake.
- **Once asleep**, each quiet minute is asleep and each restless minute awake, so a stir costs only its own minutes. (Wake-ups still need 2 minutes or more to count; see below.)
- After **15 restless minutes in a row** (`estimateResettleMinutes`) you're awake again, and falling back asleep needs the 10 quiet minutes again. A gap of 15 minutes or more does the same.
- **Missing minutes leave a gap** (for example while the service wasn't running). Gaps count as neither asleep nor awake, so they lower efficiency rather than inventing sleep.
- Asleep segments are `UNKNOWN` (asleep, stage not known): there's **no deep or REM estimate**, so the score spreads that part's points over the others. Segments are merged where they touch and trimmed to the taps.

**Why it works this way (first overnight test, 2026-11-05).** The first rule needed 20 quiet minutes after *every* restless minute, and counted 8 dB with 3 active seconds as restless. On the night of 4–5 November it gave 1 h 43 min asleep. Garmin, for the same night, gave 7 h 1 min from 22:59 with one 4-minute wake. The sound monitor heard plenty during Garmin's deep and light sleep (5 % of all seconds cleared 8 dB), so sound is a weak sign of being awake: only louder, longer noise should count. Against Garmin's stages minute by minute, these settings (12 dB, 5 s, 10 quiet minutes) gave 6 h 42 min from 22:57, with 91 % of minutes agreeing; the old ones gave 59 %. That's one night: retune when more Garmin nights are in. Garmin's stages always win when they exist.

## Night metrics (`NightMetrics.of`)

Before anything is measured, segments are **trimmed to [bed tap, wake tap]**. Where segments overlap, a later one loses the overlapping part, so no minute is counted twice. Gaps between segments count as neither asleep nor awake.

| Metric | Definition |
|---|---|
| Time in bed (TIB) | wake tap − bed tap. Always known |
| Sleep onset | start of the first non-awake segment |
| Time to fall asleep | onset − bed tap |
| Total sleep (TST) | sum of non-awake segments |
| Wake-ups | runs of awake segments that start after onset, last **≥ 2 min** (`minWakeUp`) and are followed by more sleep. Back-to-back awake segments form one run |
| Efficiency | TST / TIB |
| Deep, REM, deep + REM share | stage time / TST. **Garmin nights only**, otherwise null |
| Noise events | events overlapping the night, in time order |
| Noise clusters | events grouped when one starts within **5 min** (`noiseClusterGap`) of the previous one ending |

Edge cases:

- **The final awakening isn't a wake-up.** Lying awake before tapping "I'm up" is waking up, not an interruption. An awake run with no sleep after it isn't counted.
- **No sleep data** (source null): TIB and noise are still reported, every sleep metric is null, and `hasSleepData` is false.
- **Awake all night**: no onset, TST 0, efficiency 0, no wake-ups.

## Sleep score (`SleepScore.of`)

| Component | Max | Points |
|---|---|---|
| Duration | 35 | 35 × min(TST / sleep goal, 1). The sleep goal is 8 h by default |
| Efficiency | 25 | 0 at ≤ 70 %, linear to 25 at ≥ 90 % |
| Deep + REM | 20 | 0 at ≤ 20 % of TST, linear to 20 at ≥ 40 % |
| Interruptions | 10 | 10 − 2 per wake-up − 1 per noise cluster, floor 0 |
| Regularity | 10 | 10 × the mean regularity factor (below) |

- **Without Garmin stages**, deep + REM is left out and its 20 points are spread proportionally over the rest, giving maxima of 43.75 / 31.25 / 12.5 / 12.5. The interruption penalties scale with it: one wake-up still costs a fifth of that component.
- Each component carries a `fraction` (0–1), its `maxPoints` and `points = fraction × maxPoints`, which is what the breakdown bars show. The raw value next to a bar (for example 85 % efficiency) comes from `NightMetrics`.
- The **total** is the sum of component points, rounded to the nearest whole number.
- **No sleep data, no score**: `SleepScore.of` returns null.

Example: 85 % efficiency → (0.85 − 0.70) / 0.20 = 0.75 → 18.75 / 25.

## Regularity (`Regularity.of`)

Four goal-adherence factors, each 0–1:

| Factor | 1.0 | 0.0 | Between |
|---|---|---|---|
| Bedtime | bed tap at or before the bedtime goal (22:00) | 60 min or more after it | linear |
| Duration | TST ≥ sleep goal (8 h) | 2 h or more below it (6 h) | linear |
| Snoozes | at most the snooze goal (1) | goal + 2 or more | 0.5 at goal + 1 |
| Consistency | bedtime standard deviation ≤ 15 min | ≥ 60 min | linear |

- Bedtimes are measured against **the goal on that night's own evening** (see `NightDate` in [nights.md](nights.md)), so a 00:30 bed tap is 2 h 30 min late, not 21 h 30 min early. A goal after midnight, such as 00:15, is placed on the following morning.
- **Consistency** uses the nights whose date falls in the 7 days ending with this night, including this one, with each night in its own recorded time zone. It's the population standard deviation of their minutes-late. With **fewer than 3 nights** it's left out.
- Duration is left out when there's no sleep data.
- **Mean** = the average of whichever factors exist. That mean × 10 is the regularity component.

The goals come from Settings (`Goals`). Changing a goal changes both the duration component and the regularity factors.

## Reference ranges (`ReferenceRanges`)

Context shown next to each metric on the night's page (the morning report). They don't affect the score. Each check gives **Below**, **In range** or **Above**; bounds are inclusive and shares are compared at whole percents, as shown.

| Metric | Range |
|---|---|
| Total sleep | 7–9 h |
| Efficiency | ≥ 85 % |
| Time to fall asleep | ≤ 30 min |
| Deep | 13–23 % of total sleep |
| REM | 20–25 % of total sleep |
