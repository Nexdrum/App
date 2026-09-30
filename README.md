# Spectra Android — Native build (Compose UI + C++/Oboe engine)

Fully native Android DAW. The interface is Jetpack Compose + Material3; all sound comes from
the C++ DSP core (`DspCore`) through Oboe. No WebView is used for the DAW itself.

## Architecture

- `ui/` — Compose screens: transport bar, three piano-roll editors (Nebular, Spectrachord, Nexdrum),
  mixer/FX panels, mix orb, instrument dialog.
- `daw/DawViewModel.kt` — song state, all edits, autosave (`files/spectradaw.json`), transport control,
  120ms native position poll, JSON import/export model.
- `daw/SeqSync.kt` — pushes the pattern to the native sequencer as primitive arrays on a worker thread.
- `DspCore` (`app/src/main/cpp/`) — voices, FX, and the sample-accurate sequencer. Transport timing is
  driven by Oboe audio frames, so interface load can never drag tempo or skip notes.
- `NativeAudio.kt` — JNI surface (audition voices, FX, sequencer transport).
- `AudioService.kt` — foreground media-playback service keeping the engine alive in background.
- `assets/instruments/*.html` — the standalone WebAudio instrument pages (Nebular, Spectrachord, Nexdrum),
  shown in a dialog WebView for live playing. They do not participate in sequencing.

## Song JSON

Same schema as the classic build (`bpm`, `ts`, `neb`/`spec`/`drums` with notes/hits/FX), so old
exports import unchanged. Saves live in the app files dir; Export/Import use the system file picker.

## Build an APK with GitHub Actions

1. Create a new GitHub repository (for example `Spectra-Android`).
2. Put the contents of this folder at the root of the repository.
3. Push to the `main` branch.
4. Open **Actions → Build Spectra APK**.
5. Run the workflow if it did not start automatically.
6. When it finishes, open the build and download the **Spectra-Android-debug** artifact.
7. Extract the artifact ZIP and install `app-debug.apk` on Android.

Android may ask you to allow installation from the browser/file manager you use to open the APK.

## Android Studio

Open this folder as the project. It targets:

- compile / target SDK 35
- min SDK 26
- NDK 27.2.12479018
- CMake 3.22.1
- Oboe 1.10.0
- AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.11.00

## Important source files

- `app/src/main/java/com/sonolume/spectra/MainActivity.kt` — Compose host + file export/import.
- `app/src/main/java/com/sonolume/spectra/daw/` — state model, ViewModel, sequencer sync, audio helpers.
- `app/src/main/java/com/sonolume/spectra/ui/` — transport, piano-roll editor, track screens.
- `app/src/main/cpp/DspCore.{h,cpp}` — Sonolume DSP core + native sequencer.
- `app/src/main/cpp/AudioEngine.{h,cpp}` — Oboe stream.
- `SPEC-NATIVE-UI.md` — native UI build spec and behavior parity notes.
- `.github/workflows/build-apk.yml` — automatic APK build.

## Limitations

**Native WAV recording is not wired yet.** The Rec button reports this. The C++ port preserves the
behavior and control structure of the original audio engine but is not bit-for-bit identical to
Web Audio's built-in BiquadFilter/Convolver implementations.
