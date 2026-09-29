#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: spectrachord-grid-fix.py <project_dir>')

project = Path(sys.argv[1])
html = project / 'app/src/main/assets/instruments/spectrachord.html'
if not html.exists():
    raise SystemExit(f'missing {html}')

s = html.read_text(encoding='utf-8')

old = "const audioCtx = new (window.AudioContext || window.webkitAudioContext)();"
new = r'''// The full instrument is rendered in a WebView but audio is native. Some
// Android WebView builds can expose WebAudio late (or reject construction),
// which used to abort this script before the 25x25 grid was created. Keep a
// tiny no-op compatibility context so the legacy UI code can always finish
// rendering; the native adapter below still handles all audible voices.
function makeSpectraCompatAudioContext() {
  try {
    const Ctor = window.AudioContext || window.webkitAudioContext;
    if (Ctor) return new Ctor();
  } catch (_) {}
  const param = () => ({
    value: 0,
    setValueAtTime(v) { this.value = v; },
    exponentialRampToValueAtTime(v) { this.value = v; }
  });
  const node = () => ({
    gain: param(), delayTime: param(), frequency: param(), type: 'sine', buffer: null,
    connect() { return this; }, disconnect() {}, start() {}, stop() {}
  });
  return {
    currentTime: 0,
    sampleRate: 48000,
    destination: node(),
    createGain: node,
    createDelay: node,
    createConvolver: node,
    createOscillator: node,
    createBuffer(channels, length) {
      const data = Array.from({length: Math.max(1, channels|0)}, () => new Float32Array(Math.max(1, length|0)));
      return { getChannelData(i) { return data[i] || data[0]; } };
    },
    resume() { return Promise.resolve(); }
  };
}
const audioCtx = makeSpectraCompatAudioContext();'''

if old not in s:
    raise SystemExit('Spectrachord AudioContext anchor not found')
s = s.replace(old, new, 1)

marker = "</style>\n<script>\n(function(){\nconst grid=document.getElementById('grid'),wrap=document.getElementById('gridWrapper'),menu=document.getElementById('topMenu');"
replacement = """#gridWrapper{min-height:120px !important;visibility:visible !important;opacity:1 !important;}\n#grid{visibility:visible !important;opacity:1 !important;}\n</style>\n<script>\n(function(){\nconst grid=document.getElementById('grid'),wrap=document.getElementById('gridWrapper'),menu=document.getElementById('topMenu');"""
if marker not in s:
    raise SystemExit('Spectrachord layout anchor not found')
s = s.replace(marker, replacement, 1)

html.write_text(s, encoding='utf-8')
print('spectrachord grid fix applied')
