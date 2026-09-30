#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: native-nebular-compile-fix.py <project_dir>")

p = Path(sys.argv[1]) / "app/src/main/java/com/sonolume/spectra/ui/NativeNebular.kt"
if not p.exists():
    raise SystemExit(f"missing {p}")

s = p.read_text(encoding="utf-8")
bad = 'NebSlider("Delay decay", delayFb, 0f...95f, { "%.2f".format(it) }) { delayFb = it }'
good = 'NebSlider("Delay decay", delayFb, 0f..0.95f, { "%.2f".format(it) }) { delayFb = it }'

if bad not in s:
    raise SystemExit("Native Nebular delay-feedback slider typo anchor not found")

s = s.replace(bad, good, 1)
p.write_text(s, encoding="utf-8")
print("Native Nebular Kotlin compile typo fixed")
