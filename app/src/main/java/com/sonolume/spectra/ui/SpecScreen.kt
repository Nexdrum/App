package com.sonolume.spectra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import com.sonolume.spectra.daw.SPEC_HI
import com.sonolume.spectra.daw.SPEC_LO
import com.sonolume.spectra.daw.SpecNote
import com.sonolume.spectra.daw.barLen
import com.sonolume.spectra.daw.beatLen
import com.sonolume.spectra.daw.gridSteps
import com.sonolume.spectra.daw.midiName
import com.sonolume.spectra.daw.PITCH_COLORS
import com.sonolume.spectra.daw.velWord

private data class SpecDrag(
    val mode: String,
    val note: SpecNote?,
    val half: Int,
    val fromS: Int,
    val fromMidi: Int,
    var toS: Int,
    var toMidi: Int,
    var moved: Boolean,
    val isEnd: Boolean,
    val anchorLen: Int
)

private val WAVES = listOf("sine", "square", "sawtooth", "triangle")

@Composable
fun SpecScreen(vm: DawViewModel) {
    val ui = vm.collectUi()
    val song = ui.song
    var drag by remember { mutableStateOf<SpecDrag?>(null) }
    val rows = remember { (SPEC_HI downTo SPEC_LO).toList() }
    val steps = song.gridSteps()
    val bl = beatLen(song.bottom)
    val bar = barLen(song.top, song.bottom)
    TrackHead(
        "❖ Spectrachord", "dual-oscillator chords", ui.foldSpec, { vm.setFoldSpec(!ui.foldSpec) },
        { vm.openInstrument("spectrachord") }, { vm.specClear() },
        ui.fxSpec, { vm.setFxSpec(!ui.fxSpec) },
        song.spec.mute, song.spec.solo,
        { vm.setMute("spec", !song.spec.mute) }, { vm.setSolo("spec", !song.spec.solo) }
    ) {
        VolSlider("Vol", song.spec.vol) { vm.setTrackVol("spec", it) }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WaveSelect("Osc1", song.spec.w1) { vm.setSpecWave(0, it) }
        WaveSelect("Osc2", song.spec.w2) { vm.setSpecWave(1, it) }
    }
    if (!ui.foldSpec && ui.fxSpec) SpecFxPanel(vm)
    val ghost = remember(drag) {
        val d = drag
        if (d == null || !d.moved) emptySet()
        else if (d.mode == "new") {
            val a = minOf(d.fromS, d.toS)
            val b = maxOf(d.fromS, d.toS)
            (a..b).map { Pair(it, d.toMidi) }.toSet()
        } else {
            val n = d.note ?: return@remember emptySet<Pair<Int, Int>>()
            val a = n.s
            val b = maxOf(n.s + n.len - 1, d.toS)
            (minOf(a, b)..maxOf(a, b)).map { Pair(it, d.toMidi) }.toSet()
        }
    }
    PianoRoll(
        steps = steps, rows = rows,
        labelFor = { midiName(it) },
        labelColorFor = { Color(PITCH_COLORS[((it % 12) + 12) % 12]) },
        beatLen = bl, barLen = bar,
        playStep = ui.playStep, playing = ui.playing,
        selected = { s, m ->
            val sel = ui.specSel ?: return@PianoRoll false
            val n = song.spec.notes.find { it.s == sel.s && (it.midi1 == sel.midi || it.midi2 == sel.midi) }
            n != null && s >= n.s && s < n.s + n.len && (n.midi1 == m || n.midi2 == m)
        },
        cellKind = { s, m ->
            val n = song.spec.notes.find { (it.midi1 == m || it.midi2 == m) && s >= it.s && s < it.s + it.len }
                ?: return@PianoRoll KIND_NONE
            var k = 0
            if (n.midi1 == m) k = k or KIND_HALF1
            if (n.midi2 == m) k = k or KIND_HALF2
            if (s == n.s) {
                k = k or KIND_HEAD
                if (n.vel > 1f) k = k or KIND_ACC else if (n.vel < 0.85f) k = k or KIND_SOFT
            }
            k
        },
        pitchColor = { Color(PITCH_COLORS[((it % 12) + 12) % 12]) },
        ghost = ghost,
        line = null,
        specMode = true,
        halfMode = true,
        onTap = { c ->
            val hit = vm.specCover(c.s, c.midi)
            if (hit != null) vm.specSelect(hit.s, if (c.half == 0) hit.midi1 else hit.midi2)
            else vm.specAdd(c.s, c.midi, 1)
        },
        onDoubleTap = { c -> vm.specDelete(c.s, c.midi) },
        onLongPress = { c -> vm.specCycleVel(c.s, c.midi) },
        onLabelTap = { m -> vm.auditionSpec(m) },
        onDragStart = { c ->
            val hit = vm.specCover(c.s, c.midi)
            if (hit == null) {
                drag = SpecDrag("new", null, c.half.coerceAtLeast(0), c.s, c.midi, c.s, c.midi, false, false, 1)
                vm.auditionSpec(c.midi)
            } else {
                val half = if (hit.midi1 == c.midi && hit.midi2 == c.midi) c.half.coerceAtLeast(0)
                else if (hit.midi1 == c.midi) 0 else 1
                val endS = hit.s + hit.len - 1
                drag = SpecDrag("tweak", hit, half, c.s, c.midi, c.s, c.midi, false, c.s == endS, hit.len)
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
                if (d.mode == "new") vm.specAdd(d.fromS, d.fromMidi, 1, false)
                else if (d.note != null) {
                    val n = d.note
                    vm.specSelect(n.s, if (d.half == 0) n.midi1 else n.midi2)
                }
                return@PianoRoll
            }
            if (d.mode == "new") {
                val a = minOf(d.fromS, d.toS)
                val b = maxOf(d.fromS, d.toS)
                vm.specAdd(a, d.toMidi, (b - a + 1).coerceIn(1, 16))
                return@PianoRoll
            }
            val n = d.note ?: return@PianoRoll
            if (d.isEnd || (d.anchorLen == 1 && d.toS > d.fromS && d.toMidi == d.fromMidi)) {
                vm.specExtendRetune(n, d.half, d.toMidi, d.toS)
            } else {
                vm.specMoveRetune(n, d.half, d.toMidi, d.toS)
            }
        },
        onLineTap = { _, _ -> },
        onPointDown = {},
        onPointMove = { _, _ -> },
        onPointUp = {},
        onPointDoubleTap = {}
    )
    SpecSelBar(vm)
    Hint("Dual notes · D1–D7 · each note splits in two: top half = osc 1, bottom half = osc 2. Drag a half up/down to retune it, drag its end right to hold it longer, drag the body sideways to move both. Double-tap = delete · long-press = soft/normal/accent.")
}

@Composable
private fun SpecSelBar(vm: DawViewModel) {
    val ui = vm.collectUi()
    val n = vm.specSelNote() ?: return
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "O1 " + midiName(n.midi1) + " | O2 " + midiName(n.midi2) + " ×" + n.len +
                " @ step " + (n.s + 1) + " · " + velWord(n.vel) + " " + (n.vel * 100).toInt(),
            fontSize = 9.sp, color = SpectraText
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallBtn("Len −") { vm.specSetLen(-1) }
            SmallBtn("Len +") { vm.specSetLen(1) }
            SmallBtn("Vel") { vm.specSetVel(velCycleBtn(n.vel)) }
            SmallBtn("Play") { vm.auditionSpec(n.midi1, n.vel); vm.auditionSpec(n.midi2, n.vel) }
            SmallBtn("Delete", { vm.specDelete(n.s, n.midi1) }, warn = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fine", fontSize = 9.sp, color = SpectraMuted)
            Slider(
                value = (n.vel * 100).toInt().toFloat(),
                onValueChange = { vm.specSetVelLive(it / 100f) },
                onValueChangeFinished = { vm.specSetVel(n.vel) },
                valueRange = 5f..115f, modifier = Modifier.weight(1f).height(30.dp)
            )
        }
    }
}

private fun velCycleBtn(v: Float): Float = when {
    v > 1f -> 0.65f
    v < 0.85f -> 0.95f
    else -> 1.15f
}

@Composable
private fun WaveSelect(label: String, value: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 9.sp, color = SpectraMuted)
        androidx.compose.foundation.layout.Box {
            SmallBtn(value) { open = true }
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (w in WAVES) {
                    DropdownMenuItem(text = { Text(w) }, onClick = { onPick(w); open = false })
                }
            }
        }
    }
}

@Composable
private fun SpecFxPanel(vm: DawViewModel) {
    val ui = vm.collectUi()
    val fx = ui.song.spec.fx
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        FxCheckRow("Dly", fx.dlyOn, { vm.setSpecFx(fx.copy(dlyOn = it)) }) {
            FxSlider("Amt", fx.dlyAmt * 100, 0f..100f) { vm.setSpecFx(fx.copy(dlyAmt = it / 100)) }
            FxSlider("Time", fx.dlyTime * 100, 5f..150f) { vm.setSpecFx(fx.copy(dlyTime = it / 100)) }
            FxSlider("Fdb", fx.dlyFb * 100, 0f..95f) { vm.setSpecFx(fx.copy(dlyFb = it / 100)) }
        }
        FxCheckRow("Verb", fx.verbOn, { vm.setSpecFx(fx.copy(verbOn = it)) }) {
            FxSlider("Amt", fx.verbAmt * 100, 0f..100f) { vm.setSpecFx(fx.copy(verbAmt = it / 100)) }
            FxSlider("Room", fx.verbRoom * 100, 10f..100f) { vm.setSpecFx(fx.copy(verbRoom = it / 100)) }
        }
    }
}
