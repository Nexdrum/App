package com.sonolume.spectra.daw

import com.sonolume.spectra.NativeAudio
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object SeqSync {
    private val pushMutex = Mutex()
    private val newestRequest = AtomicLong(0)

    suspend fun pushFull(song: DawSong) {
        val ticket = newestRequest.incrementAndGet()
        withContext(Dispatchers.Default) {
            // Build the JNI payload before taking the serialization lock. This keeps a
            // newer request free to mark older work stale while arrays are being made.
            val neb = song.neb.notes
            val n = neb.size
            val s = IntArray(n); val midi = IntArray(n); val len = IntArray(n)
            val vel = FloatArray(n); val pluck = BooleanArray(n)
            val bendN = IntArray(n); val bendT = FloatArray(n * 16); val bendV = FloatArray(n * 16)
            val vexN = IntArray(n); val vexT = FloatArray(n * 16); val vexV = FloatArray(n * 16)
            for (i in neb.indices) {
                val x = neb[i]
                s[i] = x.s; midi[i] = x.midi; len[i] = x.len.coerceIn(1, 16)
                vel[i] = x.vel; pluck[i] = x.pluck
                val bn = x.bend.size.coerceIn(0, 16); bendN[i] = bn
                for (k in 0 until bn) {
                    bendT[i * 16 + k] = x.bend[k].t
                    bendV[i * 16 + k] = x.bend[k].v
                }
                val vn = x.vex.size.coerceIn(0, 16); vexN[i] = vn
                for (k in 0 until vn) {
                    vexT[i * 16 + k] = x.vex[k].t
                    vexV[i * 16 + k] = x.vex[k].v
                }
            }

            val spec = song.spec.notes
            val m = spec.size
            val ss = IntArray(m); val m1 = IntArray(m); val m2 = IntArray(m)
            val sl = IntArray(m); val sv = FloatArray(m)
            for (i in spec.indices) {
                val x = spec[i]
                ss[i] = x.s; m1[i] = x.midi1; m2[i] = x.midi2
                sl[i] = x.len.coerceIn(1, 16); sv[i] = x.vel
            }

            val drums = song.drums.hits
            val d = drums.size
            val ds = IntArray(d); val dm = IntArray(d); val dv = FloatArray(d)
            for (i in drums.indices) {
                ds[i] = drums[i].s; dm[i] = drums[i].midi; dv[i] = drums[i].vel
            }

            pushMutex.withLock {
                if (ticket != newestRequest.get()) return@withLock
                try {
                    NativeAudio.seqConfigure(
                        song.bpm.toFloat(),
                        song.top.coerceIn(1, 24),
                        song.bottom.coerceIn(1, 24),
                        song.loop,
                        song.metro
                    )
                    NativeAudio.seqSetAudible(song.audible("neb"), song.audible("spec"), song.audible("drums"))
                    NativeAudio.seqSetNebDecay(song.neb.decay)
                    NativeAudio.seqSetSpecWaves(waveToInt(song.spec.w1), waveToInt(song.spec.w2))
                    NativeAudio.seqSetNeb(s, midi, len, vel, pluck, bendN, bendT, bendV, vexN, vexT, vexV)
                    NativeAudio.seqSetSpec(ss, m1, m2, sl, sv)
                    // DspCore publishes the complete staged snapshot on this final call.
                    NativeAudio.seqSetDrums(ds, dm, dv)
                } catch (_: Throwable) {
                }
            }
        }
    }
}
