#!/usr/bin/env python3
from pathlib import Path
import re, sys

if len(sys.argv) != 2:
    raise SystemExit('usage: layout-behavior-fix.py <project-dir>')
base = Path(sys.argv[1]) / 'app/src/main/java/com/sonolume/spectra'
ui = base / 'ui'

# Keep the transport/menu pinned at the top while the selected track body scrolls.
p = base / 'MainActivity.kt'
s = p.read_text()
s = s.replace('import androidx.compose.foundation.layout.fillMaxSize\n',
              'import androidx.compose.foundation.layout.fillMaxSize\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\n')
old = '''@Composable
private fun DawApp(vm: DawViewModel, onExport: () -> Unit, onImport: () -> Unit) {
    val ui = vm.collectUi()
    Column(Modifier.fillMaxSize()) {
        Transport(vm, onExport, onImport)
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                when (ui.view) {
                    1 -> SpecScreen(vm)
                    2 -> DrumScreen(vm)
                    else -> NebScreen(vm)
                }
            }
        }
    }
    ui.instrument?.let { InstrumentDialog(vm, it) }
}
'''
new = '''@Composable
private fun DawApp(vm: DawViewModel, onExport: () -> Unit, onImport: () -> Unit) {
    val ui = vm.collectUi()
    val bodyScroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Transport(vm, onExport, onImport)
        Column(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .verticalScroll(bodyScroll)
        ) {
            when (ui.view) {
                1 -> SpecScreen(vm)
                2 -> DrumScreen(vm)
                else -> NebScreen(vm)
            }
        }
    }
    ui.instrument?.let { InstrumentDialog(vm, it) }
}
'''
if old not in s:
    raise SystemExit('MainActivity.kt: DawApp anchor not found')
p.write_text(s.replace(old, new, 1))

# Track fold buttons hide only the control menu, exactly like the original .thead fold.
for name, fold, setter, fxflag, panel in (
    ('NebScreen.kt', 'ui.foldNeb', 'vm.setFoldNeb', 'ui.fxNeb', 'NebFxPanel(vm)'),
    ('SpecScreen.kt', 'ui.foldSpec', 'vm.setFoldSpec', 'ui.fxSpec', 'SpecFxPanel(vm)'),
    ('DrumScreen.kt', 'ui.foldDrums', 'vm.setFoldDrums', 'ui.fxDrums', None),
):
    p = ui / name
    s = p.read_text()
    s2 = re.sub(
        r'\n    if \(' + re.escape(fold) + r'\) \{\n        TrackHead\([^\n]+\) \{\}\n        return\n    \}\n',
        '\n', s, count=1
    )
    if s2 == s:
        raise SystemExit(f'{name}: folded early-return block not found')
    s = s2
    old_fold = f'false, {{ {setter}(true) }},'
    if old_fold not in s:
        raise SystemExit(f'{name}: TrackHead fold anchor not found')
    s = s.replace(old_fold, f'{fold}, {{ {setter}(!{fold}) }},', 1)
    if panel:
        old_fx = f'if ({fxflag}) {panel}'
        if old_fx not in s:
            raise SystemExit(f'{name}: FX anchor not found')
        s = s.replace(old_fx, f'if (!{fold} && {fxflag}) {panel}', 1)
    else:
        old_fx = f'if ({fxflag}) {{'
        if old_fx not in s:
            raise SystemExit(f'{name}: FX anchor not found')
        s = s.replace(old_fx, f'if (!{fold} && {fxflag}) {{', 1)
    p.write_text(s)

# Restore the old gridScroll behavior: bounded grid, sticky piano labels and sticky measures.
p = ui / 'PianoRoll.kt'
s = p.read_text()
s = s.replace('import androidx.compose.foundation.layout.Box\n',
              'import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.heightIn\n')
old_box = '''    Box(
        modifier
            .horizontalScroll(hScroll)
            .verticalScroll(vScroll)
    ) {
'''
new_box = '''    Box(
        modifier
            .fillMaxWidth()
            .heightIn(max = 438.dp)
            .horizontalScroll(hScroll)
            .verticalScroll(vScroll)
    ) {
'''
if old_box not in s:
    raise SystemExit('PianoRoll.kt: scroll box anchor not found')
s = s.replace(old_box, new_box, 1)

old_tail = '''            if (line != null) drawLineOverlay(g, line)
        }
    }
}
'''
new_tail = '''            if (line != null) drawLineOverlay(g, line)
            drawStickyChrome(
                measurer = measurer,
                g = g,
                labelFor = labelFor,
                labelColorFor = labelColorFor,
                beatLen = beatLen,
                barLen = barLen,
                scrollX = hScroll.value.toFloat(),
                scrollY = vScroll.value.toFloat()
            )
        }
    }
}
'''
if old_tail not in s:
    raise SystemExit('PianoRoll.kt: canvas tail anchor not found')
s = s.replace(old_tail, new_tail, 1)

old_hit = '''        if (pos.x < labelW && pos.y >= headerH) {
            val r = ((pos.y - headerH) / cell).toInt()
'''
new_hit = '''        val scrollX = hScroll.value.toFloat()
        val scrollY = vScroll.value.toFloat()
        if (pos.y >= scrollY && pos.y < scrollY + headerH) return@awaitEachGesture
        if (pos.x >= scrollX && pos.x < scrollX + labelW && pos.y >= scrollY + headerH) {
            val r = ((pos.y - headerH) / cell).toInt()
'''
if old_hit not in s:
    raise SystemExit('PianoRoll.kt: sticky label hit anchor not found')
s = s.replace(old_hit, new_hit, 1)

anchor = 'private fun DrawScope.drawRollBase(\n'
helper = '''private fun DrawScope.drawStickyChrome(
    measurer: TextMeasurer,
    g: RollGeometry,
    labelFor: (Int) -> String,
    labelColorFor: (Int) -> Color,
    beatLen: Int,
    barLen: Int,
    scrollX: Float,
    scrollY: Float
) {
    val headerY = scrollY
    val labelX = scrollX

    drawRect(Color(0xFF0D1119), Offset(scrollX, headerY), Size(size.width, g.headerH))
    val barStyle = TextStyle(fontSize = 9.sp, color = Color(0x99FFFFFF))
    var b = 0
    while (b * barLen < g.steps) {
        val s0 = b * barLen
        val span = minOf(barLen, g.steps - s0)
        val x = g.labelW + s0 * g.cell
        drawRect(Color(0xFF1A2233), Offset(x, headerY), Size(span * g.cell, g.headerH))
        val layout = measurer.measure((b + 1).toString(), barStyle)
        drawText(layout, topLeft = Offset(x + 4f, headerY + (g.headerH - layout.size.height) / 2f))
        b++
    }
    for (step in 0 until g.steps) {
        val x = g.labelW + step * g.cell
        if (step % barLen == 0) {
            drawLine(Color(0xFF3A4A6B), Offset(x, headerY), Offset(x, headerY + g.headerH), 1.5f)
        } else if (step % beatLen == 0) {
            drawLine(Color(0xFF232D42), Offset(x, headerY), Offset(x, headerY + g.headerH), 1f)
        }
    }

    val rowStyle = TextStyle(fontSize = 10.sp)
    for (r in g.rows.indices) {
        val midi = g.rows[r]
        val y = g.headerH + r * g.cell
        drawRect(Color(0xFF10141D), Offset(labelX, y), Size(g.labelW, g.cell))
        val layout = measurer.measure(labelFor(midi), rowStyle.copy(color = labelColorFor(midi)))
        drawText(layout, topLeft = Offset(labelX + g.labelW - layout.size.width - 4f, y + (g.cell - layout.size.height) / 2f))
        drawLine(Color(0xFF263048), Offset(labelX, y + g.cell - 1f), Offset(labelX + g.labelW, y + g.cell - 1f), 1f)
    }

    drawRect(Color(0xFF0D1119), Offset(labelX, headerY), Size(g.labelW, g.headerH))
    drawLine(Color(0xFF263048), Offset(labelX, headerY + g.headerH - 1f), Offset(labelX + g.labelW, headerY + g.headerH - 1f), 1f)
    drawLine(Color(0xFF222A3D), Offset(labelX + g.labelW - 1f, headerY), Offset(labelX + g.labelW - 1f, headerY + g.headerH), 1f)
}

'''
if anchor not in s:
    raise SystemExit('PianoRoll.kt: drawRollBase anchor not found')
s = s.replace(anchor, helper + anchor, 1)
p.write_text(s)
