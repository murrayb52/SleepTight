# SleepTight: working conventions

A personal Android sleep tracker. The spec is `docs/plan/2026-11-04-v1-build-plan.md`. Read the relevant section before building a feature. If a decision departs from it, add a dated note in the plan rather than diverging silently.

## Commands

```bash
./gradlew test             # run before EVERY commit; don't commit if it fails
./gradlew :metrics:test    # fast loop while working on metrics
./gradlew assembleDebug    # must also pass before committing changes to :app
```

## Where things are explained

- `docs/architecture.md`: modules, components, a night end to end, key design decisions. **Read it before structural changes and update it in the same commit when the structure changes** (including the per-component Status column).
- `docs/README.md`: index of how-it-works pages per feature.
- `README.md`: product-level overview for first-time visitors. Keep architecture there to a brief nod that links to `docs/architecture.md`.

## Testing on devices

- Verify behaviour on the **emulator** before asking the user to test anything on their phone. Use `./gradlew installDebug` / `connectedAndroidTest`, plus `adb` for screenshots (`adb exec-out screencap -p`), logs (`adb logcat`) and UI checks.
- The physical phone is for what an emulator can't show: overnight service survival, the real mic, the alarm, and Health Connect sync from Garmin.
- All builds sign with the shared debug key (see the README), so `adb install -r` upgrades in place. Never uninstall the app on the user's phone, because that wipes their recorded nights.

## Workflow

- Work through the plan's **build order**, with one or more commits per step. Tick the step off in the README's build order list when it's done.
- **Run the unit tests before each commit.** For `:metrics` changes, write the test first (synthetic nights) and then the code.
- **Document how it works as you build it.** Each feature gets (or updates) a page in `docs/` explaining how it actually behaves: inputs, formulas, thresholds, edge cases. Link it from `docs/README.md`. The plan in `docs/plan/` is the original intent, and `docs/` is the current truth. Keep the two apart.
- When a build step is done and its CI run passes, tag that commit `v0.<step>.0` (annotated) and push the tag. `v1.0.0` follows step 10. The app version comes from these tags.
- Commit messages: imperative mood, prefixed with the step, e.g. `Step 2: add efficiency component to score`.
- Don't add a dependency without putting it in `gradle/libs.versions.toml`. Don't hard-code versions in module build files.

## Architecture rules

- **`:metrics` is pure Kotlin.** No `android.*` or `androidx.*` imports, ever. It takes plain data (timestamps, segments, samples) and returns plain results, and the app maps Room entities to and from it. The module boundary enforces this, so don't add an Android dependency to make something compile.
- **All score weights and thresholds live in `ScoreConfig`.** All reference ranges live in one constants file. No magic numbers scattered through the code.
- **Scores are recomputable.** Health Connect data can arrive hours late, so metrics and score must be derivable from stored data at any time. `scoreJson` on `Night` is a cache, not the source of truth.
- **Taps define the night.** Trim every imported segment to `[bedTapAt, wakeTapAt]`.
- **Never write audio to disk.** `NightService` computes levels in memory and stores only dB values.
- **Extension points stay as interfaces:** `AlarmEvents` (no-op in v1, Hue later) and `GarminSource` (Health Connect in v1, unofficial API later).
- **Every permission has a denial path** with a clear explanation in the UI. Never fail silently.

## Time handling

- Store instants as epoch milliseconds (UTC) in Room. Do local-time logic (the 22:00 bedtime goal, the alarm window, "which night is this") in the device's time zone via `java.time`, injected as a `Clock`/`ZoneId` so tests can control it.
- A "night" belongs to the date of its bed tap, adjusted so that a 00:30 bedtime still counts as the previous evening's night.

## Style

- British English in UI strings and docs (e.g. "optimisation", "colour"). Code identifiers follow Android API spelling.
- Kotlin official code style (`.editorconfig`). Compose screens are stateless, take state and callbacks, and get their state from a ViewModel.
- All user-facing text goes in `strings.xml`.
