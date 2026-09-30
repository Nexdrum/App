package com.sonolume.spectra.daw

import org.json.JSONArray
import org.json.JSONObject

data class LinePt(val t: Float, val v: Float)

data class NebNote(
    val s: Int,
    val midi: Int,
    val len: Int = 1,
    val vel: Float = 0.95f,
    val pluck: Boolean = false,
    val bend: List<LinePt> = emptyList(),
    val vex: List<LinePt> = emptyList(),
    val lineEdit: Boolean = false
)

data class SpecNote(
    val s: Int,
    val midi1: Int,
    val midi2: Int,
    val len: Int = 1,
    val vel: Float = 0.9f
)

data class DrumHit(val s: Int, val midi: Int, val vel: Float = 1f)

data class NebFx(
    val dlyOn: Boolean = false, val dlyAmt: Float = 0.25f, val dlyFb: Float = 0.35f, val dlyTime: Float = 0.32f,
    val dstOn: Boolean = false, val dstAmt: Float = 25f, val dstIn: Float = 1f, val dstOut: Float = 0.85f,
    val verbOn: Boolean = false, val verbAmt: Float = 0.25f, val verbDecay: Float = 2.5f
)

data class SpecFx(
    val dlyOn: Boolean = false, val dlyAmt: Float = 0.4f, val dlyFb: Float = 0.3f, val dlyTime: Float = 0.25f,
    val verbOn: Boolean = false, val verbAmt: Float = 0.3f, val verbRoom: Float = 0.5f
)

data class DrumFx(val verbOn: Boolean = false, val verbAmt: Float = 0.18f)

data class NebTrack(
    val notes: List<NebNote> = emptyList(),
    val vol: Float = 0.8f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val decay: Float = 700f,
    val lastVel: Float = 0.95f,
    val lastPluck: Boolean = false,
    val fx: NebFx = NebFx()
)

data class SpecTrack(
    val notes: List<SpecNote> = emptyList(),
    val vol: Float = 0.7f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val w1: String = "square",
    val w2: String = "sawtooth",
    val lastVel: Float = 0.95f,
    val fx: SpecFx = SpecFx()
)

data class DrumTrack(
    val hits: List<DrumHit> = emptyList(),
    val vol: Float = 0.9f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val lastVel: Float = 1f,
    val fx: DrumFx = DrumFx()
)

data class DawSong(
    val bpm: Int = 112,
    val top: Int = 4,
    val bottom: Int = 4,
    val loop: Boolean = true,
    val metro: Boolean = false,
    val neb: NebTrack = NebTrack(),
    val spec: SpecTrack = SpecTrack(),
    val drums: DrumTrack = DrumTrack()
)

fun DawSong.lastUsedStep(): Int {
    var m = -1
    for (x in neb.notes) m = maxOf(m, x.s + maxOf(1, x.len) - 1)
    for (x in spec.notes) m = maxOf(m, x.s + maxOf(1, x.len) - 1)
    for (x in drums.hits) m = maxOf(m, x.s)
    return m
}

fun DawSong.contentSteps(): Int = contentSteps(top, bottom, lastUsedStep())

fun DawSong.gridSteps(): Int = contentSteps() + 16

fun DawSong.audible(track: String): Boolean {
    val anySolo = neb.solo || spec.solo || drums.solo
    return when (track) {
        "neb" -> !neb.mute && (!anySolo || neb.solo)
        "spec" -> !spec.mute && (!anySolo || spec.solo)
        else -> !drums.mute && (!anySolo || drums.solo)
    }
}

private fun lineList(a: JSONArray?, key: String, lo: Float, hi: Float): List<LinePt> {
    if (a == null) return emptyList()
    val out = ArrayList<LinePt>(a.length())
    for (i in 0 until a.length()) {
        val p = a.optJSONObject(i) ?: continue
        val t = p.optDouble("t", Double.NaN)
        val v = p.optDouble(key, Double.NaN)
        if (!t.isFinite() || !v.isFinite()) continue
        out.add(LinePt(t.toFloat().coerceIn(0f, 1f), v.toFloat().coerceIn(lo, hi)))
    }
    return out.sortedBy { it.t }
}

fun songFromJson(root: JSONObject): DawSong {
    val bpm = root.optInt("bpm", 112).coerceIn(12, 240)
    var top = 4
    var bottom = 4
    val ts = root.optJSONObject("ts")
    if (ts != null) {
        top = ts.optInt("top", 4).coerceIn(1, 24)
        var b = ts.optInt("bottom", 4)
        if (b == 2 || b == 8 || b == 16) b = 16 / b
        if (b < 1 || b > 24) b = 4
        bottom = b
    }
    val neb = root.optJSONObject("neb")
    val spec = root.optJSONObject("spec")
    val drums = root.optJSONObject("drums")
    val nebNotes = ArrayList<NebNote>()
    neb?.optJSONArray("notes")?.let { arr ->
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i) ?: continue
            nebNotes.add(
                NebNote(
                    s = n.optInt("s"),
                    midi = n.optInt("midi", 60).coerceIn(NEB_LO, NEB_HI),
                    len = (if (n.has("len")) n.optInt("len", 1) else 1).coerceIn(1, 16),
                    vel = n.optFloatE("vel", 0.95f).coerceIn(0.05f, 1.15f),
                    pluck = n.optBoolean("pluck"),
                    bend = lineList(n.optJSONArray("bend"), "st", -12f, 12f),
                    vex = lineList(n.optJSONArray("vex"), "v", 0f, 1.15f),
                    lineEdit = n.optBoolean("lineEdit")
                )
            )
        }
    }
    val nebFx = neb?.optJSONObject("fx")
    val specNotes = ArrayList<SpecNote>()
    spec?.optJSONArray("notes")?.let { arr ->
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i) ?: continue
            if (n.has("midi1")) {
                val l1 = if (n.has("len1")) n.optInt("len1", 1) else n.optInt("len", 1)
                val l2 = if (n.has("len2")) n.optInt("len2", 1) else n.optInt("len", 1)
                specNotes.add(
                    SpecNote(
                        s = n.optInt("s"),
                        midi1 = n.optInt("midi1", 60).coerceIn(SPEC_LO, SPEC_HI),
                        midi2 = n.optInt("midi2", 72).coerceIn(SPEC_LO, SPEC_HI),
                        len = maxOf(l1, l2).coerceIn(1, 16),
                        vel = n.optFloatE("vel", 0.9f).coerceIn(0.05f, 1.15f)
                    )
                )
            } else {
                val m = n.optInt("midi", 60)
                specNotes.add(
                    SpecNote(
                        s = n.optInt("s"),
                        midi1 = m.coerceIn(SPEC_LO, SPEC_HI),
                        midi2 = (m + 12).coerceIn(SPEC_LO, SPEC_HI),
                        len = n.optInt("len", 1).coerceIn(1, 16),
                        vel = n.optFloatE("vel", 0.9f).coerceIn(0.05f, 1.15f)
                    )
                )
            }
        }
    }
    val specFx = spec?.optJSONObject("fx")
    val drumHits = ArrayList<DrumHit>()
    drums?.optJSONArray("hits")?.let { arr ->
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i) ?: continue
            drumHits.add(
                DrumHit(
                    s = n.optInt("s"),
                    midi = n.optInt("midi", 40).coerceIn(40, 60),
                    vel = n.optFloatE("vel", 1f).coerceIn(0.05f, 1.15f)
                )
            )
        }
    }
    val drumFx = drums?.optJSONObject("fx")
    return DawSong(
        bpm = bpm, top = top, bottom = bottom,
        loop = true, metro = false,
        neb = NebTrack(
            notes = nebNotes,
            vol = neb.optFloatE("vol", 0.8f).coerceIn(0f, 1.5f),
            mute = neb.optBoolean("mute"), solo = neb.optBoolean("solo"),
            decay = neb.optFloatE("decay", 700f).coerceIn(50f, 3000f),
            lastVel = neb.optFloatE("lastVel", 0.95f).coerceIn(0.05f, 1.15f),
            lastPluck = neb.optBoolean("lastPluck"),
            fx = NebFx(
                dlyOn = nebFx.optBoolean("dlyOn"), dlyAmt = nebFx.optFloatE("dlyAmt", 0.25f),
                dlyFb = nebFx.optFloatE("dlyFb", 0.35f), dlyTime = nebFx.optFloatE("dlyTime", 0.32f),
                dstOn = nebFx.optBoolean("dstOn"), dstAmt = nebFx.optFloatE("dstAmt", 25f),
                dstIn = nebFx.optFloatE("dstIn", 1f), dstOut = nebFx.optFloatE("dstOut", 0.85f),
                verbOn = nebFx.optBoolean("verbOn"), verbAmt = nebFx.optFloatE("verbAmt", 0.25f),
                verbDecay = nebFx.optFloatE("verbDecay", 2.5f)
            )
        ),
        spec = SpecTrack(
            notes = specNotes,
            vol = spec.optFloatE("vol", 0.7f).coerceIn(0f, 1.5f),
            mute = spec.optBoolean("mute"), solo = spec.optBoolean("solo"),
            w1 = spec.optString("w1", "square").takeIf { it in setOf("sine", "square", "sawtooth", "triangle") } ?: "square",
            w2 = spec.optString("w2", "sawtooth").takeIf { it in setOf("sine", "square", "sawtooth", "triangle") } ?: "sawtooth",
            lastVel = spec.optFloatE("lastVel", 0.95f).coerceIn(0.05f, 1.15f),
            fx = SpecFx(
                dlyOn = specFx.optBoolean("dlyOn"), dlyAmt = specFx.optFloatE("dlyAmt", 0.4f),
                dlyFb = specFx.optFloatE("dlyFb", 0.3f), dlyTime = specFx.optFloatE("dlyTime", 0.25f),
                verbOn = specFx.optBoolean("verbOn"), verbAmt = specFx.optFloatE("verbAmt", 0.3f),
                verbRoom = specFx.optFloatE("verbRoom", 0.5f)
            )
        ),
        drums = DrumTrack(
            hits = drumHits,
            vol = drums.optFloatE("vol", 0.9f).coerceIn(0f, 1.5f),
            mute = drums.optBoolean("mute"), solo = drums.optBoolean("solo"),
            lastVel = drums.optFloatE("lastVel", 1f).coerceIn(0.05f, 1.15f),
            fx = DrumFx(
                verbOn = drumFx.optBoolean("verbOn"),
                verbAmt = drumFx.optFloatE("verbAmt", 0.18f)
            )
        )
    )
}

private fun lineToJson(pts: List<LinePt>, key: String): JSONArray {
    val a = JSONArray()
    for (p in pts) {
        val o = JSONObject()
        o.put("t", p.t.toDouble())
        o.put(key, p.v.toDouble())
        a.put(o)
    }
    return a
}

fun DawSong.toJson(): JSONObject {
    val root = JSONObject()
    root.put("bpm", bpm)
    root.put("bars", 2)
    root.put("ts", JSONObject().put("top", top).put("bottom", bottom))
    val nebO = JSONObject()
    val nebNotes = JSONArray()
    for (n in neb.notes) {
        val o = JSONObject()
        o.put("s", n.s); o.put("midi", n.midi); o.put("len", n.len)
        o.put("vel", n.vel.toDouble()); o.put("pluck", n.pluck)
        o.put("bend", lineToJson(n.bend, "st")); o.put("vex", lineToJson(n.vex, "v"))
        o.put("lineEdit", n.lineEdit)
        nebNotes.put(o)
    }
    nebO.put("notes", nebNotes)
    nebO.put("vol", neb.vol.toDouble()); nebO.put("mute", neb.mute); nebO.put("solo", neb.solo)
    nebO.put("decay", neb.decay.toDouble()); nebO.put("lastVel", neb.lastVel.toDouble())
    nebO.put("lastPluck", neb.lastPluck)
    nebO.put(
        "fx", JSONObject()
            .put("dlyOn", neb.fx.dlyOn).put("dlyAmt", neb.fx.dlyAmt.toDouble())
            .put("dlyFb", neb.fx.dlyFb.toDouble()).put("dlyTime", neb.fx.dlyTime.toDouble())
            .put("dstOn", neb.fx.dstOn).put("dstAmt", neb.fx.dstAmt.toDouble())
            .put("dstIn", neb.fx.dstIn.toDouble()).put("dstOut", neb.fx.dstOut.toDouble())
            .put("verbOn", neb.fx.verbOn).put("verbAmt", neb.fx.verbAmt.toDouble())
            .put("verbDecay", neb.fx.verbDecay.toDouble())
    )
    root.put("neb", nebO)
    val specO = JSONObject()
    val specNotes = JSONArray()
    for (n in spec.notes) {
        val o = JSONObject()
        o.put("s", n.s); o.put("midi1", n.midi1); o.put("midi2", n.midi2)
        o.put("len", n.len); o.put("vel", n.vel.toDouble())
        specNotes.put(o)
    }
    specO.put("notes", specNotes)
    specO.put("vol", spec.vol.toDouble()); specO.put("mute", spec.mute); specO.put("solo", spec.solo)
    specO.put("w1", spec.w1); specO.put("w2", spec.w2); specO.put("lastVel", spec.lastVel.toDouble())
    specO.put(
        "fx", JSONObject()
            .put("dlyOn", spec.fx.dlyOn).put("dlyAmt", spec.fx.dlyAmt.toDouble())
            .put("dlyFb", spec.fx.dlyFb.toDouble()).put("dlyTime", spec.fx.dlyTime.toDouble())
            .put("verbOn", spec.fx.verbOn).put("verbAmt", spec.fx.verbAmt.toDouble())
            .put("verbRoom", spec.fx.verbRoom.toDouble())
    )
    root.put("spec", specO)
    val drumO = JSONObject()
    val hits = JSONArray()
    for (h in drums.hits) {
        val o = JSONObject()
        o.put("s", h.s); o.put("midi", h.midi); o.put("vel", h.vel.toDouble())
        hits.put(o)
    }
    drumO.put("hits", hits)
    drumO.put("vol", drums.vol.toDouble()); drumO.put("mute", drums.mute); drumO.put("solo", drums.solo)
    drumO.put("lastVel", drums.lastVel.toDouble())
    drumO.put("fx", JSONObject().put("verbOn", drums.fx.verbOn).put("verbAmt", drums.fx.verbAmt.toDouble()))
    root.put("drums", drumO)
    return root
}

fun demoSong(): DawSong {
    val neb = listOf(
        NebNote(0, 60, 2, 0.95f, false), NebNote(2, 62, 2, 0.9f, false),
        NebNote(4, 64, 3, 1f, false), NebNote(8, 65, 2, 0.95f, false),
        NebNote(10, 64, 2, 0.9f, false), NebNote(12, 62, 2, 0.9f, true),
        NebNote(14, 60, 2, 0.7f, true), NebNote(16, 60, 2, 0.9f, false),
        NebNote(18, 67, 2, 1f, false), NebNote(20, 65, 2, 0.95f, false),
        NebNote(22, 64, 2, 0.9f, false), NebNote(24, 62, 2, 0.9f, false),
        NebNote(
            26, 60, 4, 1f, false,
            bend = listOf(LinePt(0f, 0f), LinePt(0.5f, 2f), LinePt(1f, 0f))
        )
    )
    val spec = listOf(
        SpecNote(0, 48, 55, 8, 0.8f), SpecNote(8, 53, 57, 8, 0.8f),
        SpecNote(16, 55, 62, 8, 0.85f), SpecNote(24, 53, 60, 8, 0.8f)
    )
    val drums = ArrayList<DrumHit>()
    for (s in 0 until 32 step 4) drums.add(DrumHit(s, 40, 1f))
    for (s in 4 until 32 step 8) drums.add(DrumHit(s, 48, 1f))
    for (s in 2 until 32 step 4) drums.add(DrumHit(s, 56, 0.7f))
    drums.add(DrumHit(0, 54, 1f))
    return DawSong(bpm = 112, top = 4, bottom = 4, neb = NebTrack(notes = neb), spec = SpecTrack(notes = spec), drums = DrumTrack(hits = drums))
}

private fun JSONObject?.optBoolean(key: String): Boolean = this?.optBoolean(key, false) ?: false

private fun JSONObject?.optFloatE(key: String, d: Float): Float {
    if (this == null || !has(key) || isNull(key)) return d
    return try {
        optDouble(key, d.toDouble()).toFloat()
    } catch (_: Throwable) {
        d
    }
}

private fun JSONObject?.optString(key: String, d: String): String {
    if (this == null) return d
    return try {
        optString(key, d) ?: d
    } catch (_: Throwable) {
        d
    }
}

private fun JSONObject?.optInt(key: String, d: Int): Int {
    if (this == null) return d
    return try {
        optInt(key, d)
    } catch (_: Throwable) {
        d
    }
}
