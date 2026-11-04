# Garmin via Health Connect, and the fallback estimate

How SleepTight gets sleep stages and heart rate from a Garmin watch, and what it does without them. Code: `app/.../health/` (`GarminSource`, `HealthConnectGarminSource`, `GarminImporter`, `HealthDataInfoActivity`) and `metrics/.../SleepEstimator.kt`.

## Where the data comes from

Garmin Connect (the phone app) writes the watch's sleep sessions, with stages, and heart rate into **Health Connect** after the watch syncs. SleepTight reads them from there; it never talks to Garmin directly and never writes to Health Connect.

- **Read only**: `READ_SLEEP` and `READ_HEART_RATE`, nothing else.
- **Garmin only**: reads are filtered to records written by Garmin Connect (`com.garmin.android.apps.connectmobile`). Samsung Health (which can also write sleep on Samsung phones) and any other app are ignored, so two sources can't double up.
- `GarminSource` is an interface, so the unofficial Garmin API (see the Roadmap) can replace Health Connect later without touching the importer.

## Connecting (Profile → Data)

The **Garmin via Health Connect** row shows the state and what tapping does:

| State | Row says | Tapping |
|---|---|---|
| Health Connect missing (some phones on Android 13 and earlier) | Not available on this phone | nothing |
| Health Connect needs installing or updating | Install Health Connect | opens its Play Store page |
| Not allowed yet | Connect | Health Connect's own permission screen |
| Refused | Open Health Connect, with a note explaining how to allow both permissions | Health Connect's settings (after repeated refusals it stops asking, so the settings page is the reliable way back) |
| Allowed | Connected | Health Connect's data settings |

The state is re-read whenever Profile comes into view, so changes made in Health Connect show straight away. Once connected, recent nights are imported at once.

Health Connect's "privacy policy" link opens **SleepTight and your health data**: what's read, what it's for, and that it stays on the phone.

## Importing a night

`GarminImporter.import(night)`:

1. Needs a closed night and access; otherwise "not connected".
2. Reads sleep sessions from **12 hours before the bed tap to 12 hours after the wake tap** (a session can start before you tapped), then **trims everything to the taps** (`trimToNight` in `:metrics`: clipped, overlaps removed). The taps define the night.
3. Stages map as: awake, awake in bed and out of bed → **awake**; light, deep, REM → the same; plain "sleeping" and unknown → **asleep, stage not known**. A session without stages counts as asleep throughout.
4. Heart rate inside the night is kept.
5. Garmin segments and heart rate replace any earlier import for that night, the night is marked `garminImported`, and it's **rescored** (Garmin stages win over the estimate).

If Health Connect has nothing for the night yet, that's "no data": the estimate stays, and the night is tried again later.

### When it runs

Garmin data only appears after the watch syncs, often a while after you get up, so the import is tried:

- **straight after I'm up** (if nothing's there yet, the night is scored from the estimate at once);
- **on every app start**, for nights that ended in the **last 3 days** and still have no Garmin data;
- **on the night's page**, from **Check for Garmin data** (shown while the night has no Garmin stages), which says what happened: imported, nothing yet, not connected, or couldn't read Health Connect.

## Without Garmin: the estimate

Nights with no Garmin data are scored from the sound monitor's activity: minutes with 3 or more active seconds are awake, runs of 20 or more quiet minutes are asleep, and missing minutes are left as gaps. There's no deep or REM estimate, so that part of the score is shared out. Details in [metrics.md](metrics.md#sleep-estimate-without-garmin-sleepestimator).

## Testing

- JVM: stage mapping; the importer's trimming, storage, rescoring and "already imported" handling against a fake source (on Room).
- Emulator, real Health Connect: the test writes sleep and heart rate as SleepTight (debug builds alone may write: `src/debug/AndroidManifest.xml`), reads them back through the source pointed at its own package, and checks the default Garmin-only source ignores them.
- **Phone only**: real Garmin Connect data, which needs the watch and the user's account.
