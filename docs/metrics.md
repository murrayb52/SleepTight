# Night metrics, sleep score, regularity

How the numbers behind a night are worked out. All of it lives in the pure-Kotlin `:metrics` module (`metrics/src/main/kotlin/.../metrics/`) and is unit tested against synthetic nights. Every weight and threshold is in `ScoreConfig`, the user's goals come in as `Goals`, and the reference ranges are in `ReferenceRanges`.

## Scoring in the app

`scoreNight` (in `app/.../scoring/`) turns one closed night's stored data into a score; `NightScorer` does the database work around it.

- **Which sleep data**: Garmin segments if the night has any; otherwise the estimate from sound (below), which is stored as `ESTIMATED` segments so the night's page can draw it. Without either, the night has no score but keeps its time in bed and noise events.
- **Inputs**: the taps, those segments, the noise events, the snooze count, the bed taps of the nights before (for consistency) and the goals from Settings.
- **Cache**: the result (score, parts with points and maximum, the measurements behind them, the regularity factors and the data source) is stored as JSON in `Night.scoreJson` (`ScoreCache`). Screens read the cache, never recompute. It carries a `version`; raising `ScoreCache.VERSION` makes every night rescore on the next app start.
- **When it runs**: straight after I'm up (from Tonight, the alarm screen, or closing an overdue night); on every app start for nights with no score or an old version; and for every night when the bedtime, sleep or snooze goal changes. It's always derivable from stored data, so running it again is safe.
- The sound table is only read when a night needs an estimate.

## Inputs

A `NightInput` holds the bed and wake taps, the sleep segments (each with a stage), where those segments came from (`GARMIN`, `ESTIMATED`, or null when there's no sleep data at all), and the noise events.

Stages are `AWAKE`, `LIGHT`, `DEEP`, `REM` and `UNKNOWN`. **`UNKNOWN` counts as asleep, stage not known.** The activity-based estimate produces `AWAKE` and `UNKNOWN` only.

## Sleep estimate without Garmin (`SleepEstimator`)

When a night has no Garmin stages, asleep and awake are estimated from the sound monitor's activity per minute (seconds more than 8 dB above the room's baseline; see [sound-monitoring.md](sound-monitoring.md#activity)):

- A minute with **3 or more active seconds** is **awake** (`SoundConfig.estimateAwakeActiveSeconds`).
- A run of **20 or more consecutive quiet minutes** is **asleep** (`estimateMinQuietMinutes`), the whole run included. Quiet runs shorter than that count as awake: you haven't settled yet.
- **Missing minutes leave a gap** (for example while the service wasn't running). Gaps count as neither asleep nor awake, so they lower efficiency rather than inventing sleep.
- Asleep segments are `UNKNOWN` (asleep, stage not known): there's **no deep or REM estimate**, so the score spreads that part's points over the others. Segments are merged where they touch and trimmed to the taps.

The thresholds are starting points, to be tuned against Garmin nights once there are some to compare.

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

Context shown next to each metric. They don't affect the score.

| Metric | Range |
|---|---|
| Total sleep | 7–9 h |
| Efficiency | ≥ 85 % |
| Time to fall asleep | ≤ 30 min |
| Deep | 13–23 % of total sleep |
| REM | 20–25 % of total sleep |
