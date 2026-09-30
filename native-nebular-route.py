#!/usr/bin/env python3
from pathlib import Path
import re, sys
if len(sys.argv) != 2: raise SystemExit('usage: native-nebular-route.py <project_dir>')
root=Path(sys.argv[1])
p=root/'app/src/main/java/com/sonolume/spectra/ui/Instruments.kt'
s=p.read_text(encoding='utf-8')
pat=r'(fun\s+InstrumentDialog\s*\(\s*vm\s*:\s*DawViewModel\s*,\s*name\s*:\s*String\s*\)\s*\{)'
m=re.search(pat,s)
if not m: raise SystemExit('InstrumentDialog function not found')
insert='''\n    if (name == "nebular") {\n        NativeNebularDialog(vm)\n        return\n    }'''
if 'NativeNebularDialog(vm)' not in s:
    s=s[:m.end()]+insert+s[m.end():]
p.write_text(s,encoding='utf-8')
print('native Nebular route installed')
