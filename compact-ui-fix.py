#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: compact-ui-fix.py <project-dir>')
root = Path(sys.argv[1]) / 'app/src/main/java/com/sonolume/spectra/ui'

# Compact shared controls to match the original mobile web UI instead of
# Material 3's large default button footprint.
p = root / 'Common.kt'
s = p.read_text()
s = s.replace('import androidx.compose.foundation.layout.Arrangement\n', '''import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.background\nimport androidx.compose.foundation.border\nimport androidx.compose.foundation.clickable\nimport androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.foundation.layout.Arrangement\n''')
s = s.replace('import androidx.compose.foundation.layout.Column\n', 'import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.Column\n')
s = s.replace('import androidx.compose.foundation.layout.fillMaxWidth\n', 'import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.height\n')
s = s.replace('import androidx.compose.material3.Button\nimport androidx.compose.material3.ButtonDefaults\n', '')
s = s.replace('import androidx.compose.ui.Alignment\n', 'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.draw.clip\n')
s = s.replace(
    '    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {\n        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {\n',
    '    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {\n        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {\n'
)
s = s.replace('fontSize = 16.sp', 'fontSize = 14.sp')
s = s.replace('fontSize = 12.sp', 'fontSize = 11.sp')
s = s.replace('Arrangement.spacedBy(6.dp)', 'Arrangement.spacedBy(4.dp)')
old_btn = '''@Composable\nfun SmallBtn(text: String, onClick: () -> Unit, warn: Boolean = false, on: Boolean = false) {\n    Button(\n        onClick = onClick,\n        colors = ButtonDefaults.buttonColors(\n            containerColor = when {\n                on -> SpectraGreen\n                warn -> Color(0xFF5A2323)\n                else -> Color(0xFF2A3248)\n            },\n            contentColor = SpectraText\n        )\n    ) {\n        Text(text, fontSize = 11.sp)\n    }\n}\n'''
new_btn = '''@Composable\nfun SmallBtn(text: String, onClick: () -> Unit, warn: Boolean = false, on: Boolean = false) {\n    val shape = RoundedCornerShape(10.dp)\n    val bg = when {\n        on -> Color(0xFF00AA66)\n        warn -> Color(0xFF5A2323)\n        else -> Color(0xFF2A3248)\n    }\n    val stroke = when {\n        on -> Color(0xFF00FF88)\n        warn -> Color(0xFFAA3333)\n        else -> Color(0xFF42506D)\n    }\n    Box(\n        modifier = Modifier\n            .height(30.dp)\n            .clip(shape)\n            .background(bg)\n            .border(BorderStroke(1.dp, stroke), shape)\n            .clickable(onClick = onClick)\n            .padding(horizontal = 8.dp),\n        contentAlignment = Alignment.Center\n    ) {\n        Text(text, fontSize = 11.sp, color = SpectraText, maxLines = 1)\n    }\n}\n'''
if old_btn not in s:
    raise SystemExit('Common.kt: SmallBtn block not found')
s = s.replace(old_btn, new_btn)
s = s.replace('modifier = Modifier.weight(1f)\n        )', 'modifier = Modifier.weight(1f).height(30.dp)\n        )')
p.write_text(s)

# Compact transport/header and sliders so all primary controls fit a phone.
p = root / 'Transport.kt'
s = p.read_text()
s = s.replace('import androidx.compose.foundation.layout.fillMaxWidth\n', 'import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.height\n')
s = s.replace('Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {', 'Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {')
s = s.replace('fontSize = 18.sp', 'fontSize = 13.sp')
s = s.replace('fontSize = 12.sp', 'fontSize = 11.sp')
s = s.replace('Arrangement.spacedBy(6.dp)', 'Arrangement.spacedBy(4.dp)')
s = s.replace('modifier = Modifier.weight(1f)', 'modifier = Modifier.weight(1f).height(30.dp)')
s = s.replace('Canvas(Modifier.size(48.dp))', 'Canvas(Modifier.size(34.dp))')
p.write_text(s)

# Make the per-track labels and editor sliders a little denser as well.
for name in ('NebScreen.kt', 'SpecScreen.kt', 'DrumScreen.kt'):
    p = root / name
    s = p.read_text()
    s = s.replace('import androidx.compose.foundation.layout.fillMaxWidth\n', 'import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.height\n')
    s = s.replace('fontSize = 12.sp', 'fontSize = 11.sp')
    s = s.replace('modifier = Modifier.weight(1f)\n', 'modifier = Modifier.weight(1f).height(30.dp)\n')
    p.write_text(s)

# Restore the original piano-roll proportions and the 15% pitch-class tint
# that the WebView version used on every empty grid cell.
p = root / 'PianoRoll.kt'
s = p.read_text()
s = s.replace('import androidx.compose.foundation.gestures.scrollBy\n', '')
s = s.replace('cellDp: Dp = 28.dp,\n    labelDp: Dp = 52.dp', 'cellDp: Dp = 20.dp,\n    labelDp: Dp = 44.dp')
s = s.replace('height = 22.dp + cellDp * rows.size', 'height = 16.dp + cellDp * rows.size')
s = s.replace('val headerH = 22.dp.toPx()', 'val headerH = 16.dp.toPx()')
s = s.replace('val barStyle = TextStyle(fontSize = 10.sp, color = Color(0x99FFFFFF))', 'val barStyle = TextStyle(fontSize = 9.sp, color = Color(0x99FFFFFF))')
row_old = '''        drawRect(Color(0xFF10141D), Offset(0f, y), Size(g.labelW, g.cell))\n        val layout = measurer.measure(labelFor(midi), rowStyle.copy(color = labelColorFor(midi)))\n'''
row_new = '''        drawRect(Color(0xFF10141D), Offset(0f, y), Size(g.labelW, g.cell))\n        drawRect(\n            labelColorFor(midi).copy(alpha = 0.15f),\n            Offset(g.labelW, y),\n            Size(g.width - g.labelW, g.cell)\n        )\n        val layout = measurer.measure(labelFor(midi), rowStyle.copy(color = labelColorFor(midi)))\n'''
if row_old not in s:
    raise SystemExit('PianoRoll.kt: row background anchor not found')
s = s.replace(row_old, row_new)
line_old = '''    for (s in 0 until g.steps) {\n        val x = g.labelW + s * g.cell\n        if (s % barLen == 0) {\n'''
line_new = '''    for (s in 0 until g.steps) {\n        val x = g.labelW + s * g.cell\n        drawRect(Color(0x0DFFFFFF), Offset(x, g.headerH), Size(1f, g.height - g.headerH))\n        if (s % barLen == 0) {\n'''
if line_old not in s:
    raise SystemExit('PianoRoll.kt: step line anchor not found')
s = s.replace(line_old, line_new)
s = s.replace('drawRect(Color(0x2E78C8FF)', 'drawRect(Color(0x1FFFFFFF)')
s = s.replace('drawRect(Color(0x59FFFFFF), Offset(rect.left + 2, rect.top + 2), Size(rect.width - 4, rect.height - 4))', 'drawRect(Color(0x737CFFBE), Offset(rect.left + 1, rect.top + 1), Size(rect.width - 2, rect.height - 2))')
s = s.replace('drawRect(Color.White, Offset(rect.left, rect.top), Size(rect.width, rect.height), style = Stroke(2f))', 'drawRect(Color(0xFF7DFFBE), Offset(rect.left, rect.top), Size(rect.width, rect.height), style = Stroke(2f))')
s = s.replace('kind and KIND_HEAD != 0 -> if (kind and KIND_SOFT != 0) 0.55f else 0.85f\n            else -> 0.3f', 'kind and KIND_HEAD != 0 -> if (kind and KIND_SOFT != 0) 0.55f else 1f\n            else -> 0.42f')
s = s.replace('drawRect(Color.White, Offset(rect.left, rect.top), Size(rect.width, rect.height), style = Stroke(2.5f))', 'drawRect(Color(0xFF7DFFBE), Offset(rect.left, rect.top), Size(rect.width, rect.height), style = Stroke(2.5f))')
p.write_text(s)
