# SleepTight

Yet another sleep app, built to track and improve sleep quality. It's a personal Android sleep tracker that runs entirely on the phone and can optionally pull in Garmin data through Health Connect.

> **Status:** early development. The full v1 plan is in [`docs/PLAN.md`](docs/PLAN.md), and progress is tracked against its [build order](#build-order).

## What it does

- **Bed and wake taps.** Tap **Going to bed** at night and **I'm up** in the morning. Those two taps set the night's boundaries, and everything else fits inside them.
- **Overnight sound monitoring.** A foreground service measures sound level once a second, relative to a rolling 20-minute baseline. It logs noise events and uses per-minute activity as a rough movement proxy. **No audio is ever recorded or stored, only levels.**
- **Smart alarm.** It wakes you within a window (default 05:45–06:15) when activity suggests light sleep, or at the end of the window otherwise. Volume ramps up over 60 seconds, and every snooze is counted.
- **Garmin via Health Connect.** It imports sleep stages and heart rate written by Garmin Connect, trimmed to your bed and wake taps. Without Garmin data, it estimates asleep or awake from activity and labels the estimate as such.
- **An explainable sleep score (0–100).** Duration, efficiency, deep + REM, interruptions and regularity, each shown as a bar with its points and the raw value behind it.
- **Goal adherence.** It checks bed by 22:00, 8 hours asleep and at most 1 snooze automatically, from taps and alarm events. These goals feed the regularity metric.
- **Caffeine log.** Quick-add coffee and Coke, with an estimate of the caffeine still in your system at bedtime (5-hour half-life by default).
- **Trends and sleep debt.** 7- and 30-day views of score, sleep, bedtime, regularity, a quality-adjusted sleep debt estimate, and caffeine against score.
- **JSON export** of all your data through the system file picker.

## Principles

1. **The phone is the source of truth for the night's window.** Garmin only adds detail inside it.
2. **Every number can be explained.** The score always shows its breakdown, and metrics sit next to reference ranges.
3. **Privacy first.** Everything stays on the device, with no account and no cloud. Sound levels only, never audio.
4. **Goals, not rules.** The app records snoozes and late nights and never blocks them.

## Tech stack

| Area | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Storage | Room |
| Garmin data | Android Health Connect client |
| Sound monitoring | Foreground service (`microphone` type), `AudioRecord` at 16 kHz mono |
| Alarm | `AlarmManager.setAlarmClock` with `USE_EXACT_ALARM` |
| Build | Gradle (Kotlin DSL) with a version catalog, AGP 9, KSP |
| Tests | JUnit, mainly on the pure-Kotlin `:metrics` module |
| Platform | minSdk 28 (Android 9), target/compile SDK 37 |

## Project layout

```
app/                      Android application module
  src/main/java/io/github/murrayb52/sleeptight/
    data/                 Room entities, DAOs, repository
    tracking/             NightService (mic dB logging), noise event detection
    alarm/                AlarmScheduler, AlarmReceiver, AlarmActivity, AlarmEvents hooks
    health/               HealthConnectImporter
    caffeine/             Drink logging UI + defaults
    export/               JSON export
    ui/                   Screens: Home, Tonight, Morning report, Trends, Caffeine, Settings
  src/main/res/raw/       Bundled CC0 alarm sounds (sources and licences in ASSETS.md)
metrics/                  Pure Kotlin/JVM: night metrics, score, regularity, caffeine, sleep debt
docs/PLAN.md              The v1 build plan
```

`metrics` is a separate Gradle module rather than a package inside `app`. That way the build itself enforces the "no Android dependencies" rule, and its tests run on a plain JVM in seconds without an emulator or the Android SDK.

## Building

### Requirements

- JDK 17 or newer (21 recommended)
- Android SDK with platform 37. Either install [Android Studio](https://developer.android.com/studio), or use the command-line tools and point `ANDROID_HOME` (or `sdk.dir` in `local.properties`) at the SDK.

You don't need a separate Gradle install. Use the wrapper:

```bash
./gradlew test                 # all unit tests
./gradlew :metrics:test        # just the metrics module (fast, no Android needed)
./gradlew assembleDebug        # build the debug APK → app/build/outputs/apk/debug/
./gradlew installDebug         # install on a connected phone (USB debugging on)
```

CI (GitHub Actions) runs the unit tests and builds the debug APK on every push to `main` and on every pull request.

## Permissions (as features land)

| Permission | Why |
|---|---|
| `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` | Overnight sound levels (no audio stored) |
| `POST_NOTIFICATIONS` | The persistent "tracking tonight" notification and the alarm |
| `USE_EXACT_ALARM` | It's an alarm app, so the alarm has to fire on time |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Some Android brands kill background services overnight |
| Health Connect: sleep sessions, heart rate (read) | Optional Garmin data |

Each permission has a clear denial path. The app keeps working with reduced features instead of failing silently.

## Build order

Taken from [`docs/PLAN.md`](docs/PLAN.md):

- [x] 0. Repo setup: Gradle, Compose, Room, README, `CLAUDE.md`, CI
- [ ] 1. Data model + bed/wake taps + Home screen
- [ ] 2. Metrics module + score + regularity + unit tests
- [ ] 3. NightService: mic dB logging, baseline, noise events, Tonight screen
- [ ] 4. Alarm: scheduling, smart trigger, ramp, snooze counting, `AlarmEvents` hooks, sounds
- [ ] 5. Health Connect import + trimming + fallback estimation
- [ ] 6. Morning report screen
- [ ] 7. Caffeine log + caffeine at bedtime
- [ ] 8. Trends + sleep debt
- [ ] 9. JSON export
- [ ] 10. Overnight test on a real phone, then fixes

## Later (designed for, not built)

- Philips Hue sunrise, through `AlarmEvents.onWakeWindowOpened`
- Richer Garmin data (HRV, SpO2, respiration, restless moments) behind a `GarminSource` interface
- A Connect IQ watch app for a wrist-driven smart alarm
- Sound classification (snoring, talking, coughing)
- Falling-asleep sounds and stories

## Disclaimer

SleepTight is a personal tracking tool, not a medical device. The score, the sleep debt and the estimated stages are approximations. Talk to a doctor about any sleep concerns.

## Licence

[MIT](LICENSE) © 2026 Murray Buchanan
