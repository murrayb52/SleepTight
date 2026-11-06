# Sleep diary and caffeine

The diary records the context that sound and Garmin can't: earplugs, sleep-hygiene habits, why you woke, how rested you feel, and caffeine. It exists to give labels and inputs to the sound-model investigation (see the [Roadmap](Roadmap.md)) and to compare habits against the score later. Design: [`superpowers/specs/2026-11-05-sleep-diary-design.md`](superpowers/specs/2026-11-05-sleep-diary-design.md).

Everything is stored in Room (database version 6 and later) and is deleted with its night. Until JSON export exists, it's read off the phone with `adb` (`run-as … sqlite3` or by pulling the database).

## At the bed tap: "Tonight"

**Going to bed** always opens a sheet now (`BedsideCheckSheet`):

- **Bedside checks:** Plugged in; On the nightstand, not under the pillow; Bottom edge (the mic) towards you. They're single lines, with **Don't show this again**, and are hidden once that's ticked or the Profile switch is off. The sheet used to be skipped entirely when the checks were off.
- **Tonight:**
  - **Earplugs in** (a switch). It starts at Profile → **Earplugs in by default** (`Settings.earplugsDefault`, on by default).
  - Toggle chips: **Alcohol**, **Partner in the room**, **Different bed** and **Reading before bed**.
  - **Last 30+ min of screens ended**: Just now, Under 1 h ago, 1–2 h ago or 2 h+ ago. This is optional; tap the chosen one again to clear it.
- **Start the night** opens the night, then stores a `night_context` row: `earplugs`, `alcohol`, `partner`, `differentBed`, `readingBeforeBed` and `lastScreens` (`JUST_NOW`, `UNDER_1H`, `H1_TO_2`, `OVER_2H` or null). Swiping the sheet away cancels the bed tap.

## In the night: wake notes

`NightService` already logs screen on, off and unlock (`screen_events`). On a screen-on it also calls `AppContainer.onScreenOnInNight`, which prompts when all of these hold (`WakePromptRule`):

- the night is open, and at least **15 minutes** have passed since the bed tap;
- the alarm is idle (not ringing or snoozed);
- the last prompt was at least **10 minutes** ago.

The prompt then appears in two ways:
- **A notification:** "Awake? Tap to note why", on the silent *Night notes* channel (no sound, no vibration, visible on the lock screen). It times out after 15 minutes, and tapping it opens the app.
- **A sheet in the app:** shown while the app is open in bed, within 15 minutes of that screen-on (`DiaryPrompts`).

The sheet ("Awake at 02:14. Why?") offers these reasons, and several can be picked: Toilet, Water, Noise, Too hot, Too cold, Mind racing, Earplugs, Checking the time, Other. There's also an optional one-line note. **Save** needs a reason or a note. **Not now** or swiping away closes it without saving.

**Note a waking**, under I'm up on the in-bed screen, opens the same sheet timed at the tap.

Stored in `wake_notes`:
- `at`: the screen-on time;
- `notedAt`: when you saved it;
- `reasons`: comma-separated `WakeReason` names, in a fixed order;
- `note`: the optional text.

## In the morning: "How did you sleep?"

A sheet over the app appears when all of these hold:
- the latest closed night ended under **12 hours** ago;
- no night is open;
- the night has no survey yet;
- it hasn't been put off with **Later** since the app started (`surveyNightFor`).

On the alarm path, **See the night** and **Done** both open the app, so it follows straight after I'm up. The night's page has **How did you sleep?** to answer it at any time.

| Question | Input | Stored as |
|---|---|---|
| How rested do you feel? | slider 1–10 (starts at 5) | `rested` |
| Sleep quality | slider 1–10 (starts at 5) | `quality` |
| Times you think you woke | stepper from 0 | `wakings` |
| Time to fall asleep | slider 0–90 min in 5s (90 means 90+) | `minutesToFallAsleep` |
| In bed before trying to sleep | slider 0–90 min in 5s | `minutesBeforeTrying` |
| Earplug adjustments | stepper, shown only on earplug nights | `earplugAdjustments` (null otherwise) |
| Notes (optional) | text | `notes` |

One `morning_surveys` row per night. Saving again replaces it.

## Caffeine (build step 7)

- **The Sleep tab** shows "Caffeine by bedtime ≈ N mg" in the bedtime-goal panel, or "No caffeine logged today". **+ Add** opens the caffeine sheet.
- **Logging a drink:** choose Coffee, Decaf, Coke or Coke Zero. For coffee, also choose Home or Restaurant and Single or Double. For Coke, choose 250 ml, 500 ml or 1 L. Then choose when: Now, or 1, 2, 3, 4, 6 or 8 h ago.
- **The caffeine in each drink** is worked out when it's logged, from `Settings`:

  | Drink | Caffeine |
  |---|---|
  | Espresso shot at home | 63 mg |
  | Espresso shot at a restaurant | 75 mg |
  | Decaf shot | 3 mg |
  | Coke and Coke Zero | 10 mg per 100 ml |

  Changing these values in the app comes later.
- **The sheet** also lists the last 24 hours' drinks, each with a delete button.
- **Caffeine at a moment** (`caffeineAt` in `:metrics`) = Σ mg × 0.5^(hours before / half-life):
  - the half-life is `Settings.caffeineHalfLifeHours`, 5 h by default;
  - it counts drinks in the 24 hours before that moment (`CAFFEINE_LOOKBACK`), and none after it.
- **The Sleep tab projects** caffeine to the next bedtime goal. Within 6 hours after the bedtime goal (you're up late), it uses now instead (`caffeineTarget`).
- **The night's page** shows "Caffeine at bedtime ≈ N mg" at the bed tap, and the drinks behind it.

## On the night's page

A **Diary** panel near the end of the night's page lists:
- the bedtime context: earplugs, tags and screens;
- caffeine at bedtime, with its drinks;
- the wake notes, with their times and reasons;
- the survey answers, or the **How did you sleep?** button.
