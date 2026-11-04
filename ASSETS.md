# Bundled assets

Every third-party file shipped in the app, with its source and licence.

| File | What | Source | Licence |
|---|---|---|---|
| `app/src/main/res/font/outfit.ttf` | Outfit variable font (all UI text) | [google/fonts `ofl/outfit`](https://github.com/google/fonts/tree/main/ofl/outfit), by the Outfit Project Authors | SIL Open Font License 1.1, full text in [`licences/Outfit-OFL.txt`](licences/Outfit-OFL.txt) |
| `app/src/main/res/raw/alarm_birdsong.ogg` | "Birdsong" alarm | [bird song](https://freesound.org/people/F1tz/sounds/795995/) by F1tz | CC0 1.0 |
| `app/src/main/res/raw/alarm_epiano.ogg` | "Morning piano" alarm | [morning- epiano riff 1](https://freesound.org/people/f-r-a-g-i-l-e/sounds/476922/) by f-r-a-g-i-l-e | CC0 1.0 |
| `app/src/main/res/raw/alarm_kalimba_drops.ogg` | "Kalimba drops" alarm | [Brilliant Mesmerizing Kalimba Drops in a Airy Athmosphere Soundscape](https://freesound.org/people/bassimat/sounds/854316/) by bassimat | CC0 1.0 |
| `app/src/main/res/raw/alarm_kalimba.ogg` | "Kalimba" alarm | [kalimba - simple effect](https://freesound.org/people/Tetoszka/sounds/542318/) by Tetoszka | CC0 1.0 |
| `app/src/main/res/raw/alarm_music_box.ogg` | "Music box" alarm | [Wien_bleibt_Wien.WAV](https://freesound.org/people/thulitt/sounds/150025/) by thulitt (an 1880s march, out of copyright) | CC0 1.0 |

The alarm chime is generated in code (`alarm/Chime.kt`) and the bedside illustration (`res/drawable/ic_bedside.xml`) was drawn for this app; neither is third-party.

## How the alarm sounds were prepared

Downloaded on 2026-11-04 as Freesound's high-quality OGG previews through the API, after checking each licence is CC0. They were chosen to start gently (sounds that start loud and fade were left out, since the alarm ramps up). Each was converted with ffmpeg to mono 44.1 kHz, had leading silence trimmed, was loudness-normalised (target −16 LUFS, true peak −1.5 dB; peakier sounds land nearer −20 LUFS), and got a 30 ms fade-in and a 1 s fade-out so the loop doesn't click. Encoded as Ogg Vorbis (q4); 1.4 MB in total.

CC0 needs no attribution, but the sources are listed above so they can be traced.
