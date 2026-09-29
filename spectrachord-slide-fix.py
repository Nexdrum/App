#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: spectrachord-slide-fix.py <project_dir>')

p = Path(sys.argv[1]) / 'app/src/main/java/com/sonolume/spectra/ui/Instruments.kt'
if not p.exists():
    raise SystemExit(f'missing {p}')

s = p.read_text(encoding='utf-8')

old = '''                              } else if (change.pressed && change.previousPressed && !hold) {
                                  val prev = pointerCells[id]
                                  if (!inside) {
                                      if (prev != null) stop(pointerBase(id))
                                      pointerCells.remove(id)
                                  } else if (prev != cell) {
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

new = '''                              } else if (change.pressed && change.previousPressed && !hold) {
                                  val prev = pointerCells[id]
                                  if (!inside) {
                                      if (prev != null) stop(pointerBase(id))
                                      pointerCells.remove(id)
                                  } else if (prev != cell && canPlay(cell)) {
                                      // A blacked-out cell does NOT interrupt a glide.
                                      // Keep the last playable chord sounding until the
                                      // pointer reaches another playable square.
                                      val base = pointerBase(id)
                                      if (prev != null) stop(base)
                                      start(cell, base)
                                      pointerCells[id] = cell
                                  }
'''

if old not in s:
    raise SystemExit('Spectrachord zoom-grid slide-handler anchor not found')

s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
print('Spectrachord blackout-slide behavior fixed')
