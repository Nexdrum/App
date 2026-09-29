#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: mobile-polish-fix.py <project-dir>')
root = Path(sys.argv[1]) / 'app/src/main/java/com/sonolume/spectra/ui'

# Smaller typography and tighter button padding so labels do not clip on phones.
p = root / 'Common.kt'
s = p.read_text()
s = s.replace('fontSize = 14.sp', 'fontSize = 12.sp')
s = s.replace('fontSize = 11.sp', 'fontSize = 9.sp')
s = s.replace('.padding(horizontal = 8.dp),\n        contentAlignment', '.padding(horizontal = 6.dp),\n        contentAlignment')
s = s.replace('Text(text, fontSize = 9.sp, color = SpectraText, maxLines = 1)',
              'Text(text, fontSize = 9.sp, color = SpectraText, maxLines = 1, softWrap = false)')
s = s.replace('Text(text, fontSize = 9.sp, color = SpectraMuted, modifier = Modifier.padding(8.dp))',
              'Text(text, fontSize = 9.sp, color = SpectraMuted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp))')
s = s.replace('Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {\n                ToggleBtn("Mute", mute)',
              'Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {\n                ToggleBtn("Mute", mute)')
p.write_text(s)

# Transport: keep the visualizer always visible, split oversized phone rows, and shrink labels.
p = root / 'Transport.kt'
s = p.read_text()
s = s.replace('fontSize = 13.sp', 'fontSize = 12.sp')
s = s.replace('fontSize = 11.sp', 'fontSize = 9.sp')
old_title = '        Text("SPECTRA DAW", fontWeight = FontWeight.Black, fontSize = 12.sp, color = SpectraText, letterSpacing = 2.sp)\n'
new_title = '''        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("SPECTRA DAW", fontWeight = FontWeight.Black, fontSize = 12.sp, color = SpectraText, letterSpacing = 1.5.sp)
            MixOrb(vm)
        }
'''
if old_title not in s:
    raise SystemExit('Transport.kt: title anchor not found')
s = s.replace(old_title, new_title, 1)
s = s.replace('            SmallBtn("Nexdrum", { vm.setView(2) }, on = ui.view == 2)\n            MixOrb(vm)\n',
              '            SmallBtn("Nexdrum", { vm.setView(2) }, on = ui.view == 2)\n', 1)
old_menu_buttons = '''            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallBtn("Demo song") { vm.demo() }
                SmallBtn("Clear all", { vm.clearAll() }, warn = true)
                SmallBtn("Save") { vm.saveNow() }
                SmallBtn("Load") { vm.loadNow() }
                SmallBtn("Export JSON") { onExport() }
                SmallBtn("Import JSON") { onImport() }
                SmallBtn("● Rec") { vm.setStatus("Native WAV recording is not enabled in this build yet.") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Play in full instruments:", fontSize = 9.sp, color = SpectraMuted)
                SmallBtn("Nebular") { vm.openInstrument("nebular") }
                SmallBtn("Nexdrum") { vm.openInstrument("nexdrum") }
                SmallBtn("Spectrachord") { vm.openInstrument("spectrachord") }
            }
'''
new_menu_buttons = '''            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
'''
if old_menu_buttons not in s:
    raise SystemExit('Transport.kt: menu button anchor not found')
s = s.replace(old_menu_buttons, new_menu_buttons, 1)
s = s.replace('Canvas(Modifier.size(34.dp))', 'Canvas(Modifier.size(30.dp))')
s = s.replace('import androidx.compose.ui.graphics.Color\n', 'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.drawscope.Stroke\n')
s = s.replace('    Canvas(Modifier.size(30.dp)) {\n        drawRect(Color.Black)\n', '''    Canvas(Modifier.size(30.dp)) {
        drawCircle(Color(0xFF172033), radius = size.minDimension / 2f, center = center)
        drawCircle(Color(0xFF52627F), radius = size.minDimension / 2f - 1f, center = center, style = Stroke(1.5f))
''')
old_mix = '''            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = song.metro, onCheckedChange = { vm.setMetro(it) })
                    Text("click", fontSize = 9.sp, color = SpectraMuted)
                }
                Text("Master", fontSize = 9.sp, color = SpectraMuted)
                Slider(value = ui.masterV * 100, onValueChange = { vm.setMasterV(it / 100) }, valueRange = 0f..100f, modifier = Modifier.weight(1f).height(30.dp))
                Text("Reverb", fontSize = 9.sp, color = SpectraMuted)
                Slider(value = ui.reverbV * 100, onValueChange = { vm.setReverbV(it / 100) }, valueRange = 0f..100f, modifier = Modifier.weight(1f).height(30.dp))
                Text("Delay", fontSize = 9.sp, color = SpectraMuted)
                Slider(value = ui.delayV * 100, onValueChange = { vm.setDelayV(it / 100) }, valueRange = 0f..100f, modifier = Modifier.weight(1f).height(30.dp))
            }
'''
new_mix = '''            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
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
'''
if old_mix not in s:
    raise SystemExit('Transport.kt: mixer row anchor not found')
s = s.replace(old_mix, new_mix, 1)
p.write_text(s)

# Nebular: move Decay out of the crowded TrackHead controls row so its slider is always visible.
p = root / 'NebScreen.kt'
s = p.read_text()
s = s.replace('fontSize = 11.sp', 'fontSize = 9.sp')
old_controls = '''    ) {
        VolSlider("Vol", song.neb.vol) { vm.setTrackVol("neb", it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Decay", fontSize = 9.sp, color = SpectraMuted)
            Slider(
                value = song.neb.decay, onValueChange = { vm.setNebDecay(it) },
                valueRange = 100f..3000f, modifier = Modifier.weight(1f).height(30.dp)
            )
        }
    }
    if (!ui.foldNeb && ui.fxNeb) NebFxPanel(vm)
'''
new_controls = '''    ) {
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
'''
if old_controls not in s:
    raise SystemExit('NebScreen.kt: decay controls anchor not found')
s = s.replace(old_controls, new_controls, 1)
p.write_text(s)

# Reduce remaining dense screen labels and selection-bar text.
for name in ('SpecScreen.kt', 'DrumScreen.kt'):
    p = root / name
    s = p.read_text().replace('fontSize = 11.sp', 'fontSize = 9.sp')
    p.write_text(s)

print('mobile polish applied')
