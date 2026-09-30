package com.sonolume.spectra.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonolume.spectra.daw.DawViewModel
import com.sonolume.spectra.daw.PITCH_COLORS

@Composable
fun Transport(
    vm: DawViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val ui = vm.collectUi()
    val song = ui.song
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("SPECTRA DAW", fontWeight = FontWeight.Black, fontSize = 12.sp, color = SpectraText, letterSpacing = 1.5.sp)
            MixOrb(vm)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallBtn(if (ui.playing) "⏸" else "▶", { if (ui.playing) vm.stop() else vm.play() }, on = ui.playing)
            SmallBtn("■") { vm.stop() }
            ToggleBtn("Loop", song.loop) { vm.toggleLoop() }
            Text("BPM", fontSize = 9.sp, color = SpectraMuted)
            Slider(
                value = song.bpm.toFloat(), onValueChange = { vm.setBpm(it.toInt()) },
                valueRange = 12f..240f, modifier = Modifier.weight(1f).height(30.dp)
            )
            Text(song.bpm.toString(), fontSize = 9.sp, color = SpectraText)
            SmallBtn("Tap") { vm.tapTempo() }
            TsSelect("TS", song.top) { vm.setTs(it, song.bottom) }
            Text("/", fontSize = 9.sp, color = SpectraMuted)
            TsSelect("", song.bottom) { vm.setTs(song.top, it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallBtn(if (ui.menuOpen) "▴ Menu" else "▾ Menu") { vm.setMenu(!ui.menuOpen) }
            Text("View:", fontSize = 9.sp, color = SpectraMuted)
            SmallBtn("Nebular", { vm.setView(0) }, on = ui.view == 0)
            SmallBtn("Spectrachord", { vm.setView(1) }, on = ui.view == 1)
            SmallBtn("Nexdrum", { vm.setView(2) }, on = ui.view == 2)
        }
        if (ui.menuOpen) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = song.metro, onCheckedChange = { vm.setMetro(it) })
                    Text("click", fontSize = 9.sp, color = SpectraMuted)
                }
                Text("Master", fontSize = 9.sp, color = SpectraMuted)
                Slider(value = ui.masterV * 100, onValueChange = { vm.setMasterV(it / 100) }, valueRange = 0f..100f, modifier = Modifier.weight(1f).height(30.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Reverb", fontSize = 9.sp, color = SpectraMuted)
                Slider(value = ui.reverbV * 100, onValueChange = { vm.setReverbV(it / 100) }, valueRange = 0f..100f, modifier = Modifier.weight(1f).height(30.dp))
                Text("Delay", fontSize = 9.sp, color = SpectraMuted)
                Slider(value = ui.delayV * 100, onValueChange = { vm.setDelayV(it / 100) }, valueRange = 0f..100f, modifier = Modifier.weight(1f).height(30.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallBtn("Demo") { vm.demo() }
                SmallBtn("Clear all", { vm.clearAll() }, warn = true)
                SmallBtn("Save") { vm.saveNow() }
                SmallBtn("Load") { vm.loadNow() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallBtn("Export") { onExport() }
                SmallBtn("Import") { onImport() }
                SmallBtn("● Rec") { vm.setStatus("Native WAV recording is not enabled in this build yet.") }
            }
            Text("Play in full instruments:", fontSize = 9.sp, color = SpectraMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallBtn("Nebular") { vm.openInstrument("nebular") }
                SmallBtn("Nexdrum") { vm.openInstrument("nexdrum") }
                SmallBtn("Spectrachord") { vm.openInstrument("spectrachord") }
            }
        }
        if (ui.status.isNotEmpty()) {
            Text(ui.status, fontSize = 9.sp, color = SpectraMuted)
        }
    }
}

@Composable
private fun TsSelect(label: String, value: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label.isNotEmpty()) Text(label, fontSize = 9.sp, color = SpectraMuted)
        androidx.compose.foundation.layout.Box {
            SmallBtn(value.toString()) { open = true }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (i in 1..24) {
                    DropdownMenuItem(text = { Text(i.toString()) }, onClick = { onPick(i); open = false })
                }
            }
        }
    }
}

@Composable
private fun MixOrb(vm: DawViewModel) {
    val ui = vm.collectUi()
    var tick by remember { mutableStateOf(0) }
    if (ui.flashes.isNotEmpty()) {
        androidx.compose.runtime.LaunchedEffect(ui.flashes.size) {
            kotlinx.coroutines.delay(100)
            tick++
        }
    }
    if (tick < -1000000) return
    val now = System.currentTimeMillis()
    Canvas(Modifier.size(30.dp)) {
        drawCircle(Color(0xFF172033), radius = size.minDimension / 2f, center = center)
        drawCircle(Color(0xFF52627F), radius = size.minDimension / 2f - 1f, center = center, style = Stroke(1.5f))
        for (f in ui.flashes) {
            val age = (now - f.at) / 700f
            if (age >= 1f) continue
            val a = (1f - age) * f.vel.coerceIn(0.05f, 1.15f)
            val c = Color(PITCH_COLORS[((f.midi % 12) + 12) % 12])
            drawCircle(
                Brush.radialGradient(
                    0f to c.copy(alpha = (a).coerceIn(0f, 1f)),
                    1f to Color.Transparent,
                    center = center,
                    radius = size.minDimension / 2f
                ),
                radius = size.minDimension / 2f,
                center = center
            )
        }
    }
}
