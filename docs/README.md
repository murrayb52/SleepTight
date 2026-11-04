# SleepTight documentation

How the app works, written as each feature is built. These pages describe what the code actually does. Where that differs from the original plan, the pages here are correct.

Start with [`architecture.md`](architecture.md) for the overall structure, and [`Roadmap.md`](Roadmap.md) for what exists, what's agreed next, and future ideas.

## How it works

Pages are added as each build step lands:

| Topic | Page | Build step |
|---|---|---|
| Nights, bed and wake taps, data model | [`nights.md`](nights.md) | 1 |
| Night metrics, sleep score, regularity | [`metrics.md`](metrics.md) | 2 |
| Screens, navigation, look (redesign) | [`ui.md`](ui.md) | after 4 |
| Overnight sound monitoring and noise events | [`sound-monitoring.md`](sound-monitoring.md) | 3 |
| Smart alarm, snoozes, `AlarmEvents` hooks | [`alarm.md`](alarm.md) | 4 |
| Garmin import via Health Connect, fallback estimation | _not yet written_ | 5 |
| Caffeine log and caffeine at bedtime | _not yet written_ | 7 |
| Trends and sleep debt | _not yet written_ | 8 |
| JSON export format | _not yet written_ | 9 |

## Plans

Dated build plans live in [`plan/`](plan/). They record intent at the time of writing and aren't kept in sync with the code.

- [`2026-11-04-v1-build-plan.md`](plan/2026-11-04-v1-build-plan.md): the v1 spec and build order
