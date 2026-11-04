# Screens and look

How the app is laid out and how each screen behaves. Code: `app/.../ui/` (`SleepTightApp.kt`, `home/`, `history/`, `profile/`, `components/`, `theme/`) and `app/.../alarm/AlarmActivity.kt`. The approved mockups are in the [redesign canvas](https://claude.ai/artifact/6XdQ4FWBxVReKzJEH77tet).

## Look

- **Palette** (`Palette` in `ui/theme/Theme.kt`): deep-ink sky `#0A0F24` before bed, near-black blue `#04061A` in bed, moonlight `#9FB4FF` for information, and ember amber `#F4A653`, kept for the one main action on each screen. Day colours are used only for the morning summary.
- **Type**: Outfit, a variable font bundled in `res/font/` under the SIL Open Font License (see [`ASSETS.md`](../ASSETS.md)), with thin numerals for clocks.
- **Stars** (`StarrySky`): a fixed random field that twinkles slowly, each star at its own pace (a 9-second cycle, 45–100 % of its brightness).
- **Score dials** (`ScoreDial`): the arc *is* the score, starting at 12 o'clock. 100 is a full circle, 0 none, and anything between is proportional. The colour bands are 80+ green, 60–79 amber, below 60 coral. Without a score yet (no sleep data until step 5) a dial shows an empty track and "–".
- The app is always dark, with light status-bar icons.

## Navigation

A bottom bar with **Sleep · History · Profile**. It's **hidden while you're in bed** (nothing to fiddle with at night) and on a night's page.

## Sleep tab, before bed

- Date and greeting, a crescent moon, and the bedtime goal with the reminder time.
- Setup warnings, only when needed: battery restrictions (with Samsung advice), exact alarms, alarm volume at 0, full-screen alarm permission, notifications.
- **Wake me by**: a wheel in 5-minute steps that wraps round midnight. It saves only after you scroll it and it settles, never just for being shown, and a stored time off the 5-minute grid is shown at the nearest step. The window keeps its length and ends at the chosen time.
- **Window chip** (15/20/30/45/60 min) and **Alarm on/off chip**. With the alarm off, the wheel is replaced by "No alarm".
- **Going to bed** (amber), then **Last night** with its dial, which opens that night.

## Bedside check

A sheet at the bed tap: plugged in, on the nightstand next to you, and the mic (bottom edge) pointing towards you. **Don't show this again** turns it off, and it can be turned back on in Profile. **Start the night** carries on with the bed tap and its permission requests.

## Tonight (Sleep tab, in bed)

Near-black sky with twinkling stars and no nav bar. From the top:
- the listening pill
- a big clock
- in bed since / how long
- the alarm row (tap to turn tonight's alarm off or on; while ringing or snoozed, **Stop alarm** and **Snooze**)
- the noise panel: a strip from the bed tap to now with a tick per event, and the latest events
- a gentle wave whose height follows the live level

**I'm up** must be **pressed and held for 1.2 s** (it fills as you hold), so a bump in the night can't end the night. Screen readers get a normal click.

## Alarm screen

Shown over the lock screen when the alarm rings.

- **Sunrise**: the sky starts as night and very slowly brightens through violet and ember, with the sun rising from below the bottom edge. It takes the full 10 minutes an alarm can ring to reach dawn. Full day only comes with Stop.
- **Stop alarm** is the amber button. It only silences the alarm; **I'm up stays a separate tap** (the user's choice, 2026-11-04). The sky sweeps to full day in 1.2 s and fades into the **morning summary**: "Good morning", the night so far (dial, time in bed, noise events, snoozes, bed → now), with an amber **I'm up** and **Later**. After I'm up, it shows the finished night with **See the night** and **Done**.
- **Swipe up anywhere to snooze.** The screen follows your finger, and a swipe of 140 dp or more snoozes. Snooze lengths are never shown (they're baked in; see [alarm.md](alarm.md)). Five marks show snoozes used. After 5 snoozes the swipe does nothing and the screen says "No snoozes left. Time to get up." Screen readers get a Snooze action.

## History

7/30-day toggle; average score, average time in bed and average bedtime. Bedtimes are averaged relative to the bedtime goal, so nights either side of midnight average correctly. A strip of the last 7 dates with dials, and a list of nights (newest first) with times, noise events and snoozes. Tapping a night opens its page.

## One night

Dates and times, the score dial with time in bed, and the alarm time and snoozes. Then:
- **How the score adds up**: all five parts with their full points ("– / 35" until the night has a score)
- sleep stages, once there's sleep data
- a noise timeline with the loudest events

**Info icons (ⓘ)** sit next to the score and each part (48 dp touch target, read out as "About Efficiency" and so on). Each opens a short explanation of what's measured and how points are earned. The numbers in it come from `ScoreConfig` and the user's own goals (`ScoreExplainers.kt`), so the text can't drift from the maths.

## Profile

All-time stats (nights, % in bed by the bedtime goal, snoozes a night). Settings:
- **Goals**: bedtime (clock picker), sleep (6–10 h in half hours), snoozes (0–3)
- **Alarm**: wake window, sound (a picker with previews; see [alarm.md](alarm.md#sounds))
- **Reminders**: bedtime reminder and bedside check switches
- **Data**: Garmin and export, both coming soon
- **About**: **Credits and licences**, a page crediting each alarm sound (original title, author, CC0, a link to Freesound) and the Outfit typeface (with its full SIL Open Font License text), and noting that the chime and the bedside illustration were made for the app
