# Sleep diary for the sound-model investigation: design

Date: 2026-11-05. Status: phase 1 being built for use the same evening (the user asked for it to be ready in 2–3 hours, so the calls marked **(Claude's call)** were made without a review round and can be changed later).

## Why

The sound-only sleep estimate disagreed badly with Garmin on the first night. The plan is to collect 1–4 weeks of nights and build a better model from data. Sound and Garmin alone can't say *why* a night went the way it did, or whether a noisy minute was you awake or the room. The user also wants SleepTight to track sleep-hygiene behaviour against the sleep metrics: caffeine, reading before bed, time in bed before trying to sleep, the last long screen session, and so on.

So each night gets a short diary, written at three moments, each taking seconds:

1. **At the bed tap**: earplugs and context tags.
2. **During the night**: when you wake the screen, a one-tap reason and an optional note.
3. **After I'm up**: a short "How did you sleep?" survey.

Plus a **caffeine log** during the day (build step 7 of the v1 plan).

## Goals

- Labels for the model: the user's own account of wakings (with times and reasons), how rested they feel, and whether earplugs were in.
- Sleep-hygiene inputs stored per night, for comparing against the score later.
- Every prompt is fast, dark, and skippable. Nothing nags.
- All data stays in the Room database, retrievable over `adb` until JSON export (step 9).

## Non-goals (for now)

- Analysis or charts of the diary against sleep (that comes with the data).
- Editing diary entries after the fact (the night's page shows them; editing comes later).
- Editing the caffeine defaults in Profile (the values already sit in `Settings`).
- Windows or fan tags (the user ruled them out).
- The laptop recording rig, the second phone, radar or video (later phases).

## Anti-goals

- Anything that wakes the user more: no sound or vibration from the night prompt, no bright screens.
- Prompts that block getting back to sleep: the wake note must be dismissible with one tap.
- Changing the audio path (same phone, `VOICE_RECOGNITION`, 16 kHz) while collecting.

## Phases of the investigation

- **Phase 1 (from tonight, about a week):** normal routine with earplugs, the S21 on the nightstand as on the first night, the Garmin worn, and the diary below. Treat the first 2–3 nights as settling-in.
- **Phase 2:** 5 consecutive nights without earplugs, then at least 5 back with them. The return to earplugs is the control: if sleep recovers, the earplugs explain the change.
- **Later:** the laptop with the Brio and the laptop microphone recording the whole night, empty-room nights on the A17, and the sleep-video study.

## 1. At the bed tap: "Tonight"

The bed tap always shows a sheet before the night starts (previously only while the bedside check was on).

- **Earplugs in** (a switch). It defaults to the Profile setting "Earplugs by default" (on by default).
- **Context tags** (each a toggle chip): Alcohol, Partner in the room, Different bed, Reading before bed.
- **Last 30+ minutes of screens ended**: one choice of Just now, Under 1 h ago, 1–2 h ago, 2 h+ ago. This is optional. **(Claude's call:** buckets rather than a time picker, for speed.)
- The bedside device checks (charger, nightstand, mic) still appear above, unless "Don't show again" was ticked. Their illustration is dropped to make room. **(Claude's call.)**
- **Start the night** saves the context and carries on with the bed tap as before. Dismissing the sheet cancels the bed tap, as now.

Stored in a new `night_context` row per night.

## 2. During the night: wake note

- **Trigger:** the screen turns on while a night is being tracked, at least 15 minutes after the bed tap, while the alarm isn't ringing or snoozed, and at most once every 10 minutes. **(Claude's call** on the 15 and 10 minutes.)
- **How it appears:** two ways, so it's there whatever is on screen.
  - A silent notification ("Awake? Tap to note why"), with no sound and no vibration. Tapping it opens the app.
  - When the app opens in bed within 15 minutes of that screen-on, a bottom sheet over the in-bed screen.
- **The sheet:** reason chips (several can be picked): Toilet, Water, Noise, Too hot, Too cold, Mind racing, Earplugs, Checking the time, Other. It also has an optional one-line note, a **Save** button and a **Not now** button.
- The note is timed at the screen-on that triggered it, not at Save.
- The in-bed screen also gets a small **Note a waking** link, for logging at any time.
- **Stored:** a `wake_notes` row with the night, the screen-on time, the save time, the reasons and the note. Screen on, off and unlock events are already logged separately.

## 3. After I'm up: "How did you sleep?"

- Appears as a sheet when the app is next open after a night ends, within 12 hours of I'm up, until it's answered or skipped. On the alarm path, both **See the night** and **Done** on the morning summary open the app, so the survey follows. **(Claude's call:** Done previously only closed the alarm screen.)
- **Questions:**
  - **How rested do you feel?** 1–10 slider.
  - **Sleep quality**: 1–10 slider.
  - **How many times do you think you woke?** A stepper from 0.
  - **How long to fall asleep?** A slider of 0–90 minutes in steps of 5.
  - **How long in bed before trying to sleep?** A slider of 0–90 minutes in steps of 5. This is the user's "how long were you in bed before trying to go to sleep". It's asked here because at the bed tap you don't know yet. **(Claude's call.)**
  - **Earplug adjustments**: a stepper from 0, shown only on earplug nights.
  - **Notes**: free text.
- **Save** stores a `morning_surveys` row. **Later** hides it for this run of the app, and the night's page offers **How did you sleep?** to answer it any time.

## 4. Caffeine log (build step 7)

This follows the v1 plan's spec 8, using the `CaffeineEntry` table and the `Settings` defaults that already exist.

- **On the Sleep tab:** a compact line under the bedtime goal, "Caffeine by bedtime ≈ N mg · Add". It's projected to the bedtime goal, or to now if that time has passed.
- **The Add sheet:** pick a drink (Coffee, Decaf, Coke, Coke Zero). For coffee, also pick Home or Restaurant and Single or Double. For Coke, pick 250 ml, 500 ml or 1 L. Then pick when: Now, or 1, 2, 3, 4, 6 or 8 hours ago **(Claude's call)**. The mg comes from the Settings defaults when it's logged. Today's drinks are listed with a delete button.
- **Caffeine at bedtime** = Σ mg × 0.5^(hours before the bed tap / half-life). It covers drinks in the 24 hours before the bed tap, with a 5 h half-life from `Settings`. This is pure Kotlin in `:metrics`, tested first.
- **On the night's page:** caffeine at bedtime and the drinks behind it.

## The night's page

A new **Diary** section on the night's page shows:
- the bed-tap context: earplugs and tags;
- the wake notes, with their times and reasons;
- the survey answers, or a **How did you sleep?** button if there are none;
- caffeine at bedtime.

## Data (database version 6, automatic migration from 5)

- `Settings.earplugsDefault` (Boolean, default true).
- `night_context`: `nightId` (primary key, deleted with the night), `earplugs`, `alcohol`, `partner`, `differentBed`, `readingBeforeBed` (Booleans), `lastScreens` (nullable text: `JUST_NOW`, `UNDER_1H`, `H1_TO_2`, `OVER_2H`).
- `wake_notes`: `id`, `nightId` (deleted with the night), `at` (the screen-on time), `notedAt`, `reasons` (comma-separated reason names), `note` (nullable).
- `morning_surveys`: `nightId` (primary key, deleted with the night), `answeredAt`, `rested`, `quality`, `wakings`, `minutesToFallAsleep`, `minutesBeforeTrying`, `earplugAdjustments` (nullable), `notes` (nullable).
- Caffeine needs no change: `caffeine_entries` already exists.

## Left to the builder

- The visual layout within the existing night-sky style. The Sleep tab before bed must still fit without scrolling at 480 dpi.
- Notification channel naming, and which existing tests need adjusting.
