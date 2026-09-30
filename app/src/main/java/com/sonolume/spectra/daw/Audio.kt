package com.sonolume.spectra.daw

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.sonolume.spectra.AudioService
import com.sonolume.spectra.NativeAudio

object Audio {
    private var serviceStarted = false

    fun ensure(context: Context) {
        try {
            if (!serviceStarted) {
                serviceStarted = true
                ContextCompat.startForegroundService(context, Intent(context, AudioService::class.java))
            }
        } catch (_: Throwable) {
        }
        try {
            NativeAudio.start()
        } catch (_: Throwable) {
        }
    }

    fun applyAll(song: DawSong, master: Float = 0.85f) {
        try {
            NativeAudio.setMaster(master)
            NativeAudio.setTrack(0, song.neb.vol)
            NativeAudio.setTrack(1, song.spec.vol)
            NativeAudio.setTrack(2, song.drums.vol)
        } catch (_: Throwable) {
        }
        applyNebFx(song.neb.fx)
        applySpecFx(song.spec.fx)
        applyDrumFx(song.drums.fx)
    }

    fun applyNebFx(fx: NebFx) {
        try {
            NativeAudio.setNebFx(
                fx.dlyOn, fx.dlyAmt, fx.dlyFb, fx.dlyTime,
                fx.dstOn, fx.dstAmt, fx.dstIn, fx.dstOut,
                fx.verbOn, fx.verbAmt, fx.verbDecay
            )
        } catch (_: Throwable) {
        }
    }

    fun applySpecFx(fx: SpecFx) {
        try {
            NativeAudio.setSpecFx(
                fx.dlyOn, fx.dlyAmt, fx.dlyFb, fx.dlyTime,
                fx.verbOn, fx.verbAmt, fx.verbRoom
            )
        } catch (_: Throwable) {
        }
    }

    fun applyDrumFx(fx: DrumFx) {
        try {
            NativeAudio.setDrumFx(fx.verbOn, fx.verbAmt)
        } catch (_: Throwable) {
        }
    }

    fun auditionNeb(midi: Int, vel: Float, lenBeats: Int, stepDur: Float, pluck: Boolean, decay: Float, bend: List<LinePt>?, vex: List<LinePt>?) {
        try {
            val bn = (bend?.size ?: 0).coerceIn(0, 16)
            val bt = FloatArray(bn) { bend!![it].t }
            val bv = FloatArray(bn) { bend!![it].v }
            val vn = (vex?.size ?: 0).coerceIn(0, 16)
            val vt = FloatArray(vn) { vex!![it].t }
            val vv = FloatArray(vn) { vex!![it].v }
            NativeAudio.nebNote(midi, vel, lenBeats * stepDur, 0.01f, pluck, decay, bt, bv, vt, vv)
        } catch (_: Throwable) {
        }
    }

    fun auditionSpec(midi: Int, vel: Float, lenBeats: Int, stepDur: Float, wave: String) {
        try {
            NativeAudio.specNote(midi, vel, lenBeats * stepDur, 0.01f, waveToInt(wave))
        } catch (_: Throwable) {
        }
    }

    fun auditionDrum(midi: Int, vel: Float) {
        try {
            val kind = drumKind(midi)
            NativeAudio.drumHit(kind, midiFreq(midi), vel, drumPos(kind, vel), 0.01f)
        } catch (_: Throwable) {
        }
    }
}
