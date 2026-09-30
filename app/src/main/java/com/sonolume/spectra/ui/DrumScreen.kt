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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonolume.spectra.daw.CYMS
import com.sonolume.spectra.daw.DawViewModel
import com.sonolume.spectra.daw.KICKS
import com.sonolume.spectra.daw.SNARES
import com.sonolume.spectra.daw.barLen
import com.sonolume.spectra.daw.beatLen
import com.sonolume.spectra.daw.gridSteps
import com.sonolume.spectra.daw.drumKind
import com.sonolume.spectra.daw.drumKindLetter
import com.sonolume.spectra.daw.drumZoneName
import com.sonolume.spectra.daw.midiName
import com.sonolume.spectra.daw.PITCH_COLORS
import com.sonolume.spectra.daw.velWord

@Composable
fun DrumScreen(vm: DawViewModel) {
    val ui = vm.collectUi()
    val song = ui.song
    val rows = remember { CYMS.reversed() + SNARES.reversed() + KICKS.reversed() }
    val steps = song.gridSteps()
    val bl = beatLen(song.bottom)
    val bar = barLen(song.top, song.bottom)
    val hitSet = remember(song.drums.hits) {
        song.drums.hits.associateBy { Pair(it.s, it.midi) }
    }
    TrackHead(
        "● Nexdrum", "pitched kicks · snares · cymbals", ui.foldDrums, { vm.setFoldDrums(!ui.foldDrums) },
        { vm.openInstrument("nexdrum") }, { vm.drumClear() },
        ui.fxDrums, { vm.setFxDrums(!ui.fxDrums) },
        song.drums.mute, song.drums.solo,
        { vm.setMute("drums", !song.drums.mute) }, { vm.setSolo("drums", !song.drums.solo) }
    ) {
        VolSlider("Vol", song.drums.vol) { vm.setTrackVol("drums", it) }
    }
    if (!ui.foldDrums && ui.fxDrums) {
        val fx = song.drums.fx
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            FxCheckRow("Verb", fx.verbOn, { vm.setDrumFx(fx.copy(verbOn = it)) }) {
                FxSlider("Amt", fx.verbAmt * 100, 0f..100f) { vm.setDrumFx(fx.copy(verbAmt = it / 100)) }
            }
        }
    }
    PianoRoll(
        steps = steps, rows = rows,
        labelFor = { drumKindLetter(it) + " " + midiName(it).replace("#", "♯") },
        labelColorFor = { Color(PITCH_COLORS[((it % 12) + 12) % 12]) },
        beatLen = bl, barLen = bar,
        playStep = ui.playStep, playing = ui.playing,
        selected = { s, m -> ui.drumSel?.s == s && ui.drumSel?.midi == m },
        cellKind = { s, m ->
            val h = hitSet[Pair(s, m)] ?: return@PianoRoll KIND_NONE
            var k = KIND_HEAD
            if (h.vel > 1f) k = k or KIND_ACC else if (h.vel < 0.85f) k = k or KIND_SOFT
            k
        },
        pitchColor = { Color(PITCH_COLORS[((it % 12) + 12) % 12]) },
        ghost = emptySet(),
        line = null,
        specMode = false,
        onTap = { c -> vm.drumToggle(c.s, c.midi) },
        onDoubleTap = { c -> vm.drumDelete(c.s, c.midi) },
        onLongPress = { c -> vm.drumCycleVel(c.s, c.midi) },
        onLabelTap = { m -> vm.auditionDrum(m, song.drums.lastVel) },
        onDragStart = { false },
        onDragMove = {},
        onDragEnd = {},
        onLineTap = { _, _ -> },
        onPointDown = {},
        onPointMove = { _, _ -> },
        onPointUp = {},
        onPointDoubleTap = {}
    )
    DrumSelBar(vm)
    Hint("Drums · 7 kicks (E2–A♯2) · 7 snares (B2–F3) · 7 cymbals (F♯3–C4). Tap a hit to select it; tap it again to remove it. Long-press = soft/normal/accent. Velocity picks the strike zone: cymbals bell / bow / edge, snare edge / middle / center, kick heel / middle / toe.")
}

@Composable
private fun DrumSelBar(vm: DawViewModel) {
    val ui = vm.collectUi()
    val h = vm.drumSelHit() ?: return
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            drumKindLetter(h.midi) + " " + midiName(h.midi).replace("#", "♯") +
                " @ step " + (h.s + 1) + " · " + velWord(h.vel) + " " + (h.vel * 100).toInt() +
                " (" + drumZoneName(h.midi, h.vel) + ")",
            fontSize = 9.sp, color = SpectraText
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallBtn("Vel") { vm.drumSetVel(velCycleBtn(h.vel)) }
            SmallBtn("Play") { vm.auditionDrum(h.midi, h.vel) }
            SmallBtn("Delete", { vm.drumDelete(h.s, h.midi) }, warn = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fine", fontSize = 9.sp, color = SpectraMuted)
            Slider(
                value = (h.vel * 100).toInt().toFloat(),
                onValueChange = { vm.drumSetVelLive(it / 100f) },
                onValueChangeFinished = { vm.drumSetVel(h.vel) },
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
