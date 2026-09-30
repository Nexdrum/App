package com.sonolume.spectra.ui

import android.content.Context
import android.graphics.Paint
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sonolume.spectra.NativeAudio
import com.sonolume.spectra.daw.Audio
import com.sonolume.spectra.daw.DawViewModel
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Legacy bridge retained temporarily for Nebular and Nexdrum while their control surfaces are migrated. */
private class InstrumentNativeBridge(private val context: Context) {
    private fun ensureAudio() = Audio.ensure(context)
    private fun f(o: JSONObject, key: String, fallback: Double = 0.0): Float =
        o.optDouble(key, fallback).toFloat()

    @JavascriptInterface
    fun postMessage(raw: String) {
        try {
            val o = JSONObject(raw)
            when (o.optString("type")) {
                "ensure" -> ensureAudio()
                "nebFx" -> {
                    ensureAudio()
                    val x = o.optJSONObject("fx") ?: JSONObject()
                    NativeAudio.setNebFx(
                        x.optBoolean("dlyOn"), f(x, "dlyAmt", .25), f(x, "dlyFb", .35), f(x, "dlyTime", .32),
                        x.optBoolean("dstOn"), f(x, "dstAmt", 25.0), f(x, "dstIn", 1.0), f(x, "dstOut", .85),
                        x.optBoolean("verbOn"), f(x, "verbAmt", .25), f(x, "verbDecay", 2.5)
                    )
                }
                "specFx" -> {
                    ensureAudio()
                    val x = o.optJSONObject("fx") ?: JSONObject()
                    NativeAudio.setSpecFx(
                        x.optBoolean("dlyOn"), f(x, "dlyAmt", .4), f(x, "dlyFb", .3), f(x, "dlyTime", .25),
                        x.optBoolean("verbOn"), f(x, "verbAmt", .3), f(x, "verbRoom", .5)
                    )
                }
                "drum" -> {
                    ensureAudio()
                    val kind = when (o.optString("kind")) { "k" -> 0; "s" -> 1; else -> 2 }
                    NativeAudio.drumHit(kind, f(o, "freq", 110.0), f(o, "vel", 1.0), f(o, "pos", .5), 0f)
                }
                "nebLiveStart" -> {
                    ensureAudio()
                    NativeAudio.nebLiveStart(
                        o.optInt("id"), o.optInt("midi", 60), f(o, "level", 1.0), f(o, "pressure", 1.0),
                        f(o, "mute", 0.0), f(o, "cents", 0.0), f(o, "volume", 1.0),
                        o.optBoolean("pluck"), f(o, "release", 700.0)
                    )
                }
                "nebLiveUpdate" -> NativeAudio.nebLiveUpdate(
                    o.optInt("id"), o.optInt("midi", 60), f(o, "level", 1.0), f(o, "pressure", 1.0),
                    f(o, "mute", 0.0), f(o, "cents", 0.0), f(o, "volume", 1.0)
                )
                "nebLiveStop" -> NativeAudio.nebLiveStop(o.optInt("id"), f(o, "release", 700.0))
                "specLiveStart" -> {
                    ensureAudio()
                    val wave = waveIndex(o.optString("wave"))
                    NativeAudio.specLiveStart(o.optInt("id"), o.optInt("midi", 60), f(o, "gain", .1), wave)
                }
                "specLiveStop" -> NativeAudio.specLiveStop(o.optInt("id"))
                "livePanic" -> NativeAudio.livePanic()
            }
        } catch (_: Throwable) {
        }
    }
}

private fun restoreDawAudio(vm: DawViewModel) {
    try {
        NativeAudio.livePanic()
        val ui = vm.ui.value
        Audio.applyAll(ui.song, ui.masterV)
    } catch (_: Throwable) {
    }
}

@Composable
fun InstrumentDialog(vm: DawViewModel, name: String) {
    if (name == "nexdrum") {
        NativeNexdrumDialog(vm)
        return
    }
    if (name == "nebular") {
        NativeNebularDialog(vm)
        return
    }
    Dialog(
        onDismissRequest = {
            restoreDawAudio(vm)
            vm.openInstrument(null)
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize().padding(12.dp), color = SpectraBg) {
            if (name == "spectrachord") {
                NativeSpectrachordInstrument(
                    onClose = {
                        restoreDawAudio(vm)
                        vm.openInstrument(null)
                    }
                )
            } else {
                LegacyInstrumentWebView(vm, name)
            }
        }
    }
}

@Composable
private fun LegacyInstrumentWebView(vm: DawViewModel, name: String) {
    Column(Modifier.fillMaxSize()) {
        SmallBtn("Close " + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }) {
            restoreDawAudio(vm)
            vm.openInstrument(null)
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    setBackgroundColor(android.graphics.Color.rgb(8, 9, 13))
                    addJavascriptInterface(InstrumentNativeBridge(ctx.applicationContext), "InstrumentNative")
                    webViewClient = WebViewClient()
                    loadUrl("file:///android_asset/instruments/$name.html")
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            onRelease = {
                try {
                    NativeAudio.livePanic()
                    it.stopLoading()
                    it.loadUrl("about:blank")
                    it.removeJavascriptInterface("InstrumentNative")
                    it.destroy()
                } catch (_: Throwable) {
                }
            }
        )
    }
}

private val SPEC_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
private val SPEC_COLORS = arrayOf(
    intArrayOf(40, 255, 0), intArrayOf(0, 255, 232), intArrayOf(0, 124, 255), intArrayOf(5, 0, 255),
    intArrayOf(69, 0, 234), intArrayOf(85, 0, 79), intArrayOf(140, 0, 0), intArrayOf(180, 10, 0),
    intArrayOf(230, 30, 0), intArrayOf(255, 50, 10), intArrayOf(255, 236, 0), intArrayOf(153, 255, 0)
)
private val SPEC_INTERVAL_BRIGHTNESS = arrayOf(
    intArrayOf(220, 255), intArrayOf(30, 70), intArrayOf(70, 110), intArrayOf(100, 130),
    intArrayOf(110, 150), intArrayOf(140, 180), intArrayOf(60, 90), intArrayOf(200, 240),
    intArrayOf(100, 130), intArrayOf(110, 150), intArrayOf(80, 120), intArrayOf(60, 100)
)
private val SPEC_INTERVAL_SAT = arrayOf(
    floatArrayOf(.90f, 1f), floatArrayOf(.20f, .35f), floatArrayOf(.30f, .50f), floatArrayOf(.40f, .60f),
    floatArrayOf(.45f, .65f), floatArrayOf(.55f, .75f), floatArrayOf(.25f, .40f), floatArrayOf(.85f, 1f),
    floatArrayOf(.45f, .65f), floatArrayOf(.50f, .70f), floatArrayOf(.35f, .55f), floatArrayOf(.30f, .50f)
)

private fun waveIndex(wave: String): Int = when (wave.lowercase()) {
    "sine" -> 0
    "triangle" -> 1
    "sawtooth" -> 2
    else -> 3
}

private fun specName(midi: Int): String = SPEC_NAMES[((midi % 12) + 12) % 12]
private fun specFullName(midi: Int): String = specName(midi) + (midi / 12 - 1)

private fun blendSpecColor(midi1: Int, midi2: Int, shading: Boolean, strength: Float): Color {
    val pc1 = ((midi1 % 12) + 12) % 12
    val pc2 = ((midi2 % 12) + 12) % 12
    var r = min(255f, (SPEC_COLORS[pc1][0] + SPEC_COLORS[pc2][0]).toFloat())
    var g = min(255f, (SPEC_COLORS[pc1][1] + SPEC_COLORS[pc2][1]).toFloat())
    var b = min(255f, (SPEC_COLORS[pc1][2] + SPEC_COLORS[pc2][2]).toFloat())
    if (shading) {
        val interval = abs(pc2 - pc1) % 12
        val br = SPEC_INTERVAL_BRIGHTNESS[interval]
        val satRange = SPEC_INTERVAL_SAT[interval]
        val brightness = max(1f, .299f * r + .587f * g + .114f * b)
        var scale = when {
            brightness < br[0] -> br[0] / brightness
            brightness > br[1] -> br[1] / brightness
            else -> 1f
        }
        scale = 1f + (scale - 1f) * strength
        r *= scale; g *= scale; b *= scale
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val sat = (hi - lo) / max(1f, hi)
        val target = sat.coerceIn(satRange[0], satRange[1])
        if (sat > .001f && abs(target - sat) > .001f) {
            val gray = (r + g + b) / 3f
            val ratio = 1f + (target / sat - 1f) * strength
            r = gray + (r - gray) * ratio
            g = gray + (g - gray) * ratio
            b = gray + (b - gray) * ratio
        }
    }
    return Color((r / 255f).coerceIn(0f, 1f), (g / 255f).coerceIn(0f, 1f), (b / 255f).coerceIn(0f, 1f), 1f)
}

@Composable
private fun NativeSpectrachordInstrument(onClose: () -> Unit) {
    val context = LocalContext.current
    var panel by remember { mutableStateOf("") }
    var wave1 by remember { mutableStateOf("square") }
    var wave2 by remember { mutableStateOf("sawtooth") }
    var volume by remember { mutableStateOf(.10f) }
    var harmonicShading by remember { mutableStateOf(true) }
    var shadingStrength by remember { mutableStateOf(1f) }
    var hold by remember { mutableStateOf(false) }
    var touchOn by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var octaveOffset by remember { mutableStateOf(0) }
    var delayOn by remember { mutableStateOf(true) }
    var delayTime by remember { mutableStateOf(.25f) }
    var delayAmount by remember { mutableStateOf(.40f) }
    var delayFeedback by remember { mutableStateOf(.30f) }
    var reverbOn by remember { mutableStateOf(true) }
    var reverbAmount by remember { mutableStateOf(.30f) }
    var roomSize by remember { mutableStateOf(.50f) }
    val enabled = remember {
        mutableStateMapOf<String, Boolean>().apply {
            SPEC_NAMES.forEach { this[it] = !it.contains('#') }
        }
    }
    val heldCells = remember { mutableStateMapOf<Int, Boolean>() }

    LaunchedEffect(Unit) { Audio.ensure(context) }
    LaunchedEffect(delayOn, delayTime, delayAmount, delayFeedback, reverbOn, reverbAmount, roomSize) {
        Audio.ensure(context)
        NativeAudio.setSpecFx(delayOn, delayAmount, delayFeedback, delayTime, reverbOn, reverbAmount, roomSize)
    }
    LaunchedEffect(hold) {
        if (!hold && heldCells.isNotEmpty()) {
            heldCells.keys.toList().forEach { cell ->
                NativeAudio.specLiveStop(300000 + cell * 2)
                NativeAudio.specLiveStop(300001 + cell * 2)
            }
            heldCells.clear()
        }
    }
    DisposableEffect(Unit) {
        onDispose { try { NativeAudio.livePanic() } catch (_: Throwable) {} }
    }

    Column(Modifier.fillMaxSize().background(SpectraBg).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallBtn("Close", onClose)
            SpectrachordLogo(Modifier.weight(1f))
            ToggleBtn(if (touchOn) "Touch ON" else "Touch OFF", touchOn) { touchOn = !touchOn }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallBtn("Sound", { panel = if (panel == "sound") "" else "sound" }, on = panel == "sound")
            SmallBtn("FX", { panel = if (panel == "fx") "" else "fx" }, on = panel == "fx")
            SmallBtn("View", { panel = if (panel == "view") "" else "view" }, on = panel == "view")
            SmallBtn("Notes", { panel = if (panel == "notes") "" else "notes" }, on = panel == "notes")
        }

        when (panel) {
            "sound" -> Column(Modifier.fillMaxWidth().background(SpectraPanel).padding(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WavePicker("Osc1", wave1) { wave1 = it }
                    WavePicker("Osc2", wave2) { wave2 = it }
                }
                CompactSlider("Volume", volume, 0f..1f, { "%.2f".format(it) }) { volume = it }
                CompactCheck("Harmonic shading", harmonicShading) { harmonicShading = it }
                CompactSlider("Shading", shadingStrength, 0f..1f, { "%.2f".format(it) }) { shadingStrength = it }
            }
            "fx" -> Column(Modifier.fillMaxWidth().background(SpectraPanel).padding(4.dp)) {
                CompactCheck("Delay", delayOn) { delayOn = it }
                CompactSlider("Time", delayTime, 0f..1f, { "%.2fs".format(it) }) { delayTime = it }
                CompactSlider("Amount", delayAmount, 0f..1f, { "%.2f".format(it) }) { delayAmount = it }
                CompactSlider("Feedback", delayFeedback, 0f..0.95f, { "%.2f".format(it) }) { delayFeedback = it }
                CompactCheck("Reverb", reverbOn) { reverbOn = it }
                CompactSlider("Verb", reverbAmount, 0f..1f, { "%.2f".format(it) }) { reverbAmount = it }
                CompactSlider("Room", roomSize, .1f..1f, { "%.2f".format(it) }) { roomSize = it }
            }
            "view" -> Column(Modifier.fillMaxWidth().background(SpectraPanel).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToggleBtn("Hold", hold) { hold = !hold }
                    Text("Zoom", color = SpectraMuted, fontSize = 9.sp)
                    SmallBtn("−") { zoom = (zoom / 1.1f).coerceAtLeast(.30f); if (zoom <= 1f) pan = Offset.Zero }
                    Text("${(zoom * 100).toInt()}%", color = SpectraMuted, fontSize = 10.sp)
                    SmallBtn("+") { zoom = (zoom * 1.1f).coerceAtMost(4f) }
                    SmallBtn("Fit") { zoom = 1f; pan = Offset.Zero }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Octave", color = SpectraMuted, fontSize = 9.sp)
                    SmallBtn("−") { octaveOffset = (octaveOffset - 1).coerceAtLeast(-3) }
                    Text("${if (octaveOffset >= 0) "+" else ""}$octaveOffset", color = SpectraMuted, fontSize = 10.sp)
                    SmallBtn("+") { octaveOffset = (octaveOffset + 1).coerceAtMost(3) }
                    SmallBtn("Reset") { octaveOffset = 0 }
                }
                Text("Touch OFF: drag to pan, pinch to zoom", color = SpectraMuted, fontSize = 9.sp)
            }
            "notes" -> NoteFilter(enabled)
        }

        SpectrachordGrid(
            modifier = Modifier.fillMaxWidth().weight(1f),
            octaveOffset = octaveOffset,
            wave1 = wave1,
            wave2 = wave2,
            volume = volume,
            hold = hold,
            touchOn = touchOn,
            zoom = zoom,
            pan = pan,
            onZoomChange = { zoom = it.coerceIn(.30f, 4f) },
            onPanChange = { pan = it },
            harmonicShading = harmonicShading,
            shadingStrength = shadingStrength,
            enabled = enabled,
            heldCells = heldCells,
            ensureAudio = { Audio.ensure(context) }
        )
        Text(if (touchOn) "Native 25×25 dual-note grid · slide across cells for chords · Hold latches cells" else "Touch OFF · drag to pan · pinch to zoom", color = SpectraMuted, fontSize = 9.sp)
    }
}

@Composable
private fun SpectrachordLogo(modifier: Modifier = Modifier) {
    val spectrum = listOf(
        Color(0xFF3AFF00), Color(0xFF00FFEC), Color(0xFF008FFF), Color(0xFF0F00FB),
        Color(0xFF6300BE), Color(0xFF6E0080), Color(0xFF980000), Color(0xFFC80000),
        Color(0xFFF30000), Color(0xFFFF7800), Color(0xFFFFEF00), Color(0xFFAAFF00)
    )
    Text(
        "SPECTRACHORD",
        modifier = modifier,
        maxLines = 1,
        style = TextStyle(
            brush = Brush.horizontalGradient(spectrum),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.25.sp,
            shadow = Shadow(Color(0x5533FFFF), Offset.Zero, 8f)
        )
    )
}

@Composable
private fun WavePicker(label: String, value: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = SpectraMuted, fontSize = 10.sp)
        androidx.compose.foundation.layout.Box {
            SmallBtn(value.replaceFirstChar { it.uppercase() }) { open = true }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                listOf("sine", "square", "sawtooth", "triangle").forEach { w ->
                    DropdownMenuItem(text = { Text(w) }, onClick = { onPick(w); open = false })
                }
            }
        }
    }
}

@Composable
private fun CompactCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp)) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, color = SpectraText, fontSize = 10.sp)
    }
}

@Composable
private fun CompactSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, fmt: (Float) -> String, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = SpectraMuted, fontSize = 9.sp, modifier = Modifier.fillMaxWidth(.17f))
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(fmt(value), color = SpectraMuted, fontSize = 9.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun NoteFilter(enabled: MutableMap<String, Boolean>) {
    Column(Modifier.fillMaxWidth().background(SpectraPanel).padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0 until 3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 4) {
                    val n = SPEC_NAMES[r * 4 + c]
                    ToggleBtn(n, enabled[n] == true) { enabled[n] = !(enabled[n] == true) }
                }
            }
        }
    }
}

@Composable
private fun SpectrachordGrid(
    modifier: Modifier,
    octaveOffset: Int,
    wave1: String,
    wave2: String,
    volume: Float,
    hold: Boolean,
    touchOn: Boolean,
    zoom: Float,
    pan: Offset,
    onZoomChange: (Float) -> Unit,
    onPanChange: (Offset) -> Unit,
    harmonicShading: Boolean,
    shadingStrength: Float,
    enabled: Map<String, Boolean>,
    heldCells: MutableMap<Int, Boolean>,
    ensureAudio: () -> Unit
) {
    val pointerCells = remember { mutableMapOf<Long, Int>() }

    fun cellMidis(cell: Int): Pair<Int, Int> {
        val row = cell / 25
        val col = cell % 25
        return Pair(62 + (24 - row) + octaveOffset * 12, 62 + col + octaveOffset * 12)
    }
    fun canPlay(cell: Int): Boolean {
        val (m1, m2) = cellMidis(cell)
        return enabled[specName(m1)] == true && enabled[specName(m2)] == true
    }
    fun pointerBase(pointerId: Long): Int = 200000 + ((pointerId and 0x7fffL).toInt() * 2)
    fun start(cell: Int, base: Int) {
        if (!canPlay(cell)) return
        ensureAudio()
        val (m1, m2) = cellMidis(cell)
        NativeAudio.specLiveStart(base, m1, volume, waveIndex(wave1))
        NativeAudio.specLiveStart(base + 1, m2, volume, waveIndex(wave2))
    }
    fun stop(base: Int) {
        NativeAudio.specLiveStop(base)
        NativeAudio.specLiveStop(base + 1)
    }

    Canvas(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(octaveOffset, wave1, wave2, volume, hold, touchOn, enabled.toMap(), zoom, pan) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val side = min(size.width, size.height).toFloat()
                        val gridSide = side * zoom
                        val originX = (size.width - gridSide) / 2f + pan.x
                        val originY = (size.height - gridSide) / 2f + pan.y
                        val cellSize = gridSide / 25f

                        if (!touchOn) {
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isNotEmpty()) {
                                val dx = pressed.map { it.position.x - it.previousPosition.x }.average().toFloat()
                                val dy = pressed.map { it.position.y - it.previousPosition.y }.average().toFloat()
                                var nextZoom = zoom
                                if (pressed.size >= 2) {
                                    val a = pressed[0]
                                    val b = pressed[1]
                                    val nowDx = a.position.x - b.position.x
                                    val nowDy = a.position.y - b.position.y
                                    val oldDx = a.previousPosition.x - b.previousPosition.x
                                    val oldDy = a.previousPosition.y - b.previousPosition.y
                                    val nowDist = sqrt(nowDx * nowDx + nowDy * nowDy)
                                    val oldDist = sqrt(oldDx * oldDx + oldDy * oldDy)
                                    if (oldDist > 1f && nowDist > 1f) {
                                        nextZoom = (zoom * (nowDist / oldDist)).coerceIn(.30f, 4f)
                                        onZoomChange(nextZoom)
                                    }
                                }
                                if (nextZoom > 1f) onPanChange(pan + Offset(dx, dy))
                                else onPanChange(Offset.Zero)
                            }
                            event.changes.forEach { it.consume() }
                            continue
                        }

                        for (change in event.changes) {
                            val id = change.id.value
                            val colRaw = ((change.position.x - originX) / cellSize).toInt()
                            val rowRaw = ((change.position.y - originY) / cellSize).toInt()
                            val inside = change.position.x >= originX && change.position.x < originX + gridSide &&
                                change.position.y >= originY && change.position.y < originY + gridSide &&
                                colRaw in 0..24 && rowRaw in 0..24
                            val cell = if (inside) rowRaw * 25 + colRaw else -1

                            if (change.pressed && !change.previousPressed) {
                                if (!inside) continue
                                if (hold) {
                                    val base = 300000 + cell * 2
                                    if (heldCells[cell] == true) {
                                        stop(base)
                                        heldCells.remove(cell)
                                    } else if (canPlay(cell)) {
                                        start(cell, base)
                                        heldCells[cell] = true
                                    }
                                } else {
                                    val base = pointerBase(id)
                                    start(cell, base)
                                    if (canPlay(cell)) pointerCells[id] = cell
                                }
                            } else if (change.pressed && change.previousPressed && !hold) {
                                val prev = pointerCells[id]
                                if (!inside) {
                                    if (prev != null) stop(pointerBase(id))
                                    pointerCells.remove(id)
                                } else if (prev != cell && canPlay(cell)) {
                                    val base = pointerBase(id)
                                    if (prev != null) stop(base)
                                    // A blacked-out cell is transparent during a glide.
                                      // Keep the last playable chord sounding until another
                                      // playable square is reached.
                                      start(cell, base)
                                      pointerCells[id] = cell
                                }
                            } else if (!change.pressed && change.previousPressed && !hold) {
                                if (pointerCells.remove(id) != null) stop(pointerBase(id))
                            }
                            change.consume()
                        }
                    }
                }
            }
    ) {
        val side = min(size.width, size.height)
        val gridSide = side * zoom
        val originX = (size.width - gridSide) / 2f + pan.x
        val originY = (size.height - gridSide) / 2f + pan.y
        val cellSize = gridSide / 25f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

        for (row in 0 until 25) {
            for (col in 0 until 25) {
                val cell = row * 25 + col
                val m1 = 62 + (24 - row) + octaveOffset * 12
                val m2 = 62 + col + octaveOffset * 12
                val allowed = enabled[specName(m1)] == true && enabled[specName(m2)] == true
                val baseColor = if (allowed) blendSpecColor(m1, m2, harmonicShading, shadingStrength) else Color(0xFF111111)
                val color = if (heldCells[cell] == true) Color.White else baseColor
                val left = originX + col * cellSize
                val top = originY + row * cellSize
                drawRect(color, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(cellSize, cellSize))
                drawRect(
                    Color(0x44000000),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(cellSize, cellSize),
                    style = Stroke(max(.5f, cellSize * .03f))
                )
                if (cellSize >= 12f) {
                    val lum = .299f * baseColor.red + .587f * baseColor.green + .114f * baseColor.blue
                    textPaint.color = (if (heldCells[cell] == true || lum > .55f) Color.Black else Color.White).toArgb()
                    textPaint.textSize = (cellSize * .31f).coerceAtLeast(4f)
                    val label = specFullName(m1) + "/" + specFullName(m2)
                    drawContext.canvas.nativeCanvas.drawText(label, left + cellSize / 2f, top + cellSize * .62f, textPaint)
                }
            }
        }
    }
}
