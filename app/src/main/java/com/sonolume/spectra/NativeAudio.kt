package com.sonolume.spectra

object NativeAudio {
    init { System.loadLibrary("spectra_native") }

    external fun start(): Boolean
    external fun stop()
    external fun isRunning(): Boolean
    external fun sampleRate(): Int
    external fun xRunCount(): Int

    external fun setMaster(value: Float)
    external fun setTrack(track: Int, value: Float)
    external fun setGlobalReverb(value: Float)
    external fun setGlobalDelayMix(value: Float)
    external fun setGlobalDelayTime(value: Float)

    external fun setNebFx(
        delayOn: Boolean, delayAmt: Float, delayFb: Float, delayTime: Float,
        distOn: Boolean, distAmt: Float, distIn: Float, distOut: Float,
        verbOn: Boolean, verbAmt: Float, verbDecay: Float
    )

    external fun setSpecFx(
        delayOn: Boolean, delayAmt: Float, delayFb: Float, delayTime: Float,
        verbOn: Boolean, verbAmt: Float, verbRoom: Float
    )

    external fun setDrumFx(verbOn: Boolean, verbAmt: Float)

    external fun nebNote(
        midi: Int, velocity: Float, durationSec: Float, delaySec: Float,
        pluck: Boolean, decayMs: Float,
        bendT: FloatArray, bendV: FloatArray,
        vexT: FloatArray, vexV: FloatArray
    )

    external fun specNote(
        midi: Int, velocity: Float, durationSec: Float, delaySec: Float, wave: Int
    )

    external fun drumHit(
        kind: Int, freq: Float, velocity: Float, position: Float, delaySec: Float
    )

    external fun click(high: Boolean, delaySec: Float)

    external fun nebLiveStart(id: Int, midi: Int, level: Float, pressure: Float, muteMix: Float, cents: Float, volume: Float, pluck: Boolean, releaseMs: Float)
    external fun nebLiveUpdate(id: Int, midi: Int, level: Float, pressure: Float, muteMix: Float, cents: Float, volume: Float)
    external fun nebLiveStop(id: Int, releaseMs: Float)
    external fun specLiveStart(id: Int, midi: Int, gain: Float, wave: Int)
    external fun specLiveStop(id: Int)
    external fun livePanic()

    external fun seqConfigure(bpm: Float, top: Int, bottom: Int, loop: Boolean, metro: Boolean)
    external fun seqSetAudible(neb: Boolean, spec: Boolean, drums: Boolean)
    external fun seqSetNebDecay(decayMs: Float)
    external fun seqSetSpecWaves(w1: Int, w2: Int)
    external fun seqSetNeb(s: IntArray, midi: IntArray, len: IntArray, vel: FloatArray, pluck: BooleanArray, bendN: IntArray, bendT: FloatArray, bendV: FloatArray, vexN: IntArray, vexT: FloatArray, vexV: FloatArray)
    external fun seqSetSpec(s: IntArray, midi1: IntArray, midi2: IntArray, len: IntArray, vel: FloatArray)
    external fun seqSetDrums(s: IntArray, midi: IntArray, vel: FloatArray)
    external fun seqPlay()
    external fun seqStop()
    external fun seqIsPlaying(): Boolean
    external fun seqStep(): Int
}
