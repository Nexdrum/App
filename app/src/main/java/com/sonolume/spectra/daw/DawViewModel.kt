package com.sonolume.spectra.daw

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonolume.spectra.NativeAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class NebSel(val s: Int, val midi: Int)
data class SpecSel(val s: Int, val midi: Int)
data class DrumSel(val s: Int, val midi: Int)
data class Flash(val midi: Int, val vel: Float, val at: Long)

data class DawUiState(
    val song: DawSong = DawSong(),
    val playing: Boolean = false,
    val playStep: Int = 0,
    val flashes: List<Flash> = emptyList(),
    val status: String = "",
    val view: Int = 0,
    val nebSel: NebSel? = null,
    val specSel: SpecSel? = null,
    val drumSel: DrumSel? = null,
    val nebLineMode: String = "bend",
    val menuOpen: Boolean = true,
    val fxNeb: Boolean = false,
    val fxSpec: Boolean = false,
    val fxDrums: Boolean = false,
    val foldNeb: Boolean = false,
    val foldSpec: Boolean = false,
    val foldDrums: Boolean = false,
    val instrument: String? = null,
    val masterV: Float = 0.85f,
    val reverbV: Float = 0.25f,
    val delayV: Float = 0f
)

private const val SAVE_NAME = "spectradaw.json"

class DawViewModel : ViewModel() {
    private val _ui = MutableStateFlow(DawUiState())
    val ui: StateFlow<DawUiState> = _ui

    private var saveJob: Job? = null
    private var pollJob: Job? = null
    private var seqPushJob: Job? = null
    private var appContext: Context? = null
    private val taps = ArrayList<Long>()

    fun attach(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            loadAutosave()
        }
    }

    private fun saveFile(): File = File(appContext!!.filesDir, SAVE_NAME)

    private fun loadAutosave() {
        try {
            val f = saveFile()
            if (!f.exists()) return
            val d = JSONObject(f.readText())
            val hasNotes = (d.optJSONObject("neb")?.optJSONArray("notes")?.length() ?: 0) > 0 ||
                (d.optJSONObject("spec")?.optJSONArray("notes")?.length() ?: 0) > 0 ||
                (d.optJSONObject("drums")?.optJSONArray("hits")?.length() ?: 0) > 0
            if (!hasNotes) return
            _ui.update { it.copy(song = songFromJson(d), status = "Loaded save.") }
        } catch (_: Throwable) {
        }
    }

    private fun scheduleAutosave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(400)
            try {
                saveFile().writeText(_ui.value.song.toJson().toString())
            } catch (_: Throwable) {
            }
        }
    }

    private fun pushIfPlaying() {
        if (!_ui.value.playing) return
        // Coalesce rapid slider/drag edits so the native sequencer receives only the
        // newest complete snapshot instead of a backlog of stale full-song pushes.
        seqPushJob?.cancel()
        seqPushJob = viewModelScope.launch {
            delay(32)
            if (_ui.value.playing) SeqSync.pushFull(_ui.value.song)
        }
    }

    private fun edited() {
        scheduleAutosave()
        pushIfPlaying()
    }

    private fun flash(midi: Int, vel: Float) {
        val now = System.currentTimeMillis()
        _ui.update {
            it.copy(flashes = (it.flashes + Flash(midi, vel, now)).takeLast(24))
        }
    }

    fun setStatus(t: String) = _ui.update { it.copy(status = t) }

    fun play() {
        val ctx = appContext ?: return
        if (_ui.value.playing) return
        Audio.ensure(ctx)
        Audio.applyAll(_ui.value.song, _ui.value.masterV)
        val song = _ui.value.song
        viewModelScope.launch {
            SeqSync.pushFull(song)
            try {
                NativeAudio.seqPlay()
            } catch (_: Throwable) {
            }
            _ui.update { it.copy(playing = true, playStep = 0, flashes = emptyList()) }
            startPoll()
        }
    }

    fun stop() {
        pollJob?.cancel()
        seqPushJob?.cancel()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                NativeAudio.seqStop()
            } catch (_: Throwable) {
            }
        }
        _ui.update { it.copy(playing = false) }
    }

    private fun startPoll() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var last = -2
            while (isActive && _ui.value.playing) {
                delay(120)
                val (alive, pos) = withContext(Dispatchers.Default) {
                    try {
                        Pair(NativeAudio.seqIsPlaying(), NativeAudio.seqStep())
                    } catch (_: Throwable) {
                        Pair(false, -1)
                    }
                }
                if (!alive) {
                    _ui.update { it.copy(playing = false) }
                    break
                }
                if (pos >= 0 && pos != last) {
                    last = pos
                    val song = _ui.value.song
                    val now = System.currentTimeMillis()
                    val fl = ArrayList<Flash>()
                    for (x in song.neb.notes) if (x.s == pos && fl.size < 6) fl.add(Flash(x.midi, x.vel, now))
                    for (x in song.spec.notes) if (x.s == pos && fl.size < 6) {
                        fl.add(Flash(x.midi1, x.vel, now))
                        if (fl.size < 6) fl.add(Flash(x.midi2, x.vel, now))
                    }
                    for (h in song.drums.hits) if (h.s == pos && fl.size < 6) fl.add(Flash(h.midi, h.vel, now))
                    _ui.update { it.copy(playStep = pos, flashes = (it.flashes + fl).takeLast(24)) }
                }
            }
        }
    }

    fun setView(v: Int) = _ui.update { it.copy(view = v.coerceIn(0, 2)) }
    fun setMenu(open: Boolean) = _ui.update { it.copy(menuOpen = open) }
    fun setFxNeb(o: Boolean) = _ui.update { it.copy(fxNeb = o) }
    fun setFxSpec(o: Boolean) = _ui.update { it.copy(fxSpec = o) }
    fun setFxDrums(o: Boolean) = _ui.update { it.copy(fxDrums = o) }
    fun setFoldNeb(f: Boolean) = _ui.update { it.copy(foldNeb = f) }
    fun setFoldSpec(f: Boolean) = _ui.update { it.copy(foldSpec = f) }
    fun setFoldDrums(f: Boolean) = _ui.update { it.copy(foldDrums = f) }
    fun openInstrument(name: String?) = _ui.update { it.copy(instrument = name) }

    fun setMasterV(v: Float) {
        _ui.update { it.copy(masterV = v) }
        try {
            NativeAudio.setMaster(v)
        } catch (_: Throwable) {
        }
    }

    fun setReverbV(v: Float) {
        _ui.update { it.copy(reverbV = v) }
        try {
            NativeAudio.setGlobalReverb(v)
        } catch (_: Throwable) {
        }
    }

    fun setDelayV(v: Float) {
        _ui.update { it.copy(delayV = v) }
        try {
            NativeAudio.setGlobalDelayMix(v)
        } catch (_: Throwable) {
        }
    }

    fun setBpm(b: Int) {
        _ui.update { it.copy(song = it.song.copy(bpm = b.coerceIn(12, 240))) }
        edited()
    }

    fun tapTempo() {
        val now = System.currentTimeMillis()
        taps.add(now)
        while (taps.size > 5) taps.removeAt(0)
        if (taps.size > 1) {
            val b = (60000.0 / ((taps.last() - taps.first()).toDouble() / (taps.size - 1))).toInt()
            _ui.update { it.copy(song = it.song.copy(bpm = b.coerceIn(12, 240))) }
            edited()
        }
        viewModelScope.launch {
            delay(2200)
            taps.clear()
        }
    }

    fun toggleLoop() {
        _ui.update { it.copy(song = it.song.copy(loop = !it.song.loop)) }
        edited()
    }

    fun setMetro(on: Boolean) {
        _ui.update { it.copy(song = it.song.copy(metro = on)) }
        edited()
    }

    fun setTs(top: Int, bottom: Int) {
        _ui.update { it.copy(song = it.song.copy(top = top.coerceIn(1, 24), bottom = bottom.coerceIn(1, 24))) }
        edited()
    }

    fun clearAll() {
        stop()
        _ui.update {
            it.copy(
                song = it.song.copy(
                    neb = it.song.neb.copy(notes = emptyList()),
                    spec = it.song.spec.copy(notes = emptyList()),
                    drums = it.song.drums.copy(hits = emptyList())
                ),
                nebSel = null, specSel = null, drumSel = null, status = "Cleared whole song."
            )
        }
        scheduleAutosave()
    }

    fun demo() {
        stop()
        _ui.update {
            it.copy(
                song = demoSong(), nebSel = null, specSel = null, drumSel = null,
                status = "Demo song loaded."
            )
        }
        scheduleAutosave()
        play()
    }

    fun saveNow() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                saveFile().writeText(_ui.value.song.toJson().toString())
                _ui.update { it.copy(status = "Saved.") }
            } catch (_: Throwable) {
                _ui.update { it.copy(status = "Save failed.") }
            }
        }
    }

    fun loadNow() {
        stop()
        try {
            val f = saveFile()
            if (!f.exists()) {
                setStatus("No save yet.")
                return
            }
            _ui.update {
                it.copy(
                    song = songFromJson(JSONObject(f.readText())),
                    nebSel = null, specSel = null, drumSel = null, status = "Loaded save."
                )
            }
        } catch (_: Throwable) {
            setStatus("Could not load save.")
        }
    }

    fun exportText(): String {
        return try {
            _ui.value.song.toJson().toString(2)
        } catch (_: Throwable) {
            "{}"
        }
    }

    fun importText(text: String): Boolean {
        return try {
            val song = songFromJson(JSONObject(text))
            stop()
            _ui.update {
                it.copy(song = song, nebSel = null, specSel = null, drumSel = null, status = "Imported JSON.")
            }
            scheduleAutosave()
            true
        } catch (_: Throwable) {
            setStatus("Could not import JSON.")
            false
        }
    }

    fun setTrackVol(track: String, v: Float) {
        val song = _ui.value.song
        when (track) {
            "neb" -> {
                _ui.update { it.copy(song = song.copy(neb = song.neb.copy(vol = v))) }
                try {
                    NativeAudio.setTrack(0, v)
                } catch (_: Throwable) {
                }
            }
            "spec" -> {
                _ui.update { it.copy(song = song.copy(spec = song.spec.copy(vol = v))) }
                try {
                    NativeAudio.setTrack(1, v)
                } catch (_: Throwable) {
                }
            }
            else -> {
                _ui.update { it.copy(song = song.copy(drums = song.drums.copy(vol = v))) }
                try {
                    NativeAudio.setTrack(2, v)
                } catch (_: Throwable) {
                }
            }
        }
        scheduleAutosave()
    }

    fun setMute(track: String, on: Boolean) {
        val song = _ui.value.song
        when (track) {
            "neb" -> _ui.update { it.copy(song = song.copy(neb = song.neb.copy(mute = on))) }
            "spec" -> _ui.update { it.copy(song = song.copy(spec = song.spec.copy(mute = on))) }
            else -> _ui.update { it.copy(song = song.copy(drums = song.drums.copy(mute = on))) }
        }
        edited()
    }

    fun setSolo(track: String, on: Boolean) {
        val song = _ui.value.song
        when (track) {
            "neb" -> _ui.update { it.copy(song = song.copy(neb = song.neb.copy(solo = on))) }
            "spec" -> _ui.update { it.copy(song = song.copy(spec = song.spec.copy(solo = on))) }
            else -> _ui.update { it.copy(song = song.copy(drums = song.drums.copy(solo = on))) }
        }
        edited()
    }

    fun setNebDecay(v: Float) {
        _ui.update { it.copy(song = it.song.copy(neb = it.song.neb.copy(decay = v.coerceIn(50f, 3000f)))) }
        edited()
    }

    fun setSpecWave(slot: Int, w: String) {
        val s = _ui.value.song.spec
        _ui.update {
            it.copy(song = it.song.copy(spec = s.copy(w1 = if (slot == 0) w else s.w1, w2 = if (slot == 1) w else s.w2)))
        }
        edited()
    }

    fun setNebFx(fx: NebFx) {
        _ui.update { it.copy(song = it.song.copy(neb = it.song.neb.copy(fx = fx))) }
        Audio.applyNebFx(fx)
        edited()
    }

    fun setSpecFx(fx: SpecFx) {
        _ui.update { it.copy(song = it.song.copy(spec = it.song.spec.copy(fx = fx))) }
        Audio.applySpecFx(fx)
        edited()
    }

    fun setDrumFx(fx: DrumFx) {
        _ui.update { it.copy(song = it.song.copy(drums = it.song.drums.copy(fx = fx))) }
        Audio.applyDrumFx(fx)
        edited()
    }

    private fun prepareAudition() {
        val ctx = appContext ?: return
        Audio.ensure(ctx)
        val state = _ui.value
        Audio.applyAll(state.song, state.masterV)
    }

    fun auditionNeb(midi: Int, vel: Float = 0.95f, pluck: Boolean = false, bend: List<LinePt>? = null, vex: List<LinePt>? = null) {
        prepareAudition()
        val song = _ui.value.song
        Audio.auditionNeb(midi, vel, 2, stepDurSec(song.bpm, song.bottom), pluck, song.neb.decay, bend, vex)
        flash(midi, vel)
    }

    fun auditionSpec(midi: Int, vel: Float = 0.95f) {
        prepareAudition()
        val song = _ui.value.song
        Audio.auditionSpec(midi, vel, 2, stepDurSec(song.bpm, song.bottom), song.spec.w1)
        Audio.auditionSpec(midi, vel, 2, stepDurSec(song.bpm, song.bottom), song.spec.w2)
        flash(midi, vel)
    }

    fun auditionDrum(midi: Int, vel: Float) {
        prepareAudition()
        Audio.auditionDrum(midi, vel)
        flash(midi, vel)
    }

    fun nebAdd(s: Int, midi: Int, len: Int, audition: Boolean = true) {
        val song = _ui.value.song
        val list = song.neb.notes.filterNot { it.midi == midi && s >= it.s && s < it.s + it.len }.toMutableList()
        list.add(NebNote(s, midi, len.coerceIn(1, 16), song.neb.lastVel, song.neb.lastPluck))
        _ui.update {
            it.copy(song = song.copy(neb = song.neb.copy(notes = list)), nebSel = NebSel(s, midi))
        }
        if (audition) auditionNeb(midi)
        edited()
    }

    fun nebSelect(s: Int, midi: Int) {
        _ui.update { it.copy(nebSel = NebSel(s, midi)) }
    }

    fun nebMove(note: NebNote, ns: Int, nm: Int) {
        val song = _ui.value.song
        val grid = song.gridSteps()
        val nxs = ns.coerceIn(0, grid - 1)
        val nxm = nm.coerceIn(NEB_LO, NEB_HI)
        val list = song.neb.notes.filterNot { it === note }.filterNot {
            it.midi == nxm && !(nxs + note.len - 1 < it.s || nxs > it.s + it.len - 1)
        }.toMutableList()
        list.add(note.copy(s = nxs, midi = nxm))
        _ui.update { it.copy(song = song.copy(neb = song.neb.copy(notes = list)), nebSel = NebSel(nxs, nxm)) }
        auditionNeb(nxm, note.vel, note.pluck, note.bend, note.vex)
        edited()
    }

    fun nebExtend(note: NebNote, toS: Int) {
        val song = _ui.value.song
        val nl = (toS - note.s + 1).coerceIn(1, 16)
        val list = song.neb.notes.map { if (it === note) it.copy(len = nl) else it }
        _ui.update { it.copy(song = song.copy(neb = song.neb.copy(notes = list))) }
        auditionNeb(note.midi, note.vel, note.pluck, note.bend, note.vex)
        edited()
    }

    fun nebDelete(s: Int, midi: Int) {
        val song = _ui.value.song
        _ui.update {
            it.copy(
                song = song.copy(neb = song.neb.copy(notes = song.neb.notes.filterNot { x -> x.s == s && x.midi == midi })),
                nebSel = if (it.nebSel?.s == s && it.nebSel?.midi == midi) null else it.nebSel
            )
        }
        edited()
    }

    fun nebClear() {
        _ui.update {
            it.copy(song = it.song.copy(neb = it.song.neb.copy(notes = emptyList())), nebSel = null)
        }
        edited()
    }

    fun nebCycleVel(s: Int, midi: Int) {
        val song = _ui.value.song
        val n = song.neb.notes.find { it.s == s && it.midi == midi } ?: return
        val nv = velCycle(n.vel)
        _ui.update {
            it.copy(song = song.copy(neb = song.neb.copy(notes = song.neb.notes.map { x -> if (x === n) x.copy(vel = nv) else x }, lastVel = nv)))
        }
        auditionNeb(midi, nv, n.pluck, n.bend, n.vex)
        edited()
    }

    fun nebSelNote(): NebNote? {
        val sel = _ui.value.nebSel ?: return null
        return _ui.value.song.neb.notes.find { it.s == sel.s && it.midi == sel.midi }
    }

    fun nebSetLen(d: Int) {
        val n = nebSelNote() ?: return
        val nl = (n.len + d).coerceIn(1, 16)
        _ui.update {
            it.copy(song = it.song.copy(neb = it.song.neb.copy(notes = it.song.neb.notes.map { x -> if (x === n) x.copy(len = nl) else x })))
        }
        edited()
    }

    fun nebSetVel(v: Float) {        val n = nebSelNote() ?: return
        val nv = v.coerceIn(0.05f, 1.15f)
        _ui.update {
            it.copy(song = it.song.copy(neb = it.song.neb.copy(notes = it.song.neb.notes.map { x -> if (x === n) x.copy(vel = nv) else x }, lastVel = nv)))
        }
        auditionNeb(n.midi, nv, n.pluck, n.bend, n.vex)
        edited()
    }

    fun nebSetVelLive(v: Float) {
        val n = nebSelNote() ?: return
        val nv = v.coerceIn(0.05f, 1.15f)
        _ui.update {
            it.copy(song = it.song.copy(neb = it.song.neb.copy(notes = it.song.neb.notes.map { x -> if (x === n) x.copy(vel = nv) else x }, lastVel = nv)))
        }
        scheduleAutosave()
    }

    fun nebTogglePluck() {
        val n = nebSelNote() ?: return
        _ui.update {
            it.copy(
                song = it.song.copy(
                    neb = it.song.neb.copy(
                        notes = it.song.neb.notes.map { x -> if (x === n) x.copy(pluck = !x.pluck) else x },
                        lastPluck = !n.pluck
                    )
                )
            )
        }
        auditionNeb(n.midi, n.vel, !n.pluck, n.bend, n.vex)
        edited()
    }

    fun setNebLineMode(m: String) {
        _ui.update { it.copy(nebLineMode = if (m == "vex") "vex" else "bend") }
    }

    fun nebToggleLineEdit() {
        val n = nebSelNote() ?: return
        _ui.update {
            it.copy(song = it.song.copy(neb = it.song.neb.copy(notes = it.song.neb.notes.map { x -> if (x === n) x.copy(lineEdit = !x.lineEdit) else x })))
        }
        edited()
    }

    fun nebClearLine() {
        val n = nebSelNote() ?: return
        val vex = _ui.value.nebLineMode == "vex"
        _ui.update {
            it.copy(song = it.song.copy(neb = it.song.neb.copy(notes = it.song.neb.notes.map { x -> if (x === n) (if (vex) x.copy(vex = emptyList()) else x.copy(bend = emptyList())) else x })))
        }
        edited()
    }

    fun nebAddLinePt(t: Float, v: Float) {
        val n = nebSelNote() ?: return
        if (!n.lineEdit) return
        val vex = _ui.value.nebLineMode == "vex"
        val pt = if (vex) LinePt(t.coerceIn(0f, 1f), v.coerceIn(0f, 1.15f)) else LinePt(t.coerceIn(0f, 1f), v.coerceIn(-12f, 12f))
        _ui.update {
            it.copy(
                song = it.song.copy(
                    neb = it.song.neb.copy(
                        notes = it.song.neb.notes.map { x ->
                            if (x === n) {
                                if (vex) x.copy(vex = (x.vex + pt).sortedBy { p -> p.t }) else x.copy(bend = (x.bend + pt).sortedBy { p -> p.t })
                            } else x
                        }
                    )
                )
            )
        }
        auditionNeb(n.midi, n.vel, n.pluck, nebSelNote()?.bend, nebSelNote()?.vex)
        edited()
    }

    fun nebMoveLinePt(idx: Int, t: Float, v: Float) {
        val n = nebSelNote() ?: return
        val vex = _ui.value.nebLineMode == "vex"
        _ui.update {
            it.copy(
                song = it.song.copy(
                    neb = it.song.neb.copy(
                        notes = it.song.neb.notes.map { x ->
                            if (x === n) {
                                if (vex) {
                                    val m = x.vex.toMutableList()
                                    if (idx in m.indices) {
                                        val lo = if (idx > 0) m[idx - 1].t + 0.01f else 0f
                                        val hi = if (idx < m.size - 1) m[idx + 1].t - 0.01f else 1f
                                        m[idx] = LinePt(t.coerceIn(lo, hi), v.coerceIn(0f, 1.15f))
                                    }
                                    x.copy(vex = m)
                                } else {
                                    val m = x.bend.toMutableList()
                                    if (idx in m.indices) {
                                        val lo = if (idx > 0) m[idx - 1].t + 0.01f else 0f
                                        val hi = if (idx < m.size - 1) m[idx + 1].t - 0.01f else 1f
                                        m[idx] = LinePt(t.coerceIn(lo, hi), v.coerceIn(-12f, 12f))
                                    }
                                    x.copy(bend = m)
                                }
                            } else x
                        }
                    )
                )
            )
        }
        scheduleAutosave()
        pushIfPlaying()
    }

    fun nebRemoveLinePt(idx: Int) {
        val n = nebSelNote() ?: return
        val vex = _ui.value.nebLineMode == "vex"
        _ui.update {
            it.copy(
                song = it.song.copy(
                    neb = it.song.neb.copy(
                        notes = it.song.neb.notes.map { x ->
                            if (x === n) {
                                if (vex) x.copy(vex = x.vex.filterIndexed { i, _ -> i != idx }) else x.copy(bend = x.bend.filterIndexed { i, _ -> i != idx })
                            } else x
                        }
                    )
                )
            )
        }
        edited()
    }

    fun specCover(s: Int, midi: Int): SpecNote? {
        return _ui.value.song.spec.notes.find { (it.midi1 == midi || it.midi2 == midi) && s >= it.s && s < it.s + it.len }
    }

    fun specAdd(s: Int, midi: Int, len: Int, audition: Boolean = true) {
        val song = _ui.value.song
        val list = song.spec.notes.filterNot {
            (it.midi1 == midi || it.midi2 == midi) && s >= it.s && s < it.s + it.len
        }.toMutableList()
        list.add(SpecNote(s, midi, midi, len.coerceIn(1, 16), song.spec.lastVel))
        _ui.update { it.copy(song = song.copy(spec = song.spec.copy(notes = list)), specSel = SpecSel(s, midi)) }
        if (audition) auditionSpec(midi)
        edited()
    }

    fun specSelect(s: Int, midi: Int) {
        _ui.update { it.copy(specSel = SpecSel(s, midi)) }
    }

    fun specRetune(note: SpecNote, half: Int, nm: Int) {
        val nxm = nm.coerceIn(SPEC_LO, SPEC_HI)
        _ui.update {
            it.copy(
                song = it.song.copy(
                    spec = it.song.spec.copy(
                        notes = it.song.spec.notes.map { x ->
                            if (x === note) {
                                if (half == 0) x.copy(midi1 = nxm) else x.copy(midi2 = nxm)
                            } else x
                        }
                    )
                ),
                specSel = SpecSel(note.s, nxm)
            )
        }
        auditionSpec(nxm, note.vel)
        edited()
    }

    fun specExtend(note: SpecNote, toS: Int) {
        val nl = (toS - note.s + 1).coerceIn(1, 16)
        _ui.update {
            it.copy(song = it.song.copy(spec = it.song.spec.copy(notes = it.song.spec.notes.map { x -> if (x === note) x.copy(len = nl) else x })))
        }
        edited()
    }

    fun specExtendRetune(note: SpecNote, half: Int, midi: Int, toS: Int) {
        val nxm = midi.coerceIn(SPEC_LO, SPEC_HI)
        val nl = (toS - note.s + 1).coerceIn(1, 16)
        _ui.update {
            it.copy(
                song = it.song.copy(
                    spec = it.song.spec.copy(
                        notes = it.song.spec.notes.map { x ->
                            if (x === note) {
                                val nn = if (half == 0) x.copy(midi1 = nxm) else x.copy(midi2 = nxm)
                                nn.copy(len = nl)
                            } else x
                        }
                    )
                ),
                specSel = SpecSel(note.s, nxm)
            )
        }
        auditionSpec(nxm, note.vel)
        edited()
    }

    fun specMoveRetune(note: SpecNote, half: Int, midi: Int, toS: Int) {
        val song = _ui.value.song
        val nxs = toS.coerceIn(0, song.gridSteps() - 1)
        val nxm = midi.coerceIn(SPEC_LO, SPEC_HI)
        _ui.update {
            it.copy(
                song = it.song.copy(
                    spec = it.song.spec.copy(
                        notes = it.song.spec.notes.map { x ->
                            if (x === note) {
                                if (half == 0) x.copy(s = nxs, midi1 = nxm) else x.copy(s = nxs, midi2 = nxm)
                            } else x
                        }
                    )
                ),
                specSel = SpecSel(nxs, nxm)
            )
        }
        auditionSpec(nxm, note.vel)
        edited()
    }

    fun specMove(note: SpecNote, ns: Int, nm: Int) {
        val song = _ui.value.song
        val grid = song.gridSteps()
        val nxs = ns.coerceIn(0, grid - 1)
        val d1 = nm - note.midi1
        val nm1 = (note.midi1 + d1).coerceIn(SPEC_LO, SPEC_HI)
        val nm2 = (note.midi2 + d1).coerceIn(SPEC_LO, SPEC_HI)
        val list = song.spec.notes.filterNot { it === note }.toMutableList()
        list.add(note.copy(s = nxs, midi1 = nm1, midi2 = nm2))
        _ui.update { it.copy(song = song.copy(spec = song.spec.copy(notes = list)), specSel = SpecSel(nxs, nm1)) }
        auditionSpec(nm1, note.vel)
        edited()
    }

    fun specDelete(s: Int, midi: Int) {
        val song = _ui.value.song
        val hit = song.spec.notes.find { (it.midi1 == midi || it.midi2 == midi) && s >= it.s && s < it.s + it.len }
        _ui.update {
            it.copy(
                song = song.copy(spec = song.spec.copy(notes = song.spec.notes.filterNot { x -> x === hit })),
                specSel = if (it.specSel?.s == s && it.specSel?.midi == midi) null else it.specSel
            )
        }
        edited()
    }

    fun specClear() {
        _ui.update { it.copy(song = it.song.copy(spec = it.song.spec.copy(notes = emptyList())), specSel = null) }
        edited()
    }

    fun specCycleVel(s: Int, midi: Int) {
        val n = specCover(s, midi) ?: return
        val nv = velCycle(n.vel)
        _ui.update {
            it.copy(song = it.song.copy(spec = it.song.spec.copy(notes = it.song.spec.notes.map { x -> if (x === n) x.copy(vel = nv) else x }, lastVel = nv)))
        }
        auditionSpec(n.midi1, nv)
        edited()
    }

    fun specSelNote(): SpecNote? {
        val sel = _ui.value.specSel ?: return null
        return _ui.value.song.spec.notes.find { it.s == sel.s && (it.midi1 == sel.midi || it.midi2 == sel.midi) }
    }

    fun specSetLen(d: Int) {
        val n = specSelNote() ?: return
        val nl = (n.len + d).coerceIn(1, 16)
        _ui.update {
            it.copy(song = it.song.copy(spec = it.song.spec.copy(notes = it.song.spec.notes.map { x -> if (x === n) x.copy(len = nl) else x })))
        }
        edited()
    }

    fun specSetVel(v: Float) {
        val n = specSelNote() ?: return
        val nv = v.coerceIn(0.05f, 1.15f)
        _ui.update {
            it.copy(song = it.song.copy(spec = it.song.spec.copy(notes = it.song.spec.notes.map { x -> if (x === n) x.copy(vel = nv) else x }, lastVel = nv)))
        }
        auditionSpec(n.midi1, nv)
        edited()
    }

    fun specSetVelLive(v: Float) {
        val n = specSelNote() ?: return
        val nv = v.coerceIn(0.05f, 1.15f)
        _ui.update {
            it.copy(song = it.song.copy(spec = it.song.spec.copy(notes = it.song.spec.notes.map { x -> if (x === n) x.copy(vel = nv) else x }, lastVel = nv)))
        }
        scheduleAutosave()
    }

    fun drumToggle(s: Int, midi: Int) {
        val song = _ui.value.song
        val ix = song.drums.hits.indexOfFirst { it.s == s && it.midi == midi }
        val sel = _ui.value.drumSel
        if (ix >= 0) {
            if (sel?.s == s && sel.midi == midi) {
                _ui.update {
                    it.copy(
                        song = song.copy(drums = song.drums.copy(hits = song.drums.hits.filterIndexed { i, _ -> i != ix })),
                        drumSel = null
                    )
                }
            } else {
                _ui.update { it.copy(drumSel = DrumSel(s, midi)) }
                return
            }
        } else {
            val list = song.drums.hits + DrumHit(s, midi, song.drums.lastVel)
            _ui.update { it.copy(song = song.copy(drums = song.drums.copy(hits = list)), drumSel = DrumSel(s, midi)) }
            auditionDrum(midi, song.drums.lastVel)
        }
        edited()
    }

    fun drumDelete(s: Int, midi: Int) {
        val song = _ui.value.song
        _ui.update {
            it.copy(
                song = song.copy(drums = song.drums.copy(hits = song.drums.hits.filterNot { x -> x.s == s && x.midi == midi })),
                drumSel = if (it.drumSel?.s == s && it.drumSel?.midi == midi) null else it.drumSel
            )
        }
        edited()
    }

    fun drumClear() {
        _ui.update { it.copy(song = it.song.copy(drums = it.song.drums.copy(hits = emptyList())), drumSel = null) }
        edited()
    }

    fun drumCycleVel(s: Int, midi: Int) {
        val song = _ui.value.song
        val h = song.drums.hits.find { it.s == s && it.midi == midi } ?: return
        val nv = velCycle(h.vel)
        _ui.update {
            it.copy(
                song = song.copy(drums = song.drums.copy(hits = song.drums.hits.map { x -> if (x === h) x.copy(vel = nv) else x }, lastVel = nv)),
                drumSel = DrumSel(s, midi)
            )
        }
        auditionDrum(midi, nv)
        edited()
    }

    fun drumSelHit(): DrumHit? {
        val sel = _ui.value.drumSel ?: return null
        return _ui.value.song.drums.hits.find { it.s == sel.s && it.midi == sel.midi }
    }

    fun drumSetVel(v: Float) {
        val h = drumSelHit() ?: return
        val nv = v.coerceIn(0.05f, 1.15f)
        _ui.update {
            it.copy(song = it.song.copy(drums = it.song.drums.copy(hits = it.song.drums.hits.map { x -> if (x === h) x.copy(vel = nv) else x }, lastVel = nv)))
        }
        auditionDrum(h.midi, nv)
        edited()
    }

    fun drumSetVelLive(v: Float) {
        val h = drumSelHit() ?: return
        val nv = v.coerceIn(0.05f, 1.15f)
        _ui.update {
            it.copy(song = it.song.copy(drums = it.song.drums.copy(hits = it.song.drums.hits.map { x -> if (x === h) x.copy(vel = nv) else x }, lastVel = nv)))
        }
        scheduleAutosave()
    }
}
