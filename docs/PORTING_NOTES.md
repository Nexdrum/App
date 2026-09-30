# Native port map

The supplied Web Audio engine is retained in `reference/audio-web-original.js` as the sound-design reference.

| Web engine behavior | Native Android location |
| --- | --- |
| `nebNoteAt(...)` | `DspCore::nebNote` / `NebVoice::sample` |
| `specOscAt(...)` | `DspCore::specNote` / `SpecVoice::sample` |
| `drumKickAt(...)` | `DrumVoice::sample(kind=0)` |
| `drumSnareAt(...)` | `DrumVoice::sample(kind=1)` |
| `drumCymbalAt(...)` | `DrumVoice::sample(kind=2)` |
| `nebMuteMix(...)` | `DspCore::nebMuteMix` |
| `drumZone(...)` | kept exactly in the JS native shim; the resolved strike position is sent to C++ |
| `applyNebFX(...)` | `DspCore::setNebFx` + native track processing |
| `applySpecFX(...)` | `DspCore::setSpecFx` + native track processing |
| `applyDrumFX(...)` | `DspCore::setDrumFx` + native track processing |
| WebAudio master graph | one Oboe stereo callback in `AudioEngine` |

## Deliberate architecture changes

The Web Audio engine creates many nodes per note and uses browser BiquadFilter/Convolver nodes. The native engine instead uses persistent voice structs, fixed voice pools, native biquads, algorithmic reverbs, and reusable delay lines. This is why it is not bit-for-bit identical yet, but it removes the browser graph/node-allocation bottleneck that motivated the Android port.

The original control behavior — velocity ranges, continuous drum strike positions, Nebular palm blend, automation lines, note ranges and DAW state — remains in the UI/native bridge.
