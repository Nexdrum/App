package com.sonolume.spectra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonolume.spectra.daw.DawViewModel
import com.sonolume.spectra.daw.LinePt
import com.sonolume.spectra.daw.NEB_HI
import com.sonolume.spectra.daw.NEB_LO
import com.sonolume.spectra.daw.NebNote
import com.sonolume.spectra.daw.barLen
import com.sonolume.spectra.daw.beatLen
import com.sonolume.spectra.daw.gridSteps
import com.sonolume.spectra.daw.drumZoneName
import com.sonolume.spectra.daw.midiName
import com.sonolume.spectra.daw.PITCH_COLORS
import com.sonolume.spectra.daw.velWord

private data class NebDrag(
    val mode: String,
    val note: NebNote?,
    val fromS: Int,
    val fromMidi: Int,
    var toS: Int,
    var toMidi: Int,
    var moved: Boolean
)

@Composable
fun NebScreen(vm: DawViewModel) {
    val ui = vm.collectUi()
    val song = ui.song
    var drag by remember { mutableStateOf<NebDrag?>(null) }
    var pointIdx by remember { mutableStateOf(0) }
    val rows = remember { (NEB_HI downTo NEB_LO).toList() }
    val steps = song.gridSteps()
    val bl = beatLen(song.bottom)
    val bar = barLen(song.top, song.bottom)
    val noteByHead = remember(song.neb.notes) {
        song.neb.notes.associateBy { Pair(it.s, it.midi) }
    }
    val coverOf = remember(song.neb.notes) {
        val m = HashMap<Pair<Int, Int>, NebNote>()
        for (n in song.neb.notes) {
            for (s in n.s until n.s + n.len) m[Pair(s, n.midi)] = n
        }
        m
    }
    TrackHead(
        "✦ Nebular", "bowed / plucked strings", ui.foldNeb, { vm.setFoldNeb(!ui.foldNeb) },
        { vm.openInstrument("nebular") }, { vm.nebClear() },
        ui.fxNeb, { vm.setFxNeb(!ui.fxNeb) },
        song.neb.mute, song.neb.solo,
        { vm.setMute("neb", !song.neb.mute) }, { vm.setSolo("neb", !song.neb.solo) }
    ) {
        VolSlider("Vol", song.neb.vol) { vm.setTrackVol("neb", it) }
    }
    if (!ui.foldNeb) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Decay", fontSize = 9.sp, color = SpectraMuted)
            Slider(
                value = song.neb.decay, onValueChange = { vm.setNebDecay(it) },
                valueRange = 100f..3000f, modifier = Modifier.weight(1f).height(30.dp)
            )
            Text(song.neb.decay.toInt().toString() + " ms", fontSize = 9.sp, color = SpectraMuted)
        }
    }
    if (!ui.foldNeb && ui.fxNeb) NebFxPanel(vm)
    val ghost = remember(drag) {
        val d = drag
        if (d == null || !d.moved) emptySet()
        else when (d.mode) {
            "extend" -> {
                val n = d.note ?: return@remember emptySet<Pair<Int, Int>>()
                val b = maxOf(n.s + n.len - 1, d.toS)
                (minOf(n.s, b)..maxOf(n.s, b)).map { Pair(it, n.midi) }.toSet()
            }
            "move" -> {
                val n = d.note ?: return@remember emptySet<Pair<Int, Int>>()
                (d.toS until d.toS + n.len).map { Pair(it, d.toMidi) }.toSet()
            }
            else -> {
                val a = minOf(d.fromS, d.toS)
                val b = maxOf(d.fromS, d.toS)
                (a..b).map { Pair(it, d.toMidi) }.toSet()
            }
        }
    }
    val selNote = ui.nebSel?.let { sel -> song.neb.notes.find { it.s == sel.s && it.midi == sel.midi } }
    val vexMode = ui.nebLineMode == "vex"
    PianoRoll(
        steps = steps, rows = rows,
        labelFor = { midiName(it) },
        labelColorFor = { Color(PITCH_COLORS[((it % 12) + 12) % 12]) },
        beatLen = bl, barLen = bar,
        playStep = ui.playStep, playing = ui.playing,
        selected = { s, m -> ui.nebSel?.s == s && ui.nebSel?.midi == m },
        cellKind = { s, m ->
            val n = coverOf[Pair(s, m)] ?: return@PianoRoll KIND_NONE
            var k = KIND_TAIL
            if (s == n.s) {
                k = KIND_HEAD
                if (n.vel > 1f) k = k or KIND_ACC else if (n.vel < 0.85f) k = k or KIND_SOFT
            }
            k
        },
        pitchColor = { Color(PITCH_COLORS[((it % 12) + 12) % 12]) },
        ghost = ghost,
        line = selNote?.let {
            LineOverlay(it.s, it.len, it.midi, if (vexMode) it.vex else it.bend, vexMode, !it.lineEdit, if (vexMode) it.vel else 0f)
        },
        specMode = false,
        onTap = { c ->
            val hit = coverOf[Pair(c.s, c.midi)]
            if (hit != null) vm.nebSelect(hit.s, hit.midi)
            else vm.nebAdd(c.s, c.midi, 1)
        },
        onDoubleTap = { c -> vm.nebDelete(c.s, c.midi) },
        onLongPress = { c -> vm.nebCycleVel(c.s, c.midi) },
        onLabelTap = { m -> vm.auditionNeb(m) },
        onDragStart = { c ->
            val cover = coverOf[Pair(c.s, c.midi)]
            if (cover == null) {
                drag = NebDrag("new", null, c.s, c.midi, c.s, c.midi, false)
                vm.auditionNeb(c.midi)
            } else {
                val zone = if (c.s > cover.s) "extend" else "move"
                drag = NebDrag(zone, cover, c.s, c.midi, c.s, c.midi, false)
            }
            true
        },
        onDragMove = { c ->
            drag?.let {
                if (c.s != it.toS || c.midi != it.toMidi) {
                    it.toS = c.s
                    it.toMidi = c.midi
                    it.moved = true
                    drag = it.copy()
                }
            }
        },
        onDragEnd = { moved ->
            val d = drag
            drag = null
            if (d == null) return@PianoRoll
            if (!moved) {
                if (d.mode == "new") vm.nebAdd(d.fromS, d.fromMidi, 1, false)
                else if (d.note != null) vm.nebSelect(d.note.s, d.note.midi)
                return@PianoRoll
            }
            when (d.mode) {
                "extend" -> if (d.note != null) vm.nebExtend(d.note, d.toS)
                "move" -> if (d.note != null) vm.nebMove(d.note, d.toS, d.toMidi)
                else -> {
                    val a = minOf(d.fromS, d.toS)
                    val b = maxOf(d.fromS, d.toS)
                    vm.nebAdd(a, d.toMidi, (b - a + 1).coerceIn(1, 16))
                }
            }
        },
        onLineTap = { t, v -> vm.nebAddLinePt(t, v) },
        onPointDown = { pointIdx = it },
        onPointMove = { t, v -> vm.nebMoveLinePt(pointIdx, t, v) },
        onPointUp = {},
        onPointDoubleTap = { idx -> vm.nebRemoveLinePt(idx) }
    )
    NebSelBar(vm)
    Hint("Melody strings · F1–D5 · tap empty = add · drag right = longer · tap note = select · double-tap = delete · long-press = soft/normal/accent. Line shows Bend or Vel automation for the selected note — switch Edit on to add/drag points.")
}

@Composable
private fun NebSelBar(vm: DawViewModel) {
    val ui = vm.collectUi()
    val n = ui.nebSel?.let { sel -> ui.song.neb.notes.find { it.s == sel.s && it.midi == sel.midi } } ?: return
    val vexMode = ui.nebLineMode == "vex"
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            midiName(n.midi) + " @ step " + (n.s + 1) + " · len " + n.len + " · " + velWord(n.vel) + " " +
                (n.vel * 100).toInt() + touchWord(n) + (if (n.pluck) "" else " · bow") +
                (if (n.bend.isNotEmpty()) " · bend " + n.bend.size else "") +
                (if (n.vex.isNotEmpty()) " · vline " + n.vex.size else ""),
            fontSize = 9.sp, color = SpectraText
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallBtn("Len −") { vm.nebSetLen(-1) }
            SmallBtn("Len +") { vm.nebSetLen(1) }
            SmallBtn("Vel: " + velWord(n.vel)) { vm.nebSetVel(velCycleBtn(n.vel)) }
            SmallBtn("Play") { vm.auditionNeb(n.midi, n.vel, n.pluck, n.bend, n.vex) }
            SmallBtn("Delete", { vm.nebDelete(n.s, n.midi) }, warn = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fine", fontSize = 9.sp, color = SpectraMuted)
            Slider(
                value = (n.vel * 100).toInt().toFloat(),
                onValueChange = { vm.nebSetVelLive(it / 100f) },
                onValueChangeFinished = { vm.nebSetVel(n.vel) },
                valueRange = 5f..115f, modifier = Modifier.weight(1f).height(30.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallBtn("Pluck: " + if (n.pluck) "on" else "off", { vm.nebTogglePluck() }, on = n.pluck)
            SmallBtn("Line: " + if (vexMode) "Vel" else "Bend") { vm.setNebLineMode(if (vexMode) "bend" else "vex") }
            SmallBtn("Edit: " + if (n.lineEdit) "on" else "off", { vm.nebToggleLineEdit() }, on = n.lineEdit)
            SmallBtn("Clear line") { vm.nebClearLine() }
        }
    }
}

private fun touchWord(n: NebNote): String {
    if (!n.pluck) return ""
    val v = n.vel.coerceIn(0.05f, 1.15f)
    val b1 = 0.05f + (1.15f - 0.05f) / 3f
    val b2 = 0.05f + 2f * (1.15f - 0.05f) / 3f
    val mm = when {
        v <= b1 -> 1f
        v >= b2 -> 0f
        else -> (b2 - v) / (b2 - b1)
    }
    return when {
        mm >= 0.999f -> " · palm"
        mm <= 0.001f -> " · pluck"
        else -> " · mute " + (mm * 100).toInt() + "%"
    }
}

private fun velCycleBtn(v: Float): Float = when {
    v > 1f -> 0.65f
    v < 0.85f -> 0.95f
    else -> 1.15f
}

@Composable
private fun NebFxPanel(vm: DawViewModel) {
    val ui = vm.collectUi()
    val fx = ui.song.neb.fx
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        FxCheckRow("Dly", fx.dlyOn, { vm.setNebFx(fx.copy(dlyOn = it)) }) {
            FxSlider("Amt", fx.dlyAmt * 100, 0f..100f) { vm.setNebFx(fx.copy(dlyAmt = it / 100)) }
            FxSlider("Time", fx.dlyTime * 100, 5f..150f) { vm.setNebFx(fx.copy(dlyTime = it / 100)) }
            FxSlider("Fdb", fx.dlyFb * 100, 0f..95f) { vm.setNebFx(fx.copy(dlyFb = it / 100)) }
        }
        FxCheckRow("Dst", fx.dstOn, { vm.setNebFx(fx.copy(dstOn = it)) }) {
            FxSlider("Amt", fx.dstAmt, 0f..100f) { vm.setNebFx(fx.copy(dstAmt = it)) }
            FxSlider("In", fx.dstIn * 100, 0f..300f) { vm.setNebFx(fx.copy(dstIn = it / 100)) }
            FxSlider("Out", fx.dstOut * 100, 0f..300f) { vm.setNebFx(fx.copy(dstOut = it / 100)) }
        }
        FxCheckRow("Verb", fx.verbOn, { vm.setNebFx(fx.copy(verbOn = it)) }) {
            FxSlider("Amt", fx.verbAmt * 100, 0f..100f) { vm.setNebFx(fx.copy(verbAmt = it / 100)) }
            FxSlider("Decay", fx.verbDecay, 0.3f..8f) { vm.setNebFx(fx.copy(verbDecay = it)) }
        }
    }
}
