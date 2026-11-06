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
| Snooze | Now + snooze length (9 min) | Same `setAlarmClock` ring, rescheduled |

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
- plays the chosen sound (see [Sounds](#sounds)) on the **alarm stream**, with the volume ramping from **10 % to 100 % over 60 s**. There's **no vibration**: you asked for sound only (2026-11-04)
- shows a full-screen alarm over the lock screen, with the time, a press-and-hold **I'm up** and, while snoozes are left, a swipe up to snooze

If the phone is unlocked and in use, Android shows a heads-up notification with **Snooze** and **I'm up** instead of taking over the screen. Home also shows a "Good morning" card with **Snooze** while the alarm is ringing or snoozed; the press-and-hold I'm up below it ends the alarm and the night.

An unanswered alarm stops after **10 minutes**.

## Snooze and I'm up

- **Snooze** adds one to the night's `snoozeCount`, reschedules the ring, shows "Alarm snoozed until 06:24" with an I'm up action, and calls `AlarmEvents.onSnooze`. The count feeds the regularity snooze factor (goal: at most 1).
- **Snoozes get shorter and run out** (`SnoozeLadder`). Each snooze is 2 minutes shorter than the last, never under 2: **9 → 7 → 5 → 3 → 2**. The ladder is baked in (`AlarmConfig.baseSnoozeMinutes`), isn't a setting, and the lengths are never shown in the app. `Settings.snoozeMinutes` is no longer used. After **5 snoozes** the alarm screen, the notification and Home offer only I'm up, with "No snoozes left. Time to get up." The next snooze length is worked out from the night's count when the alarm starts ringing, so it's right after a restart too. Emulator-checked: snooze 5 is 2 minutes, and the re-ring offered only Stop (now I'm up).
- **I'm up** (the press-and-hold button on the alarm screen, or the notification's I'm up action; `ACTION_IM_UP`) stops the sound, cancels pending alarms, calls `AlarmEvents.onDismiss` **and ends the night** (`AppContainer.endNightFromAlarm`: wake tap now, tracking stopped, Garmin import or scoring). Until 2026-11-05 these were two taps, Stop and then I'm up, which was confusing in practice. Emulator-checked end to end (`AlarmImUpTest`).
- **Snooze** on the alarm screen is a **swipe up** anywhere.
- **An unanswered alarm** (after 10 minutes) only stops ringing. It doesn't end the night: you may not be there.

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
- Sounds loop with `MediaPlayer` on the alarm stream, with the 10 → 100 % ramp (`AlarmPlayer`). If a file can't be played, the generated chime (`Chime`, a C–E–G bell, looped with a static `AudioTrack`) rings instead: it's no longer a choice, but an alarm that rings beats one that doesn't.
- In the picker, each sound shows who made it ("by F1tz · CC0 · Freesound"), from the same `SoundCredit` as the credits page. Tapping a sound selects it and plays a preview at half volume on the alarm stream, so it follows the alarm volume. The preview stops on the next tap or when the picker closes.
- Emulator-checked: with Birdsong selected, the ring plays a looping `MediaPlayer` with `USAGE_ALARM`.
