#!/usr/bin/env python3
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: spectrachord-slide-fix.py <project_dir>')

p = Path(sys.argv[1]) / 'app/src/main/java/com/sonolume/spectra/ui/Instruments.kt'
if not p.exists():
    raise SystemExit(f'missing {p}')

s = p.read_text(encoding='utf-8')
start_marker = 'else if (change.pressed && change.previousPressed && !hold) {'
end_marker = 'else if (!change.pressed && change.previousPressed && !hold) {'
start = s.find(start_marker)
if start < 0:
    raise SystemExit('Spectrachord move handler not found')
end = s.find(end_marker, start)
if end < 0:
    raise SystemExit('Spectrachord move-handler end not found')

block = s[start:end]

if '} else if (prev != cell) {' not in block:
    raise SystemExit('Spectrachord playable-cell transition not found')
block = block.replace(
    '} else if (prev != cell) {',
    '} else if (prev != cell && canPlay(cell)) {',
    1,
)

pattern = re.compile(
    r'if\s*\(canPlay\(cell\)\)\s*\{\s*'
    r'start\(cell,\s*base\)\s*'
    r'pointerCells\[id\]\s*=\s*cell\s*'
    r'\}\s*else\s*\{\s*'
    r'pointerCells\.remove\(id\)\s*'
    r'\}',
    re.S,
)

replacement = '''// A blacked-out cell is transparent during a glide.\n                                      // Keep the last playable chord sounding until another\n                                      // playable square is reached.\n                                      start(cell, base)\n                                      pointerCells[id] = cell'''
block2, count = pattern.subn(replacement, block, count=1)
if count != 1:
    raise SystemExit('Spectrachord blackout branch not found')

s = s[:start] + block2 + s[end:]
p.write_text(s, encoding='utf-8')
print('Spectrachord blackout-slide behavior fixed')
