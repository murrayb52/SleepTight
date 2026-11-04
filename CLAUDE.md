# SleepTight: working conventions

A personal Android sleep tracker. The spec is `docs/2026-11-04-v1-build-plan.md`. Read the relevant section before building a feature. If a decision departs from it, add a dated note in the plan rather than diverging silently.

## Commands

```bash
./gradlew test             # run before EVERY commit; don't commit if it fails
./gradlew :metrics:test    # fast loop while working on metrics
./gradlew assembleDebug    # must also pass before committing changes to :app
```

## Workflow

- Work through the plan's **build order**, with one or more commits per step. Tick the step off in the README's build order list when it's done.
- **Run the unit tests before each commit.** For `:metrics` changes, write the test first (synthetic nights) and then the code.
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
