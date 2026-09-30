package com.sonolume.spectra.ui

import android.graphics.Paint
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sonolume.spectra.NativeAudio
import com.sonolume.spectra.daw.Audio
import com.sonolume.spectra.daw.DawViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val nebNoteNames = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
private val nebNoteColors = listOf(
    Color(58,255,0), Color(0,255,236), Color(0,143,255), Color(15,0,251),
    Color(99,0,190), Color(110,0,128), Color(152,0,0), Color(200,0,0),
    Color(243,0,0), Color(255,120,0), Color(255,239,0), Color(170,255,0)
)
private val nebPhaseLabels = listOf("0","7","2","9","4","E","6","1","8","3","X","5")
private val nebPhaseSemitones = listOf(0,7,2,9,4,11,6,1,8,3,10,5)
private val nebSemitoneToPhase = IntArray(12).also { a -> nebPhaseSemitones.forEachIndexed { i, s -> a[s] = i } }
private val nebFullStrings = listOf(20,27,34,41,48,55,62,69,76,83,90,97,104)
private val nebPresets = linkedMapOf(
    "Nebular" to listOf(29,36,43,50,45,40,35),
    "Cello" to listOf(36,43,50,57,64),
    "Guitar" to listOf(65,60,55,50,45,40,35),
    "Custom" to listOf(48,55,62,69,76)
)

private fun nebMod(v: Int, m: Int = 12): Int = ((v % m) + m) % m
private fun nebLabel(midi: Int): String = "${nebNoteNames[nebMod(midi)]}${midi / 12 - 1}"
private fun nebPcColor(midi: Int): Color = nebNoteColors[nebMod(midi)]
private fun nebDozenal(n: Int): String {
    val d = "0123456789XE"
    if (n < 12) return d[n].toString()
    var x = n
    var out = ""
    while (x > 0) { out = d[x % 12] + out; x /= 12 }
    return out
}

private data class NebProfile(val level: Float, val pressure: Float, val mute: Float)
private data class NebVoice(val cell: Pair<Int,Int>, val profile: NebProfile)
private data class NebLoopEvent(
    val t: Long, val kind: Int, val id: Int, val midi: Int,
    val level: Float, val pressure: Float, val mute: Float, val cents: Float,
    val volume: Float, val pluck: Boolean, val release: Float
)

@Composable
fun NativeNebularDialog(vm: DawViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openMidi = remember { mutableStateListOf<Int>().apply { addAll(nebPresets.getValue("Nebular")) } }
    val atomSelections = remember { mutableStateListOf<Set<Int>>().apply { repeat(openMidi.size) { add(emptySet()) } } }
    val activeVoices = remember { mutableStateMapOf<Long, NebVoice>() }

    var preset by remember { mutableStateOf("Nebular") }
    var presetMenu by remember { mutableStateOf(false) }
    var matchScale by remember { mutableStateOf(false) }
    var audioOn by remember { mutableStateOf(true) }
    var volume by remember { mutableFloatStateOf(.8f) }
    var releaseMs by remember { mutableFloatStateOf(700f) }
    var pluck by remember { mutableStateOf(false) }
    var palm by remember { mutableStateOf(false) }
    var yExpression by remember { mutableStateOf(true) }
    var expressionDepth by remember { mutableFloatStateOf(.85f) }
    var xBend by remember { mutableStateOf(true) }
    var bendRange by remember { mutableFloatStateOf(50f) }

    var verbOn by remember { mutableStateOf(false) }
    var verbAmt by remember { mutableFloatStateOf(.25f) }
    var verbDecay by remember { mutableFloatStateOf(2.5f) }
    var delayOn by remember { mutableStateOf(false) }
    var delayAmt by remember { mutableFloatStateOf(.25f) }
    var delayFb by remember { mutableFloatStateOf(.35f) }
    var delayTime by remember { mutableFloatStateOf(.32f) }
    var distOn by remember { mutableStateOf(false) }
    var distAmt by remember { mutableFloatStateOf(25f) }
    var distIn by remember { mutableFloatStateOf(1f) }
    var distOut by remember { mutableFloatStateOf(.85f) }

    var loopState by remember { mutableStateOf("idle") }
    var loopStart by remember { mutableStateOf(0L) }
    var loopDuration by remember { mutableStateOf(0L) }
    val loopEvents = remember { mutableStateListOf<NebLoopEvent>() }
    var loopJob by remember { mutableStateOf<Job?>(null) }

    fun ensureAudio() { Audio.ensure(context) }
    fun restoreDaw() {
        try {
            NativeAudio.livePanic()
            val ui = vm.ui.value
            Audio.applyAll(ui.song, ui.masterV)
        } catch (_: Throwable) {}
    }
    fun stopAll() {
        activeVoices.keys.toList().forEach { id -> NativeAudio.nebLiveStop((id and 0x7fffffff).toInt(), releaseMs) }
        activeVoices.clear()
        try { NativeAudio.livePanic() } catch (_: Throwable) {}
    }
    fun resetAtoms() {
        for (i in atomSelections.indices) atomSelections[i] = emptySet()
    }
    fun applyPreset(name: String) {
        stopAll()
        preset = name
        openMidi.clear(); openMidi.addAll(nebPresets.getValue(name))
        atomSelections.clear(); repeat(openMidi.size) { atomSelections.add(emptySet()) }
    }
    fun record(ev: NebLoopEvent) {
        if (loopState == "recording") loopEvents.add(ev.copy(t = SystemClock.uptimeMillis() - loopStart))
    }
    fun profile(yFrac: Float): NebProfile {
        if (!yExpression) return NebProfile(1f,1f,0f)
        val distance = (abs(yFrac.coerceIn(0f,1f) - .5f) / .5f).coerceIn(0f,1f)
        if (palm) {
            val mute = (distance * expressionDepth).coerceIn(0f,1f)
            return NebProfile(1f,1f,mute)
        }
        if (!pluck) {
            val pressure = (1f - distance * (1.18f * expressionDepth)).coerceIn(0f,1f)
            val level = .06f + pressure.toDouble().pow(2.35).toFloat() * .94f
            return NebProfile(level, pressure, 0f)
        }
        val minLevel = if (expressionDepth >= .999f) 0f else .08f
        val level = (1f - distance * expressionDepth).coerceIn(minLevel,1f)
        return NebProfile(level, level, 0f)
    }
    fun cents(xFrac: Float): Float = if (xBend) ((xFrac.coerceIn(0f,1f) - .5f) * 2f * bendRange) else 0f

    fun nativeId(id: Long): Int = (id and 0x3fffffff).toInt()
    fun startVoice(id: Long, stringIndex: Int, position: Int, p: NebProfile, c: Float, doRecord: Boolean = true) {
        if (!audioOn || stringIndex !in openMidi.indices) return
        ensureAudio()
        val midi = openMidi[stringIndex] + position
        val nid = nativeId(id)
        NativeAudio.nebLiveStart(nid, midi, p.level, p.pressure, p.mute, c, volume, pluck, releaseMs)
        activeVoices[id] = NebVoice(stringIndex to position, p)
        if (doRecord) record(NebLoopEvent(0,0,nid,midi,p.level,p.pressure,p.mute,c,volume,pluck,releaseMs))
    }
    fun updateVoice(id: Long, stringIndex: Int, position: Int, p: NebProfile, c: Float, doRecord: Boolean = true) {
        if (stringIndex !in openMidi.indices) return
        val midi = openMidi[stringIndex] + position
        val old = activeVoices[id]
        val useProfile = if (pluck) old?.profile ?: p else p
        val nid = nativeId(id)
        if (old == null) {
            startVoice(id,stringIndex,position,useProfile,c,doRecord)
            return
        }
        if (pluck && old.cell.first != stringIndex) {
            NativeAudio.nebLiveStop(nid, releaseMs)
            NativeAudio.nebLiveStart(nid,midi,useProfile.level,useProfile.pressure,useProfile.mute,c,volume,pluck,releaseMs)
        } else {
            NativeAudio.nebLiveUpdate(nid,midi,useProfile.level,useProfile.pressure,useProfile.mute,c,volume)
        }
        activeVoices[id] = NebVoice(stringIndex to position, useProfile)
        if (doRecord) record(NebLoopEvent(0,1,nid,midi,useProfile.level,useProfile.pressure,useProfile.mute,c,volume,pluck,releaseMs))
    }
    fun stopVoice(id: Long, doRecord: Boolean = true) {
        val old = activeVoices.remove(id) ?: return
        val nid = nativeId(id)
        NativeAudio.nebLiveStop(nid, releaseMs)
        if (doRecord) record(NebLoopEvent(0,2,nid,0,old.profile.level,old.profile.pressure,old.profile.mute,0f,volume,pluck,releaseMs))
    }

    fun stopLoop() {
        loopJob?.cancel(); loopJob = null
        // Loop voices live in a separate id range.
        loopEvents.map { it.id + 1_000_000 }.distinct().forEach { NativeAudio.nebLiveStop(it, releaseMs) }
        if (loopState == "playing") loopState = "idle"
    }
    fun playLoop() {
        if (loopEvents.isEmpty() || loopDuration <= 0L) return
        stopLoop(); ensureAudio(); loopState = "playing"
        val snapshot = loopEvents.toList().sortedBy { it.t }
        val duration = loopDuration
        loopJob = scope.launch {
            while (isActive) {
                var last = 0L
                for (ev in snapshot) {
                    val wait = ev.t - last
                    if (wait > 0) delay(wait)
                    val id = ev.id + 1_000_000
                    when (ev.kind) {
                        0 -> NativeAudio.nebLiveStart(id,ev.midi,ev.level,ev.pressure,ev.mute,ev.cents,ev.volume,ev.pluck,ev.release)
                        1 -> NativeAudio.nebLiveUpdate(id,ev.midi,ev.level,ev.pressure,ev.mute,ev.cents,ev.volume)
                        2 -> NativeAudio.nebLiveStop(id,ev.release)
                    }
                    last = ev.t
                }
                val tail = duration - last
                if (tail > 0) delay(tail)
            }
        }
    }

    LaunchedEffect(delayOn,delayAmt,delayFb,delayTime,distOn,distAmt,distIn,distOut,verbOn,verbAmt,verbDecay) {
        ensureAudio()
        NativeAudio.setNebFx(delayOn,delayAmt,delayFb,delayTime,distOn,distAmt,distIn,distOut,verbOn,verbAmt,verbDecay)
    }

    DisposableEffect(Unit) {
        ensureAudio()
        onDispose { stopLoop(); restoreDaw() }
    }

    Dialog(
        onDismissRequest = { stopLoop(); restoreDaw(); vm.openInstrument(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(8,9,13)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "NEBULAR",
                        style = TextStyle(
                            brush = Brush.horizontalGradient(nebNoteColors),
                            fontSize = 27.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                            shadow = Shadow(Color.White.copy(alpha=.18f), blurRadius=12f)
                        )
                    )
                    NebBtn("Close") { stopLoop(); restoreDaw(); vm.openInstrument(null) }
                }

                NebCard("UI control section") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            NebBtn("Preset: $preset") { presetMenu = true }
                            DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                                nebPresets.keys.forEach { n -> DropdownMenuItem(text={Text(n)}, onClick={ presetMenu=false; applyPreset(n) }) }
                            }
                        }
                        NebToggle("Match scale", matchScale) { matchScale = it }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NebBtn("Reset Atoms") { resetAtoms() }
                        NebBtn(if (audioOn) "Audio: On" else "Audio: Off", on = audioOn) { audioOn = !audioOn; if (!audioOn) stopAll() }
                        NebBtn("Stop Notes") { stopAll() }
                    }
                }

                NebCard("Waveform behavior settings") {
                    NebSlider("Volume", volume, .05f..1f, { "%.2f".format(it) }) { volume = it }
                    NebSlider("Decay", releaseMs, 1f..5000f, { "${it.roundToInt()} ms" }) { releaseMs = it }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NebToggle("Pluck mode", pluck) { pluck = it }
                        NebToggle("Palm mute", palm) { palm = it }
                    }
                    NebToggle("Y-axis expression", yExpression) { yExpression = it }
                    NebSlider("Expression depth", expressionDepth, .2f..1f, { "%.2f".format(it) }) { expressionDepth = it }
                    NebToggle("X-axis pitch bend", xBend) { xBend = it }
                    NebSlider("Bend range", bendRange, 10f..50f, { "±${it.roundToInt()}¢" }) { bendRange = it }
                }

                NebCard("FX") {
                    NebToggle("Reverb", verbOn) { verbOn = it }
                    NebSlider("Reverb amount", verbAmt, 0f..1f, { "%.2f".format(it) }) { verbAmt = it }
                    NebSlider("Reverb decay", verbDecay, .3f..8f, { "%.1f s".format(it) }) { verbDecay = it }
                    NebToggle("Delay", delayOn) { delayOn = it }
                    NebSlider("Delay amount", delayAmt, 0f..1f, { "%.2f".format(it) }) { delayAmt = it }
                    NebSlider("Delay decay", delayFb, 0f..0.95f, { "%.2f".format(it) }) { delayFb = it }
                    NebSlider("Delay speed", delayTime, .05f..1.5f, { "%.2f s".format(it) }) { delayTime = it }
                    NebToggle("Distortion", distOn) { distOn = it }
                    NebSlider("Dist gain", distAmt, 0f..100f, { it.roundToInt().toString() }) { distAmt = it }
                    NebSlider("Dist in", distIn, 0f..3f, { "%.2f".format(it) }) { distIn = it }
                    NebSlider("Dist out", distOut, 0f..3f, { "%.2f".format(it) }) { distOut = it }
                }

                // The original Nebular page intentionally lets the atom bank and the
                // 25-position board extend beyond a phone viewport. Keep them in one
                // shared horizontal scroll region so the layout behaves like that page
                // instead of squeezing the instrument to fit the screen.
                val coreScroll = rememberScrollState()
                val atomCardW = 190.dp
                val atomsRowW = (72 + 190 * openMidi.size + 6 * (openMidi.size + 1)).dp
                val boardCardW = (56 + 36 * 25 + 20).dp
                val coreW = if (atomsRowW > boardCardW) atomsRowW else boardCardW

                Box(Modifier.fillMaxWidth().horizontalScroll(coreScroll)) {
                    Column(Modifier.width(coreW), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Phase atoms", color=Color.White.copy(alpha=.72f), fontSize=11.sp, fontWeight=FontWeight.Bold, letterSpacing=1.sp)
                        Row(Modifier.width(atomsRowW), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(34.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                NebBtn("+") {
                                    val first = openMidi.firstOrNull() ?: return@NebBtn
                                    val lower = nebFullStrings.filter { it < first }.maxOrNull() ?: max(12, first-7)
                                    openMidi.add(0,lower); atomSelections.add(0,emptySet()); preset="Custom"
                                }
                                NebBtn("−") { if (openMidi.size>1) { openMidi.removeAt(0); atomSelections.removeAt(0); preset="Custom"; stopAll() } }
                            }
                            openMidi.indices.forEach { i ->
                                NebAtomCard(
                                    midi=openMidi[i], selected=atomSelections[i],
                                    onSelect={ phase ->
                                        if (matchScale) {
                                            val targetPc = nebMod(openMidi[i] + nebPhaseSemitones[phase])
                                            val add = phase !in atomSelections[i]
                                            for (s in openMidi.indices) {
                                                val rel = nebMod(targetPc - nebMod(openMidi[s]))
                                                val pi = nebSemitoneToPhase[rel]
                                                val next = atomSelections[s].toMutableSet()
                                                if (add) next.add(pi) else next.remove(pi)
                                                atomSelections[s] = next
                                            }
                                        } else {
                                            val next = atomSelections[i].toMutableSet()
                                            if (!next.add(phase)) next.remove(phase)
                                            atomSelections[i] = next
                                        }
                                    },
                                    onOctDown={ openMidi[i]=(openMidi[i]-12).coerceAtLeast(12); preset="Custom"; stopAll() },
                                    onOctUp={ openMidi[i]=(openMidi[i]+12).coerceAtMost(107); preset="Custom"; stopAll() },
                                    onSemiDown={ openMidi[i]=(openMidi[i]-1).coerceAtLeast(12); preset="Custom"; stopAll() },
                                    onSemiUp={ openMidi[i]=(openMidi[i]+1).coerceAtMost(107); preset="Custom"; stopAll() }
                                )
                            }
                            Column(Modifier.width(34.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                NebBtn("+") {
                                    val last = openMidi.lastOrNull() ?: return@NebBtn
                                    val higher = nebFullStrings.filter { it > last }.minOrNull() ?: min(107,last+7)
                                    openMidi.add(higher); atomSelections.add(emptySet()); preset="Custom"
                                }
                                NebBtn("−") { if (openMidi.size>1) { openMidi.removeAt(openMidi.lastIndex); atomSelections.removeAt(atomSelections.lastIndex); preset="Custom"; stopAll() } }
                            }
                        }

                        NebCard("Flat board · ${openMidi.size} strings · 25 positions", modifier=Modifier.width(boardCardW)) {
                            Text("Touch, hold, slide", color=Color.White.copy(alpha=.55f), fontSize=10.sp)
                            NebBoard(
                                openMidi=openMidi.toList(), atoms=atomSelections.toList(), active=activeVoices,
                                pluck=pluck,
                                profileForY={ profile(it) }, centsForX={ cents(it) },
                                onDown={ id,s,pos,p,c -> startVoice(id,s,pos,p,c) },
                                onMove={ id,s,pos,p,c -> updateVoice(id,s,pos,p,c) },
                                onUp={ id -> stopVoice(id) }
                            )
                        }

                        Row(Modifier.width(boardCardW), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically) {
                            NebBtn(
                                when(loopState){"recording"->"Loop: Finish";"playing"->"Loop: Re-record";else->if(loopEvents.isEmpty())"Loop: Off" else "Loop: Record New"},
                                on = loopState != "idle"
                            ) {
                                when(loopState) {
                                    "recording" -> { loopDuration = max(1L,SystemClock.uptimeMillis()-loopStart); loopState="idle" }
                                    "playing" -> { stopLoop(); loopEvents.clear(); loopStart=SystemClock.uptimeMillis(); loopDuration=0; loopState="recording" }
                                    else -> { stopLoop(); loopEvents.clear(); loopStart=SystemClock.uptimeMillis(); loopDuration=0; loopState="recording" }
                                }
                            }
                            NebBtn("Play Loop", on=loopState=="playing") { if (loopState=="playing") stopLoop() else playLoop() }
                            NebBtn("Stop Loop") { stopLoop() }
                            Text(
                                when { loopState=="recording" -> "Recording loop…"; loopState=="playing" -> "Looping %.2f s".format(loopDuration/1000f); loopEvents.isNotEmpty() -> "Loop ready · %.2f s".format(loopDuration/1000f); else -> "No loop recorded" },
                                color=Color.White.copy(alpha=.58f), fontSize=9.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun NebCard(title: String, modifier: Modifier = Modifier.fillMaxWidth(), content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color(255,255,255,10)).border(1.dp,Color.White.copy(alpha=.12f),RoundedCornerShape(18.dp)).padding(10.dp),
        verticalArrangement=Arrangement.spacedBy(7.dp)
    ) {
        Text(title.uppercase(), color=Color.White.copy(alpha=.7f), fontSize=10.sp, fontWeight=FontWeight.Bold, letterSpacing=1.sp)
        content()
    }
}

@Composable
private fun NebBtn(text:String, on:Boolean=false, action:()->Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if(on) Color.White.copy(alpha=.14f) else Color.Transparent)
            .border(1.dp,Color.White.copy(alpha=.14f),shape)
            .clickable(onClick=action)
            .padding(horizontal=7.dp,vertical=3.dp),
        contentAlignment = Alignment.Center
    ) { Text(text,fontSize=9.sp,color=Color.White, maxLines=1) }
}

@Composable
private fun NebToggle(label:String, value:Boolean, onChange:(Boolean)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        Switch(value,onChange)
        Text(label,color=Color.White.copy(alpha=.78f),fontSize=9.sp)
    }
}

@Composable
private fun NebSlider(label:String, value:Float, range:ClosedFloatingPointRange<Float>, format:(Float)->String, onChange:(Float)->Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(label,color=Color.White.copy(alpha=.78f),fontSize=9.sp)
            Text(format(value),color=Color.White.copy(alpha=.55f),fontSize=9.sp)
        }
        Slider(value=value,onValueChange=onChange,valueRange=range,modifier=Modifier.height(20.dp))
    }
}

@Composable
private fun NebAtomCard(
    midi:Int, selected:Set<Int>, onSelect:(Int)->Unit,
    onOctDown:()->Unit,onOctUp:()->Unit,onSemiDown:()->Unit,onSemiUp:()->Unit
) {
    Column(
        Modifier.width(190.dp).clip(RoundedCornerShape(18.dp)).background(Color(255,255,255,8)).border(1.dp,Color.White.copy(alpha=.12f),RoundedCornerShape(18.dp)).padding(8.dp),
        horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(3.dp)
    ) {
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){ NebBtn("Oct−",action=onOctDown); NebBtn("Oct+",action=onOctUp) }
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){ NebBtn("−",action=onSemiDown); NebBtn("+",action=onSemiUp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            Column { Text("${nebLabel(midi)} string",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold); Text("12 o’clock locked to open string",color=Color.White.copy(alpha=.46f),fontSize=9.sp, maxLines=1) }
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(nebPcColor(midi)))
        }
        Canvas(
            Modifier.size(118.dp).pointerInput(midi,selected) {
                detectTapGestures { o ->
                    val cx=size.width/2f; val cy=size.height/2f; val r=size.width*.35f
                    var best=-1; var bestD=Float.MAX_VALUE
                    for(i in 0..11){ val a=-PI/2+i*(2*PI/12); val x=cx+(r*cos(a)).toFloat(); val y=cy+(r*sin(a)).toFloat(); val d=(o.x-x)*(o.x-x)+(o.y-y)*(o.y-y); if(d<bestD){bestD=d;best=i} }
                    if(best>=0 && sqrt(bestD)<size.width*.12f) onSelect(best)
                }
            }
        ) {
            val c=Offset(size.width/2,size.height/2); val r=size.width*.35f; val nr=size.width*.075f
            drawCircle(color=Color.White.copy(alpha=.12f), radius=r+8f, center=c, style=Stroke(2f))
            val p=Paint().apply { textAlign=Paint.Align.CENTER; textSize=size.width*.075f; isAntiAlias=true }
            for(i in 0..11){
                val a=-PI/2+i*(2*PI/12); val pt=Offset(c.x+(r*cos(a)).toFloat(),c.y+(r*sin(a)).toFloat()); val active=i in selected
                drawCircle(if(active) Color.White.copy(alpha=.12f) else Color.Black.copy(alpha=.18f),nr,pt)
                drawCircle(if(active) nebPcColor(midi) else Color.White.copy(alpha=.24f),nr,pt,style=Stroke(2f))
                p.color=(if(active) android.graphics.Color.WHITE else android.graphics.Color.argb(135,255,255,255))
                drawContext.canvas.nativeCanvas.drawText(nebPhaseLabels[i],pt.x,pt.y+p.textSize*.34f,p)
            }
        }
    }
}

@Composable
private fun NebBoard(
    openMidi:List<Int>, atoms:List<Set<Int>>, active:Map<Long,NebVoice>, pluck:Boolean,
    profileForY:(Float)->NebProfile, centsForX:(Float)->Float,
    onDown:(Long,Int,Int,NebProfile,Float)->Unit,
    onMove:(Long,Int,Int,NebProfile,Float)->Unit,
    onUp:(Long)->Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val labelW=56.dp; val cellW=36.dp; val headerH=26.dp; val cellH=54.dp
    val totalW=labelW+cellW*25; val totalH=headerH+cellH*openMidi.size
    val labelPx=with(density){labelW.toPx()}; val cellPx=with(density){cellW.toPx()}; val headerPx=with(density){headerH.toPx()}; val rowPx=with(density){cellH.toPx()}
    val pressed = active.values.map { it.cell }.toSet()

    Canvas(
        Modifier.width(totalW).height(totalH).pointerInput(openMidi, pluck) {
            awaitPointerEventScope {
                while (true) {
                    val event=awaitPointerEvent(PointerEventPass.Main)
                    event.changes.forEach { ch ->
                        val id=ch.id.value
                        val x=ch.position.x; val y=ch.position.y
                        val inside=x>=labelPx && x<labelPx+25*cellPx && y>=headerPx && y<headerPx+openMidi.size*rowPx
                        val pos=if(inside) floor((x-labelPx)/cellPx).toInt().coerceIn(0,24) else -1
                        val displayRow=if(inside) floor((y-headerPx)/rowPx).toInt().coerceIn(0,openMidi.lastIndex) else -1
                        val s=if(inside) openMidi.lastIndex-displayRow else -1
                        val xf=if(inside) ((x-labelPx-pos*cellPx)/cellPx).coerceIn(0f,1f) else .5f
                        val yf=if(inside) ((y-headerPx-displayRow*rowPx)/rowPx).coerceIn(0f,1f) else .5f
                        if(ch.pressed && !ch.previousPressed && inside) onDown(id,s,pos,profileForY(yf),centsForX(xf))
                        else if(ch.pressed && ch.previousPressed) {
                            val old=active[id]?.cell
                            if(!inside) { if(old!=null) onUp(id) }
                            else if(old==null || old.first!=s || old.second!=pos) onMove(id,s,pos,profileForY(yf),centsForX(xf))
                            else if(!pluck) onMove(id,s,pos,profileForY(yf),centsForX(xf))
                            else onMove(id,s,pos,active[id]?.profile ?: profileForY(yf),centsForX(xf))
                        } else if(!ch.pressed && ch.previousPressed) onUp(id)
                        if(ch.pressed || ch.previousPressed) ch.consume()
                    }
                }
            }
        }
    ) {
        drawRect(Color(8,9,13))
        val text=Paint().apply { isAntiAlias=true; textAlign=Paint.Align.CENTER; color=android.graphics.Color.argb(150,255,255,255) }
        text.textSize=with(density){12.sp.toPx()}
        for(pos in 0..24){ val x=labelPx+pos*cellPx; drawRect(Color.White.copy(alpha=.035f),Offset(x,0f),androidx.compose.ui.geometry.Size(cellPx,headerPx)); text.color=android.graphics.Color.argb(150,255,255,255); drawContext.canvas.nativeCanvas.drawText(nebDozenal(pos),x+cellPx/2,headerPx*.7f,text) }
        val stringStroke=with(density){2.dp.toPx()}
        val glowStroke=with(density){7.dp.toPx()}
        val activeR=with(density){10.dp.toPx()}
        val activeBorder=with(density){1.dp.toPx()}
        openMidi.indices.reversed().forEachIndexed { displayRow,s ->
            val y=headerPx+displayRow*rowPx
            text.textSize=with(density){15.sp.toPx()}; text.color=android.graphics.Color.argb(200,255,255,255)
            drawContext.canvas.nativeCanvas.drawText(nebLabel(openMidi[s]),labelPx/2,y+rowPx*.58f,text)
            for(pos in 0..24){
                val x=labelPx+pos*cellPx; val midi=openMidi[s]+pos; val pc=nebMod(midi); val color=nebNoteColors[pc]
                val cy=y+rowPx/2
                val cx=x+cellPx/2
                val isPressed=(s to pos) in pressed
                drawRect(Color.White.copy(alpha=.05f),Offset(x,y),androidx.compose.ui.geometry.Size(cellPx,rowPx),style=Stroke(1f))

                // Original Nebular fretboard: a pale string crosses every cell and a
                // short glowing pitch-color segment sits in the exact center.
                drawLine(Color.White.copy(alpha=.38f),Offset(x+cellPx*.05f,cy),Offset(x+cellPx*.95f,cy),stringStroke,cap=StrokeCap.Round)
                drawLine(color.copy(alpha=.16f),Offset(cx-cellPx*.18f,cy),Offset(cx+cellPx*.18f,cy),glowStroke,cap=StrokeCap.Round)
                drawLine(color,Offset(cx-cellPx*.18f,cy),Offset(cx+cellPx*.18f,cy),stringStroke,cap=StrokeCap.Round)

                val phase=nebSemitoneToPhase[nebMod(pc-nebMod(openMidi[s]))]
                if(s<atoms.size && phase in atoms[s]) {
                    val r=if(isPressed) activeR*1.35f else activeR
                    drawCircle(color.copy(alpha=.10f),r*1.8f,Offset(cx,cy))
                    drawCircle(color.copy(alpha=.20f),r*1.35f,Offset(cx,cy))
                    drawCircle(color,r,Offset(cx,cy))
                    drawCircle(Color.White.copy(alpha=.30f),r,Offset(cx,cy),style=Stroke(activeBorder))
                }
                if(isPressed){
                    drawRect(Color.White.copy(alpha=.18f),Offset(x+1,y+1),androidx.compose.ui.geometry.Size(cellPx-2,rowPx-2),style=Stroke(2f))
                    text.textSize=with(density){12.sp.toPx()}; text.color=android.graphics.Color.argb(220,255,255,255)
                    drawContext.canvas.nativeCanvas.drawText(nebNoteNames[pc],x+cellPx*.76f,y+rowPx*.86f,text)
                }
            }
        }
    }
}
