package com.sonolume.spectra.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SpanStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle as TextSpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sonolume.spectra.NativeAudio
import com.sonolume.spectra.daw.DawViewModel
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow

private val NexBg = Color(0xFF0C0E14)
private val NexPanel = Color(0xFF151926)
private val NexBorder = Color(0xFF263048)
private val NexControl = Color(0xFF232839)
private val NexControlOn = Color(0xFF33466B)
private val NexText = Color(0xFFE8EAF0)
private val NexMuted = Color(0xFFA6ADBC)

private enum class NexKind { KICK, SNARE, CYMBAL }

private data class NexMode(
    val kick: List<String>,
    val snare: List<String>,
    val cymbal: List<String>,
    val sub: String,
)

private val NexCompact = NexMode(
    kick = listOf("E2", "F2", "F#2", "G2", "G#2", "A2", "A#2"),
    snare = listOf("B2", "C3", "C#3", "D3", "D#3", "E3", "F3"),
    cymbal = listOf("F#3", "G3", "G#3", "A3", "A#3", "B3", "C4"),
    sub = "7 kick pedals (E2–A♯2) · 7 snares (B2–F3) · 7 cymbals (F♯3–C4). Hit position = dynamics.",
)

private val NexFull = NexMode(
    kick = listOf("G1", "G#1", "A1", "A#1", "B1", "C2", "C#2", "D2", "D#2", "E2", "F2", "F#2"),
    snare = listOf("D3", "D#3", "E3", "F3", "F#3", "G3", "G#3", "A3", "A#3", "B3", "C4", "C#4"),
    cymbal = listOf("A4", "A#4", "B4", "C5", "C#5", "D5", "D#5", "E5", "F5", "F#5", "G5", "G#5"),
    sub = "12 kick pedals (G1–F♯2) · 12 snares (D3–C♯4) · 12 cymbals (A4–G♯5). Hit position = dynamics.",
)

@Composable
fun NativeNexdrumDialog(vm: DawViewModel) {
    var volume by remember { mutableFloatStateOf(85f) }
    var arch by remember { mutableFloatStateOf(64f) }
    var fitOn by remember { mutableStateOf(true) }
    var dynamicsOn by remember { mutableStateOf(false) }
    var fullOn by remember { mutableStateOf(false) }
    var soundReady by remember { mutableStateOf(false) }
    var readout by remember { mutableStateOf("Tap a pad. Kick: top = loud. Snare: center = loud. Cymbal: edge = loud.") }
    val mode = if (fullOn) NexFull else NexCompact

    fun ensureAudio() {
        NativeAudio.start()
        soundReady = true
    }
    fun close() {
        NativeAudio.livePanic()
        vm.openInstrument(null)
    }

    DisposableEffect(Unit) { onDispose { NativeAudio.livePanic() } }

    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = NexBg) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        NexTitle()
                        Text(mode.sub, color = NexMuted, fontSize = 8.sp, lineHeight = 10.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                    NexButton("Close", false, ::close)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NexSlider("Volume", volume, 0f, 100f, Modifier.weight(1f)) { volume = it }
                    NexSlider("Arch", arch, 0f, 140f, Modifier.weight(1f)) { arch = it }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexButton(if (fitOn) "Fit: on" else "Fit: off", fitOn) { fitOn = !fitOn }
                    NexButton(if (dynamicsOn) "Dynamics: on" else "Dynamics: off", dynamicsOn) { dynamicsOn = !dynamicsOn }
                    NexButton(if (fullOn) "Full 36: on" else "Full 36: off", fullOn) { fullOn = !fullOn }
                    NexButton(if (soundReady) "Sound on · Native" else "Enable sound", soundReady) { ensureAudio() }
                }

                Text(readout, modifier = Modifier.fillMaxWidth(), color = NexText.copy(alpha = 0.9f), fontSize = 8.sp, lineHeight = 10.sp, textAlign = TextAlign.Center)

                NexKit(mode, arch, fitOn, dynamicsOn, volume) { kind, note, velocity, position, description ->
                    ensureAudio()
                    val scaled = (velocity * (volume / 85f)).coerceIn(0.05f, 1.15f)
                    NativeAudio.drumHit(kind.ordinal, nexNoteToFreq(note), scaled, position, 0f)
                    readout = "${nexPretty(note)} ${kind.name.lowercase()} · ${(velocity * 100f).toInt()}% · $description"
                }

                Text(
                    "Each row stays on one line and arcs like a rainbow. Arch controls curve depth. Fit keeps the whole kit playable without horizontal scrolling; switch Fit off to pan across the full-size pads.",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp),
                    color = NexMuted,
                    fontSize = 7.sp,
                    lineHeight = 9.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun NexTitle() {
    val colors = listOf(Color(0xFF00FFEC), Color(0xFF008FFF), Color(0xFF6300BE), Color(0xFF980000), Color(0xFFF30000), Color(0xFFFF7800), Color(0xFFFFEF00))
    val text = buildAnnotatedString {
        append("The ")
        "Nexdrum".forEachIndexed { i, c ->
            pushStyle(TextSpanStyle(color = colors[i % colors.size], fontWeight = FontWeight.Black))
            append(c)
            pop()
        }
    }
    Text(text, color = NexText, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 0.3.sp)
}

@Composable
private fun NexKit(
    mode: NexMode,
    arch: Float,
    fitOn: Boolean,
    dynamicsOn: Boolean,
    volume: Float,
    onHit: (NexKind, String, Float, Float, String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NexPanel,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NexBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp)) {
            NexRow(if (mode === NexFull) "Cymbals — A → G♯" else "Cymbals — F♯3 → C4", "edge loud, center soft", NexKind.CYMBAL, mode.cymbal, arch, fitOn, dynamicsOn, volume, onHit)
            NexRow(if (mode === NexFull) "Snares — D → C♯" else "Snares — B2 → F3", "center loud, edge quiet", NexKind.SNARE, mode.snare, arch, fitOn, dynamicsOn, volume, onHit)
            NexRow(if (mode === NexFull) "Kicks — G → F♯" else "Kicks — E2 → A♯2", "top loud, bottom quiet", NexKind.KICK, mode.kick, arch, fitOn, dynamicsOn, volume, onHit)
        }
    }
}

@Composable
private fun NexRow(
    title: String,
    hint: String,
    kind: NexKind,
    notes: List<String>,
    arch: Float,
    fitOn: Boolean,
    dynamicsOn: Boolean,
    volume: Float,
    onHit: (NexKind, String, Float, Float, String) -> Unit,
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title.uppercase(), color = NexText, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.25.sp)
        Text(hint, color = NexMuted, fontSize = 7.sp)
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val count = notes.size
        val gap = 4.dp
        val natural = if (kind == NexKind.KICK) 48.dp else 50.dp
        val fitted = ((maxWidth - gap * (count - 1)) / count).coerceAtLeast(18.dp)
        val padWidth = if (fitOn) minDp(natural, fitted) else natural
        val archDp = (arch * 0.48f).dp
        val baseHeight = when (kind) { NexKind.KICK -> padWidth * 1.52f; else -> padWidth }
        val rowHeight = baseHeight + archDp + 6.dp
        val scroll = rememberScrollState()
        Row(
            modifier = Modifier.fillMaxWidth().then(if (fitOn) Modifier else Modifier.horizontalScroll(scroll)).height(rowHeight).padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Top
        ) {
            notes.forEachIndexed { index, note ->
                val t = if (count > 1) index.toFloat() / (count - 1).toFloat() else 0.5f
                val arcFactor = 1f - sin((PI * t).toFloat())
                val y = archDp * arcFactor
                val rotation = (t - 0.5f) * 22f * (arch / 70f).coerceIn(0f, 2f)
                Box(modifier = Modifier.width(padWidth).padding(top = y).graphicsLayer { rotationZ = rotation }) {
                    NexPad(kind, note, padWidth, dynamicsOn, volume, onHit)
                }
            }
        }
    }
}

@Composable
private fun NexPad(
    kind: NexKind,
    note: String,
    width: Dp,
    dynamicsOn: Boolean,
    volume: Float,
    onHit: (NexKind, String, Float, Float, String) -> Unit,
) {
    var flash by remember { mutableStateOf<Offset?>(null) }
    var flashSeq by remember { mutableStateOf(0) }
    val color = nexNoteColor(note)

    LaunchedEffect(flashSeq) { if (flash != null) { delay(140); flash = null } }

    fun fire(p: Offset, size: androidx.compose.ui.unit.IntSize) {
        val w = size.width.coerceAtLeast(1).toFloat()
        val h = size.height.coerceAtLeast(1).toFloat()
        val x = p.x.coerceIn(0f, w)
        val y = p.y.coerceIn(0f, h)
        when (kind) {
            NexKind.KICK -> {
                val yn = (y / h).coerceIn(0f, 1f)
                val vel = if (dynamicsOn) 0.3f + 0.7f * (1f - yn) else 1f
                val pos = if (dynamicsOn) yn else 0.15f
                val desc = if (pos < 0.36f) "toe (top)" else if (pos < 0.71f) "toe+heel" else "heel (bottom)"
                onHit(kind, note, vel, pos, desc)
            }
            NexKind.SNARE -> {
                val dx = (x - w / 2f) / (w / 2f)
                val dy = (y - h / 2f) / (h / 2f)
                val r = sqrt(dx * dx + dy * dy).coerceIn(0f, 1f)
                val vel = if (dynamicsOn) 1f - 0.7f * r else 1f
                val pos = if (dynamicsOn) r else 0f
                val edge = nexSmoothStep(0.3f, 0.95f, pos)
                val desc = if (edge < 0.25f) "center" else if (edge < 0.75f) "center+edge" else "edge"
                onHit(kind, note, vel, pos, desc)
            }
            NexKind.CYMBAL -> {
                val dx = (x - w / 2f) / (w / 2f)
                val dy = (y - h / 2f) / (h / 2f)
                val r = sqrt(dx * dx + dy * dy).coerceIn(0f, 1f)
                val vel = if (dynamicsOn) 0.22f + 0.78f * r else 1f
                val pos = if (dynamicsOn) r else 0.5f
                val desc = if (pos < 0.38f) "bell" else if (pos < 0.76f) "bow" else "edge"
                onHit(kind, note, vel, pos, desc)
            }
        }
        flash = Offset(x, y)
        flashSeq++
    }

    val ratio = if (kind == NexKind.KICK) 3f / 4.6f else 1f
    Box(
        modifier = Modifier.width(width).aspectRatio(ratio).pointerInput(kind, note, dynamicsOn, volume) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fire(down.position, size)
                down.consume()
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            when (kind) {
                NexKind.KICK -> drawKick(color, flash)
                NexKind.SNARE -> drawSnare(color, flash)
                NexKind.CYMBAL -> drawCymbal(color, flash)
            }
        }
        if (kind == NexKind.KICK) Text("▲ LOUD", modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp), color = Color(0xFFFFD47C), fontSize = 5.sp, fontWeight = FontWeight.Bold)
        Text(
            nexPretty(note),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            color = if (kind == NexKind.SNARE) Color(0xFF1A2030) else if (kind == NexKind.CYMBAL) Color(0xFF2E2008) else Color.White,
            fontSize = if (width < 30.dp) 6.sp else 8.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKick(glow: Color, flash: Offset?) {
    val r = 7.dp.toPx()
    drawRoundRect(Color(0xFF171B26), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
    drawRoundRect(Color(0xFF39415A), style = Stroke(width = 2.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
    val insetX = size.width * 0.12f
    val top = size.height * 0.10f
    val bottom = size.height * 0.74f
    drawRoundRect(Color(0xFF30384B), Offset(insetX, top), Size(size.width - 2f * insetX, bottom - top), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()))
    var y = top + 5.dp.toPx()
    while (y < bottom - 2.dp.toPx()) {
        drawLine(Color.White.copy(alpha = 0.14f), Offset(insetX + 2.dp.toPx(), y), Offset(size.width - insetX - 2.dp.toPx(), y), strokeWidth = 1.dp.toPx())
        y += 6.dp.toPx()
    }
    if (flash != null) { drawCircle(glow.copy(alpha = 0.33f), radius = kotlin.math.min(size.width, size.height) * 0.28f, center = flash); drawCircle(Color.White, radius = 3.dp.toPx(), center = flash) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnare(glow: Color, flash: Offset?) {
    val c = center
    val r = kotlin.math.min(size.width, size.height) * 0.48f
    drawCircle(Color(0xFF4C5468), r, c); drawCircle(Color(0xFFAAB1C5), r * 0.92f, c); drawCircle(Color(0xFFE9EBF1), r * 0.82f, c); drawCircle(Color.White.copy(alpha = 0.75f), r * 0.60f, c); drawCircle(Color(0xFFC51E2D), r * 0.105f, c); drawCircle(Color(0xFF7B0811), r * 0.105f, c, style = Stroke(1.dp.toPx()))
    if (flash != null) { drawCircle(glow.copy(alpha = 0.28f), radius = r * 0.46f, center = flash); drawCircle(Color.White, radius = 3.dp.toPx(), center = flash) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCymbal(glow: Color, flash: Offset?) {
    val c = center
    val r = kotlin.math.min(size.width, size.height) * 0.48f
    drawCircle(Color(0xFF5E4311), r, c); drawCircle(Color(0xFFD9A63A), r * 0.92f, c); drawCircle(Color(0xFFE8C25C), r * 0.73f, c); drawCircle(Color(0xFFA97A1F), r * 0.55f, c, style = Stroke(1.dp.toPx())); drawCircle(Color(0xFFF5CD5E), r * 0.36f, c); drawCircle(Color(0xFFFFE9A0), r * 0.18f, c)
    for (f in listOf(0.30f, 0.48f, 0.66f, 0.84f)) drawCircle(Color(0xFF6B4D15).copy(alpha = 0.32f), r * f, c, style = Stroke(0.7.dp.toPx()))
    if (flash != null) { drawCircle(glow.copy(alpha = 0.30f), radius = r * 0.44f, center = flash); drawCircle(Color.White, radius = 3.dp.toPx(), center = flash) }
}

@Composable
private fun NexButton(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.height(24.dp).background(if (active) NexControlOn else NexControl, RoundedCornerShape(6.dp)).border(1.dp, if (active) Color(0xFF5876A8) else Color(0xFF3A4560), RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = NexText, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun NexSlider(label: String, value: Float, min: Float, max: Float, modifier: Modifier = Modifier, onValue: (Float) -> Unit) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = NexText, fontSize = 7.sp); Text(value.toInt().toString(), color = NexMuted, fontSize = 7.sp) }
        Box(
            modifier = Modifier.fillMaxWidth().height(16.dp).pointerInput(min, max) {
                fun set(x: Float) { val f = (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f); onValue(min + (max - min) * f) }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false); set(down.position.x); down.consume()
                    while (true) { val event = awaitPointerEvent(); val ch = event.changes.firstOrNull() ?: break; if (!ch.pressed) break; set(ch.position.x); ch.consume() }
                }
            }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                drawLine(Color(0xFF3A4560), Offset(3.dp.toPx(), cy), Offset(size.width - 3.dp.toPx(), cy), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                val f = ((value - min) / (max - min)).coerceIn(0f, 1f)
                val x = 3.dp.toPx() + (size.width - 6.dp.toPx()) * f
                drawLine(Color(0xFF7394C9), Offset(3.dp.toPx(), cy), Offset(x, cy), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(Color(0xFFD7E4FF), 4.dp.toPx(), Offset(x, cy))
            }
        }
    }
}

private fun minDp(a: Dp, b: Dp): Dp = if (a < b) a else b
private fun nexPretty(note: String): String = note.replace("#", "♯")

private fun nexNoteToFreq(note: String): Float {
    val name = note.dropLast(1)
    val octave = note.takeLast(1).toIntOrNull() ?: 4
    val semi = when (name) { "C" -> 0; "C#" -> 1; "D" -> 2; "D#" -> 3; "E" -> 4; "F" -> 5; "F#" -> 6; "G" -> 7; "G#" -> 8; "A" -> 9; "A#" -> 10; else -> 11 }
    val midi = (octave + 1) * 12 + semi
    return (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()
}

private fun nexNoteColor(note: String): Color = when (note.dropLast(1)) {
    "C" -> Color(0xFF3AFF00); "C#" -> Color(0xFF00FFEC); "D" -> Color(0xFF008FFF); "D#" -> Color(0xFF0F00FB); "E" -> Color(0xFF6300BE); "F" -> Color(0xFF6E0080); "F#" -> Color(0xFF980000); "G" -> Color(0xFFC80000); "G#" -> Color(0xFFF30000); "A" -> Color(0xFFFF7800); "A#" -> Color(0xFFFFEF00); else -> Color(0xFFAAFF00)
}

private fun nexSmoothStep(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
