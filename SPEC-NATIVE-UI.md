# Spectra Native UI — build spec

Full rewrite of the DAW interface from WebView HTML to native Android (Jetpack Compose + Material3).
Audio engine untouched: `DspCore` + Oboe + `NativeAudio` JNI, including the native sequencer
(`seqConfigure/seqSetNeb/seqSetSpec/seqSetDrums/seqPlay/seqStop/seqStep/seqIsPlaying`).

## Source of truth
`reference/audio-web-original.js` (legacy) and the removed `app/src/main/assets/index.html`
(WebView DAW: transport `performance.now()+setInterval`, per-note postMessage bridge) defined the behavior.
JSON song schema is unchanged so old exports still import.

## Module map (package com.sonolume.spectra)
- `daw/Music.kt` — constants (NEB_LO 29..HI 74, SPEC_LO 26..HI 98, KICKS 40-46, SNARES 47-53,
  CYMS 54-60, pitch-class colors, note names), midiFreq, drumKind, drumZoneIdx, velCycle, transport math.
- `daw/DawState.kt` — immutable data model + tolerant JSON parse (legacy ts.bottom 2/8/16 fold) + demo song.
- `daw/DawViewModel.kt` — StateFlow UI state, all mutations, debounced autosave to `files/spectradaw.json`,
  transport control, 120ms position poll, orb flashes, status line, tap tempo.
- `daw/SeqSync.kt` — builds JNI primitive arrays from state, pushes config+pattern on a background thread.
- `daw/Audio.kt` — ensureEngine (foreground service + NativeAudio.start), audition helpers, FX appliers.
- `ui/Theme.kt` — dark scheme matching #08090d.
- `ui/Transport.kt` — title, play/stop/loop/BPM/tap/TS, menu, view tabs, orb, song buttons, instruments row.
- `ui/PianoRoll.kt` — canvas grid editor with single-finger edit gestures and two-finger scroll.
- `ui/NebScreen.kt` — header, FX panel, roll, bend/vel line overlay, selbar.
- `ui/SpecScreen.kt` — header, osc selects, FX, dual-half roll, selbar.
- `ui/DrumScreen.kt` — header, verb FX, 21-row kit grid, selbar.
- `ui/Instruments.kt` — WebView dialog hosting `assets/instruments/*.html` (standalone WebAudio pages).
- `MainActivity.kt` — ComponentActivity, SAF export/import, setContent.

## Behavior parity notes
- Grids: tap empty = add len-1 (audition), drag right on new = longer, tap note = select,
  drag body = move, drag onto tail = extend, double-tap = delete, long-press = vel cycle.
- Spec: top/bottom half = osc1/osc2; drag half vertically retunes, drag end extends, body drag moves both.
- Drums: tap toggles hit/select (tap selected again = remove), double-tap = delete, long-press = vel cycle.
- Neb line: locked until Edit on; tap line adds point, drag moves, double-tap point removes, clear wipes.
- Mute/solo audible rule, lastVel/lastPluck memory, per-note select bars with fine sliders.
- Steps shown = contentSteps+16; loop wraps at contentSteps; non-loop end auto-stops UI via poll.

## Build
Same `.github/workflows/build-apk.yml`. Compose via BOM 2024.11.00 + Kotlin compose plugin 2.0.21.
New deps: compose-bom, ui, foundation, material3, activity-compose, lifecycle viewmodel/runtime compose.
