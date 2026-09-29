#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: spectrachord-slide-fix.py <project_dir>')

p = Path(sys.argv[1]) / 'app/src/main/java/com/sonolume/spectra/ui/Instruments.kt'
if not p.exists():
    raise SystemExit(f'missing {p}')

s = p.read_text(encoding='utf-8')

old = '''                                val prev = pointerCells[id]
                                if (prev != cell) {
                                    val base = pointerBase(id)
                                    if (prev != null) stop(base)
                                    if (canPlay(cell)) {
                                        start(cell, base)
                                        pointerCells[id] = cell
                                    } else {
                                        pointerCells.remove(id)
                                    }
                                }
'''

new = '''                                val prev = pointerCells[id]
                                // Blacked-out cells are transparent to a slide gesture.
                                // Keep the last playable chord sounding until the finger
                                // actually reaches another playable square. This prevents
                                // gaps when gliding across a filtered scale.
                                if (prev != cell && canPlay(cell)) {
                                    val base = pointerBase(id)
                                    if (prev != null) stop(base)
                                    start(cell, base)
                                    pointerCells[id] = cell
                                }
'''

if old not in s:
    raise SystemExit('Spectrachord slide-handler anchor not found; source layout changed')

s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
print('Spectrachord blackout-slide behavior fixed')
