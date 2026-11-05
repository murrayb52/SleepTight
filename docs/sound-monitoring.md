# Overnight sound monitoring and noise events

How SleepTight listens overnight. The maths is in `:metrics` (`Sound.kt`), the pipeline in `app/.../tracking/NightRecorder.kt`, and the Android service in `app/.../tracking/NightService.kt`.

**No audio is ever stored.** Each second of microphone input lives in a 32 KB in-memory buffer just long enough to compute one number, its level, and is then overwritten. Only levels and noise events reach the database.

## Lifecycle

- **Going to bed** opens the night and, if the microphone is allowed, starts `NightService`, a foreground service of type `microphone` with a "Tracking tonight" notification.
- **I'm up** (or closing an overdue night) stops the service. It writes any batched samples before it goes.
- **Opening the app** while a night is open restarts monitoring if it isn't running: after a reboot, after Android killed the app, or after a background restart was refused.
- If Android kills the process, the service is sticky, so Android restarts it in the background. If Android refuses that restart (it can, for microphone services), the app shows a "Sound tracking stopped" notification and the Tonight view offers **Resume**. Opening the app resumes it anyway.
- A partial wake lock keeps the CPU running with the screen off, released when the service stops, with a 20-hour safety timeout. On the emulator, 120 samples arrived in 120 seconds with the screen off and Doze forced on.

## From microphone to level

- `AudioRecord` at **16 kHz mono, 16-bit**, using the `VOICE_RECOGNITION` source (falling back to `MIC`). That source normally skips automatic gain control, which would otherwise flatten the levels.
- Once a second, the RMS of that second's 16,000 samples becomes **dBFS** (0 = the loudest the mic can report). Digital silence is floored at −120 dBFS.
- **Baseline**: the median dBFS of the previous **20 minutes** (1,200 seconds), excluding the current second so a loud second can't raise its own baseline. Before 20 minutes have passed, it uses whatever it has; the very first second is its own baseline.
- **Relative level** = dBFS − baseline. This is what's stored (`sound_samples.dbRelative`), because phone mics aren't calibrated and only the change from your room's normal level means anything.

## Noise events

- A second more than **15 dB** above baseline is loud. An event needs at least **1** loud second (`minNoiseSeconds`).
- Each sample covers one second, so an event runs from its first loud second to the end of its last.
- Events less than **30 s** apart (end to start) **merge** into one, keeping the higher peak.
- An event is written the moment it starts and updated as it grows or merges, so the Tonight view shows it at once.
- `NoiseEvents` is an extension point that hears every event as it starts or grows. v1 doesn't use it; the planned opt-in loud-moment clips will.

For scoring, events are later grouped into **clusters** (within 5 min); see [metrics.md](metrics.md).

## Activity

`activityPerMinute` counts, per minute, the seconds more than **12 dB** above baseline (8 dB until 2026-11-05: on the first real night 5 % of all seconds cleared 8 dB while Garmin had the user soundly asleep). It's a rough movement proxy for the smart alarm (step 4) and the sleep estimate without Garmin (step 5). It's worked out from the stored samples rather than stored separately.

All of these thresholds live in `SoundConfig`.

## What each stored second holds

Since database version 5 (2026-11-05), each row of `sound_samples` keeps the second as measured as well as the relative level, for the sound-model investigation (see the [Roadmap](Roadmap.md)). Older nights have nulls in the new columns. Everything is in dBFS (relative to the loudest the mic can report), **not calibrated dB SPL**: comparing with WHO's 40 dB needs a one-off calibration of the phone against a sound meter.

| Column | What it is |
|---|---|
| `dbRelative` | the level minus the 20-minute median baseline (drives noise events, activity and the smart alarm) |
| `dbfs` | the RMS level of the whole second |
| `peakDbfs` | the loudest single sample: a sharp knock shows here even when the average is low |
| `lowDbfs` | level below **300 Hz**: thumps, movement against the bed, snoring's rumble |
| `midDbfs` | **300–2,000 Hz**: most of the voice range (talking, coughing) |
| `highDbfs` | above **2,000 Hz**: rustling bedding, hiss |

The bands are second-order Butterworth filters (`Biquad` in `Sound.kt`) run over the audio in memory, so still **no audio is stored**. The band edges are `SoundConfig.lowBandMaxHz` and `highBandMinHz`. Unit tests check that a 100 Hz, 800 Hz and 5 kHz tone each land in their own band.

## Screen events

While a night is tracked, `NightService` also logs the screen turning **on**, **off** and being **unlocked** (`screen_events`: time and type only). Picking the phone up is a strong sign of being awake, so these are labelled "awake" moments for the analysis. They aren't used in the score.

## Storage

Samples are batched and written every **30 seconds**. A kill loses at most the last 30 seconds. Measured on the emulator before version 5, an 8-hour night was about **1.3 MB** (28,800 rows plus their index); the five measured columns add roughly 0.6 MB a night. That's acceptable for now. If it becomes a problem, older nights can be thinned to per-minute summaries without touching the scores, which only use noise events and activity.

## Tonight view

While a night is open, Home becomes the Tonight view, kept on one screen so there's nothing to navigate at night:

- In bed since, time so far, and **I'm up**.
- **Sound**: the live level ("+4 dB vs your room") with a bar that's full at +30 dB, and a reminder that only levels are kept.
- **Noise events**: the count, and the latest 10 with time, length and peak.

## Permissions and what happens when they're missing

| Situation | What you see |
|---|---|
| Microphone denied at the bed tap | The night still opens (taps define it). A red card says sound monitoring is off and why, with **Allow microphone**. After a second denial Android won't ask again, so the button opens the app's settings instead |
| Mic allowed later in Settings | Monitoring starts as soon as you return to the app |
| Notifications denied | Tracking still works. A note says there's no tracking notification |
| Mic hearing pure digital silence for 10 s | A warning to check the quick-settings microphone toggle and other apps using the mic |
| Mic unavailable or failing mid-night | "Sound monitoring stopped" with **Try again**, plus a notification |
| Android refused to restart the service | "Sound monitoring stopped" with **Resume**, plus a notification. Opening the app resumes it |
| Battery optimisation on | A card before bed asks to let SleepTight run unrestricted. On Samsung it also explains Sleeping and Deep sleeping apps and links to the app's settings. It disappears once allowed |

## Not yet checked on a real phone

The emulator's microphone can't take test audio on this laptop. Its PulseAudio client fails to connect to PipeWire. So real levels, the baseline and noise detection on a real mic, and whether Samsung leaves the service alone all night, are for the first overnight test on the phone.
