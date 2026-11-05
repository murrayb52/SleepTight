# Nights, bed and wake taps, data model

How a night is recorded, what's stored, and how the Home screen behaves. Code: `app/.../data/` (entities, DAOs, `NightRepository`), `app/.../ui/home/`, and `metrics/.../NightDate.kt`.

## The two taps

- **Going to bed** creates a `Night` with `bedTapAt` = now and `zoneId` = the device's current time zone. If a night is already open, nothing new is created and the open one is returned, so a double tap can't open two nights.
- **I'm up** sets `wakeTapAt` = now on the open night. The repository rejects a wake tap that isn't after the bed tap or is in the future. From the Home button, that can only happen if the phone's clock jumps backwards overnight; Home then shows a snackbar asking you to check the date and time settings, rather than crashing or closing the night with a negative length.
- Only one night is ever open. "Open" means `wakeTapAt` is null.

From step 3 onwards, these taps will also start and stop the overnight service, schedule the alarm and trigger the Garmin import.

## Which date a night belongs to

`NightDate.of(bedTapAt, zone)` names a night after the evening it started. A bed tap before **12:00 local time** counts towards the previous date, so 00:30 on the 4th is the night of the 3rd. The noon cutoff is `NightDate.DAY_STARTS_AT`. It lives in `:metrics` because regularity (step 2) uses the same rule.

The zone is the night's stored `zoneId`, not the phone's current zone. A night recorded in Johannesburg keeps its local times after you fly somewhere else.

## Forgotten "I'm up" (the 16-hour prompt)

If the open night is at least **16 hours** old (`OPEN_NIGHT_PROMPT_AFTER`), Home asks "Still in bed?" and shows when the night started and how long ago that was. Home re-checks every minute while it's on screen.

- **Set wake time** opens a clock picker, pre-filled with bed tap + your sleep goal (8 h by default). The picked clock time resolves to its first occurrence *after* the bed tap. For a 22:00 bed tap, 06:15 means the next morning and 23:30 means the same evening. If that moment hasn't happened yet, a snackbar says so and the night stays open.
- **Keep it open** hides the prompt for that night until the app process restarts. It comes back on a later launch while the night is still open.

The picker exists because closing at "now" would record a forgotten night as 20+ hours in bed and wreck that night's metrics.

## Home screen

- Not in bed: the tagline, a large **Going to bed** button, and either last night's card or a one-line hint on first use.
- In bed: "In bed since 22:04", the time so far, and a large **I'm up** button in the tertiary colour so it's visibly a different state.
- **Last night** is the closed night with the latest bed tap: its bed and wake times, and time in bed.
- Times follow the phone's 12/24-hour setting.
- All text is centred, including text that wraps onto several lines, and the screen scrolls if large font sizes push it past the screen height. Checked at 1.3× font scale on a narrow (360 dp) display.

## Data model

All instants are epoch milliseconds (UTC). Every per-night table cascades on delete, so deleting a night removes everything recorded in it. Schemas are exported to `app/schemas/` and there's no destructive-migration fallback, because a schema change must never wipe recorded nights.

| Table | Columns | Notes |
|---|---|---|
| `nights` | id, bedTapAt, wakeTapAt?, zoneId, alarmWindowStart?, alarmWindowEnd?, alarmFiredAt?, snoozeCount, garminImported, scoreJson? | `scoreJson` is a cache, always recomputable |
| `sound_samples` | nightId, timestamp, dbRelative | Key (nightId, timestamp). Levels only, never audio |
| `noise_events` | id, nightId, start, end, peakDbRelative | |
| `sleep_stage_segments` | id, nightId, start, end, stage, source | stage: AWAKE / LIGHT / DEEP / REM / UNKNOWN; source: GARMIN / ESTIMATED |
| `heart_rate_samples` | nightId, timestamp, bpm | Key (nightId, timestamp) |
| `caffeine_entries` | id, timestamp, drinkType, decaf?, venue?, shots?, volumeMl?, mg | drinkType: COFFEE / COKE / COKE_ZERO. Coffee uses decaf, venue, shots; soft drinks use volumeMl. `mg` is resolved at log time and editable |
| `settings` | one row (id 0) | Defaults below |

Enums are stored by name.

### Settings defaults

| Setting | Default |
|---|---|
| Target bedtime | 22:00 |
| Target sleep | 8 h |
| Max snoozes | 1 |
| Alarm window | 05:45–06:15 |
| Snooze length | 9 min |
| Alarm sound | Birdsong |
| Espresso shot, home / restaurant | 63 / 75 mg |
| Decaf shot | 3 mg |
| Cola | 10 mg per 100 ml |
| Caffeine half-life | 5 h |

Until settings are first saved, the repository returns these defaults.
