# Smart alarm, snoozes, `AlarmEvents` hooks

How the alarm decides when to ring, how it rings, and what's recorded. The decision maths is in `:metrics` (`Alarm.kt`) and the Android side in `app/.../alarm/`.

## Turning it on and off

Home has an **Alarm** switch with the window under it (default **05:45–06:15**, from Settings). It's stored as `Settings.alarmEnabled`, which was added in database version 2 with an automatic migration that keeps all existing data.

- **Before bed**, the switch decides whether the next bed tap arms an alarm.
- **In bed**, it arms or disarms tonight's alarm directly, and the card shows "Tonight's alarm: 05:45 – 06:15".
- **I'm up** (or closing an overdue night) disarms everything, including a ringing or snoozed alarm.

## The window

At the bed tap, `WakeWindow.after` picks the next time the start clock time comes round after the bed tap, and the next end time after that, in the night's own time zone:

- A 22:00 bed tap wakes the next morning, and a 01:30 tap the same morning.
- A bed tap inside the window (say 05:50) gets **tomorrow's** window.
- A window crossing midnight (23:50–00:20) works, and daylight-saving changes are handled by the zone.

The window is stored on the night (`alarmWindowStart`/`alarmWindowEnd`). If the alarm is disarmed before it ever rang, the window is cleared.

## Scheduling

| What | When | How |
|---|---|---|
| Window opens | Window start | Exact, Doze-allowed alarm → `AlarmEvents.onWakeWindowOpened` |
| Ring | Window end, or earlier if the smart trigger fires | `setAlarmClock`: exact, survives Doze, shows the alarm icon in the status bar |
| Snooze | Now + the next snooze length (see below) | Same `setAlarmClock` ring, rescheduled |

A reboot or a clock or time-zone change clears or shifts alarms. `AlarmReceiver` re-arms the open night's alarm on boot and on clock changes, unless it has already rung or its window has passed. Checked on the emulator: an armed alarm survived a reboot and rang on time.

## Smart trigger

While the sound monitor is running, once a minute inside the window (except its last minute) the service asks `SmartTrigger.shouldWakeNow`:

- Take the mean activity of the last **2 complete minutes**. Activity is seconds per minute more than 12 dB above baseline; see [sound-monitoring.md](sound-monitoring.md).
- Ring early if that mean is **above the night's 75th percentile** of per-minute activity (nearest-rank) **and** at least **3 active seconds** a minute.
- Needs at least **30 minutes** of activity history and both recent minutes present. The minute in progress is never used.
- At most one early ring per night, and none once the alarm has rung.

The 3-second minimum is an addition to the plan: on a dead-quiet night the 75th percentile is 0, so without it a single cough would ring the alarm up to 30 minutes early.

Activity per minute is kept in memory by the recorder and rebuilt from stored samples if the service restarts mid-night. **Without the microphone there's no smart trigger**, and the alarm rings at the end of the window.

## Ringing

`AlarmRingService` (a foreground service of type `mediaPlayback`) is started by the ring alarm. It:

- records `alarmFiredAt` on the night (first ring only, so snoozes don't overwrite it) and calls `AlarmEvents.onAlarmFired`
- plays the chosen sound (see [Sounds](#sounds)) on the **alarm stream**, with the volume easing in from **2 % to 100 % over 3 minutes**, exponentially so it rises evenly to the ear (about 14 % at 90 s; changed 2026-11-09 because 10 % linear over 60 s started too loud). There's **no vibration**: you asked for sound only (2026-11-04)
- shows a full-screen alarm over the lock screen, with the time, a press-and-hold **I'm up** and, while snoozes are left, a swipe up to snooze

If the phone is unlocked and in use, Android shows a heads-up notification with **Snooze** and **I'm up** instead of taking over the screen. Home also shows a "Good morning" card with **Snooze** while the alarm is ringing or snoozed; the press-and-hold I'm up below it ends the alarm and the night.

An unanswered alarm stops after **10 minutes**.

## Snooze and I'm up

- **Snooze** adds one to the night's `snoozeCount`, records a `snooze_events` row (when it was pressed, its length and the curve), reschedules the ring, shows "Alarm snoozed until 06:24" with an I'm up action, and calls `AlarmEvents.onSnooze`. The count feeds the regularity snooze factor (goal: at most 1). The rows are for the [snooze investigation](#snooze-investigation).
- **How many snoozes, and how long** are settings in Profile (since 2026-11-09; before that the ladder was fixed at 9 → 7 → 5 → 3 → 2):
  - **Snoozes allowed**: 0–10, default **5**. After the last one the alarm screen, the notification and Home offer only I'm up, with "No snoozes left. Time to get up." With 0 there's no snooze at all.
  - **Snooze lengths** (`SnoozeCurve`), always starting at **9 min** (`AlarmConfig.firstSnoozeMinutes`). With *n* snoozes, snooze *i* (0 to *n* − 1) sits at *t* = *i* / (*n* − 1) along the curve, and is rounded to whole minutes:
    - **Constant**: 9 min every time.
    - **Linear** (the default): equal steps down to **2 min** (`lastSnoozeMinutes`): 9 + (2 − 9)·*t*. Five snoozes give **9, 7, 6, 4, 2**.
    - **Exponential**: the same fraction shorter each time, down to 2 min: 9 · (2/9)^*t*. The big drops come first. Five snoozes give **9, 6, 4, 3, 2**.
  - One snooze is always 9 min. Two decreasing snoozes go straight from 9 to 2.
- The lengths aren't shown on the alarm screen, only in the Profile picker, so you choose a shape once rather than plan around it each morning. The marks under the alarm show one per snooze allowed (narrower when there are more than 6).
- The settings are read each time the alarm starts ringing, along with the night's snooze count, so the next length is right after a restart, and a change in Profile applies from the next ring. `Settings.snoozeMinutes` is no longer used.
- **I'm up** (the press-and-hold button on the alarm screen, or the notification's I'm up action; `ACTION_IM_UP`) stops the sound, cancels pending alarms, calls `AlarmEvents.onDismiss` **and ends the night** (`AppContainer.endNightFromAlarm`: wake tap now, tracking stopped, Garmin import or scoring). Until 2026-11-05 these were two taps, Stop and then I'm up, which was confusing in practice. Emulator-checked end to end (`AlarmImUpTest`).
- **Snooze** on the alarm screen is a **swipe up** anywhere.
- **An unanswered alarm** (after 10 minutes) only stops ringing. It doesn't end the night: you may not be there.

## Snooze investigation

Agreed 2026-11-09, instead of building an "intelligent snooze" on guesswork. The idea to test: within a snooze, ring early when you **stir** (as the smart alarm does), so the alarm lands as you surface. Ringing early when you go *quiet* doesn't work, because everyone lies still straight after snoozing.

What's recorded (database version 8): each snooze's time, length and curve in `snooze_events`. Sound is recorded right through the snoozes (`NightService` keeps going until I'm up), and Garmin's stages cover them too. Ringing periods run from each ring (`alarmFiredAt` for the first, then each snooze's time plus its length) to the next snooze or I'm up. Leave them out of any sound analysis: the mic hears the alarm.

To answer, after a week or two of mornings: does stirring inside a snooze come before you'd have woken anyway, and does it line up with Garmin's light sleep? Build the early ring only if it does. Snoozes before version 8 have only a count.

## Bedtime reminder

A notification **30 minutes before the bedtime goal** ("Bedtime in 30 minutes. Time to start winding down…"), so 21:30 for the default 22:00. `BedtimeReminder.next` picks the next such time, and `BedtimeReminderScheduler` keeps one exact alarm for it:

- re-set after each reminder (for tomorrow), on boot and clock changes, at app start, and whenever the bedtime goal or the reminder setting changes
- skipped if you're already in bed (a night is open), and cleared from the notification shade when you tap Going to bed
- on by default (`Settings.bedtimeReminderEnabled`, schema version 3). The switch arrives with the Profile screen

## `AlarmEvents`

The extension point for things that react to the alarm: `onWakeWindowOpened`, `onAlarmFired`, `onSnooze`, `onDismiss`. v1 uses `AlarmEvents.None`. The Philips Hue sunrise will implement `onWakeWindowOpened`.

## Permissions and warnings

Shown in the alarm card when the alarm is on:

| Situation | What you see |
|---|---|
| Alarm volume at 0 | "Your alarm volume is off, so the alarm will only vibrate…" |
| Exact alarms not allowed (Android 12 only; 13+ grants `USE_EXACT_ALARM` to alarm apps) | Explanation and **Allow**, which opens "Alarms & reminders" |
| Full-screen notifications not allowed (Android 14+) | It still rings, but you must unlock to stop it. **Allow** opens the setting |
| Notifications off | No alarm screen or buttons. It rings for up to 10 minutes; open SleepTight to stop it. The warning's Allow opens the app's notification settings |

## Sounds

Four sounds (`AlarmSound`), chosen in **Profile → Alarm → Sound**: **Birdsong** (the default), Morning piano, Kalimba drops and Kalimba. All are CC0 recordings from Freesound in `res/raw`; sources and preparation in [`ASSETS.md`](../ASSETS.md).

- **Chime and Music box were removed on 2026-11-05.** Their bright, ringing pitch startled the user awake.
- The choice is stored as a key in `Settings.alarmSound`. No key, or the key of a removed sound (`music_box`), means Birdsong.
- Sounds loop with `MediaPlayer` on the alarm stream, with the 2 → 100 % ease-in (`AlarmPlayer`). If a file can't be played, the generated chime (`Chime`, a C–E–G bell, looped with a static `AudioTrack`) rings instead: it's no longer a choice, but an alarm that rings beats one that doesn't.
- In the picker, each sound shows who made it ("by F1tz · CC0 · Freesound"), from the same `SoundCredit` as the credits page. Tapping a sound selects it and plays a preview at half volume on the alarm stream, so it follows the alarm volume. The preview stops on the next tap or when the picker closes.
- Emulator-checked: with Birdsong selected, the ring plays a looping `MediaPlayer` with `USAGE_ALARM`.
