package com.sonolume.spectra.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonolume.spectra.daw.LinePt

data class Cell(val s: Int, val midi: Int, val half: Int = -1)

const val KIND_NONE = 0
const val KIND_HEAD = 1
const val KIND_TAIL = 2
const val KIND_ACC = 4
const val KIND_SOFT = 8
const val KIND_HALF1 = 16
const val KIND_HALF2 = 32

data class LineOverlay(
    val noteS: Int,
    val noteLen: Int,
    val midi: Int,
    val pts: List<LinePt>,
    val vex: Boolean,
    val locked: Boolean,
    val emptyVal: Float
)

class RollGeometry(
    val labelW: Float,
    val headerH: Float,
    val cell: Float,
    val rows: List<Int>,
    val steps: Int
) {
    fun cellAt(x: Float, y: Float, halfMode: Boolean = false): Cell? {
        if (x < labelW || y < headerH) return null
        val s = ((x - labelW) / cell).toInt()
        val r = ((y - headerH) / cell).toInt()
        if (s !in 0 until steps || r !in rows.indices) return null
        val half = if (halfMode) {
            if ((y - headerH) % cell < cell / 2f) 0 else 1
        } else -1
        return Cell(s, rows[r], half)
    }

    fun cellRect(c: Cell): Rect {
        val r = rows.indexOf(c.midi)
        if (r < 0) return Rect.Zero
        val l = labelW + c.s * cell
        val t = headerH + r * cell
        return Rect(l, t, l + cell, t + cell)
    }

    val width: Float get() = labelW + steps * cell
    val height: Float get() = headerH + rows.size * cell

    fun rowTop(midi: Int): Float {
        val r = rows.indexOf(midi)
        return if (r < 0) -1f else headerH + r * cell
    }
}

@Composable
fun PianoRoll(
    steps: Int,
    rows: List<Int>,
    labelFor: (Int) -> String,
    labelColorFor: (Int) -> Color,
    beatLen: Int,
    barLen: Int,
    playStep: Int,
    playing: Boolean,
    selected: (Int, Int) -> Boolean,
    cellKind: (Int, Int) -> Int,
    pitchColor: (Int) -> Color,
    ghost: Set<Pair<Int, Int>>,
    line: LineOverlay?,
    specMode: Boolean,
    onTap: (Cell) -> Unit,
    onDoubleTap: (Cell) -> Unit,
    onLongPress: (Cell) -> Unit,
    onLabelTap: (Int) -> Unit,
    onDragStart: (Cell) -> Boolean,
    onDragMove: (Cell) -> Unit,
    onDragEnd: (Boolean) -> Unit,
    onLineTap: (Float, Float) -> Unit,
    onPointDown: (Int) -> Unit,
    onPointMove: (Float, Float) -> Unit,
    onPointUp: () -> Unit,
    onPointDoubleTap: (Int) -> Unit,
    halfMode: Boolean = false,
    modifier: Modifier = Modifier,
    cellDp: Dp = 20.dp,
    labelDp: Dp = 44.dp
) {
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val measurer = rememberTextMeasurer()
    val latestRows by rememberUpdatedState(rows)
    val latestSteps by rememberUpdatedState(steps)
    val latestLine by rememberUpdatedState(line)
    val latestTap by rememberUpdatedState(onTap)
    val latestDouble by rememberUpdatedState(onDoubleTap)
    val latestLong by rememberUpdatedState(onLongPress)
    val latestLabel by rememberUpdatedState(onLabelTap)
    val latestDragStart by rememberUpdatedState(onDragStart)
    val latestDragMove by rememberUpdatedState(onDragMove)
    val latestDragEnd by rememberUpdatedState(onDragEnd)
    val latestLineTap by rememberUpdatedState(onLineTap)
    val latestPointDown by rememberUpdatedState(onPointDown)
    val latestPointMove by rememberUpdatedState(onPointMove)
    val latestPointUp by rememberUpdatedState(onPointUp)
    val latestPointDouble by rememberUpdatedState(onPointDoubleTap)
    val latestHalfMode by rememberUpdatedState(halfMode)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(max = 438.dp)
            .horizontalScroll(hScroll)
            .verticalScroll(vScroll)
    ) {
        Canvas(
            Modifier
                .size(
                    width = labelDp + cellDp * steps,
                    height = 16.dp + cellDp * rows.size
                )
                .pointerInput(hScroll, vScroll) {
                    var lastTapAt = 0L
                    var lastTapKey: Any? = null
                    awaitEachGestureWrapper(
                        hScroll, vScroll, labelDp, cellDp,
                        { latestHalfMode },
                        { latestRows }, { latestSteps }, { latestLine },
                        latestTapGetter = { latestTap },
                        latestDoubleGetter = { latestDouble },
                        latestLongGetter = { latestLong },
                        latestLabelGetter = { latestLabel },
                        latestDragStartGetter = { latestDragStart },
                        latestDragMoveGetter = { latestDragMove },
                        latestDragEndGetter = { latestDragEnd },
                        latestLineTapGetter = { latestLineTap },
                        latestPointDownGetter = { latestPointDown },
                        latestPointMoveGetter = { latestPointMove },
                        latestPointUpGetter = { latestPointUp },
                        latestPointDoubleGetter = { latestPointDouble },
                        tapMemory = { lastTapAt to lastTapKey },
                        saveTap = { t, k -> lastTapAt = t; lastTapKey = k }
                    )
                }
        ) {
            val labelW = labelDp.toPx()
            val headerH = 16.dp.toPx()
            val cell = cellDp.toPx()
            val g = RollGeometry(labelW, headerH, cell, rows, steps)
            drawRollBase(measurer, g, labelFor, labelColorFor, beatLen, barLen)
            for (r in rows.indices) {
                val midi = rows[r]
                for (s in 0 until steps) {
                    val kind = cellKind(s, midi)
                    val isGhost = ghost.contains(Pair(s, midi))
                    val isSel = selected(s, midi)
                    val isPlay = playing && s == playStep
                    if (kind == KIND_NONE && !isGhost && !isSel && !isPlay) continue
                    drawRollCell(g, Cell(s, midi), kind, pitchColor(midi), specMode, isSel, isGhost, isPlay)
                }
            }
            if (line != null) drawLineOverlay(g, line)
            drawStickyChrome(
                measurer = measurer,
                g = g,
                labelFor = labelFor,
                labelColorFor = labelColorFor,
                beatLen = beatLen,
                barLen = barLen,
                scrollX = hScroll.value.toFloat(),
                scrollY = vScroll.value.toFloat()
            )
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.awaitEachGestureWrapper(
    hScroll: ScrollState,
    vScroll: ScrollState,
    labelDp: Dp,
    cellDp: Dp,
    halfModeGetter: () -> Boolean,
    rowsGetter: () -> List<Int>,
    stepsGetter: () -> Int,
    lineGetter: () -> LineOverlay?,
    latestTapGetter: () -> (Cell) -> Unit,
    latestDoubleGetter: () -> (Cell) -> Unit,
    latestLongGetter: () -> (Cell) -> Unit,
    latestLabelGetter: () -> (Int) -> Unit,
    latestDragStartGetter: () -> (Cell) -> Boolean,
    latestDragMoveGetter: () -> (Cell) -> Unit,
    latestDragEndGetter: () -> (Boolean) -> Unit,
    latestLineTapGetter: () -> (Float, Float) -> Unit,
    latestPointDownGetter: () -> (Int) -> Unit,
    latestPointMoveGetter: () -> (Float, Float) -> Unit,
    latestPointUpGetter: () -> () -> Unit,
    latestPointDoubleGetter: () -> (Int) -> Unit,
    tapMemory: () -> Pair<Long, Any?>,
    saveTap: (Long, Any?) -> Unit
) {
    awaitEachGesture {
        val rows = rowsGetter()
        val steps = stepsGetter()
        val line = lineGetter()
        val labelW = labelDp.toPx()
        val headerH = 16.dp.toPx()
        val cell = cellDp.toPx()
        val g = RollGeometry(labelW, headerH, cell, rows, steps)
        val halfMode = halfModeGetter()
        val down = awaitFirstDown()
        val pos = down.position
        val scrollX = hScroll.value.toFloat()
        val scrollY = vScroll.value.toFloat()
        if (pos.y >= scrollY && pos.y < scrollY + headerH) return@awaitEachGesture
        if (pos.x >= scrollX && pos.x < scrollX + labelW && pos.y >= scrollY + headerH) {
            val r = ((pos.y - headerH) / cell).toInt()
            if (r in rows.indices) latestLabelGetter()(rows[r])
            return@awaitEachGesture
        }
        val phit = linePointHit(g, line, pos, cell)
        if (phit != null) {
            val pointNow = System.currentTimeMillis()
            val pointKey: Any = Pair("linePoint", phit)
            val (lastPointAt, lastPointKey) = tapMemory()
            if (pointNow - lastPointAt < 300 && lastPointKey == pointKey) {
                saveTap(0L, null)
                latestPointDoubleGetter()(phit)
                return@awaitEachGesture
            }
            latestPointDownGetter()(phit)
            var pointMoved = false
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Main)
                if (ev.changes.size >= 2) break
                val change = ev.changes.firstOrNull() ?: break
                if (change.changedToUp()) {
                    change.consume()
                    break
                }
                if ((change.position - change.previousPosition).getDistance() > 2f) pointMoved = true
                val ln = lineGetter() ?: break
                val (t, v) = lineXYToTV(g, ln, change.position, cell)
                latestPointMoveGetter()(t, v)
                change.consume()
            }
            latestPointUpGetter()()
            if (!pointMoved) saveTap(pointNow, pointKey)
            return@awaitEachGesture
        }
        val ltap = linePathHit(g, line, pos, cell)
        if (ltap != null) {
            latestLineTapGetter()(ltap.first, ltap.second)
            return@awaitEachGesture
        }
        val cellHit = g.cellAt(pos.x, pos.y, halfMode) ?: return@awaitEachGesture
        val now = System.currentTimeMillis()
        val key: Any = Triple(cellHit.s, cellHit.midi, cellHit.half)
        val (lastAt, lastKey) = tapMemory()
        if (now - lastAt < 300 && lastKey == key) {
            saveTap(0L, null)
            latestDoubleGetter()(cellHit)
            return@awaitEachGesture
        }
        // Do not enter drag mode on pointer-down. Starting the drag immediately used
        // to cancel long-press for Nebular/Spectrachord because those tracks always
        // returned true from onDragStart. Dragging now begins only after real motion.
        var dragOn = false
        var moved = false
        var longFired = false
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Main)
            if (ev.changes.size >= 2) {
                panTwoFinger(hScroll, vScroll)
                moved = true
                longFired = true
                break
            }
            val change = ev.changes.firstOrNull() ?: break
            if (change.changedToUp()) {
                change.consume()
                if (!moved && System.currentTimeMillis() - now >= 450L) {
                    longFired = true
                    latestLongGetter()(cellHit)
                }
                break
            }
            val d = (change.position - change.previousPosition).getDistance()
            if (d > 2f) {
                if (!dragOn) dragOn = latestDragStartGetter()(cellHit)
                val c2 = g.cellAt(change.position.x, change.position.y, halfMode)
                if (c2 != null) {
                    if (dragOn) latestDragMoveGetter()(c2)
                    moved = true
                }
                change.consume()
            }
        }
        if (dragOn) {
            latestDragEndGetter()(moved)
        } else if (!moved && !longFired) {
            saveTap(now, key)
            latestTapGetter()(cellHit)
        }
    }
}

private fun linePointHit(g: RollGeometry, line: LineOverlay?, pos: Offset, cell: Float): Int? {
    if (line == null || line.locked) return null
    val startX = g.labelW + line.noteS * cell
    val w = cell * maxOf(1, line.noteLen)
    val baseTop = g.rowTop(line.midi)
    if (baseTop < 0) return null
    val midY = baseTop + cell / 2f
    line.pts.forEachIndexed { idx, p ->
        val y = if (line.vex) {
            midY + 1.5f * cell - (p.v.coerceIn(0f, 1.15f) / 1.15f) * 7f * cell
        } else {
            midY - p.v.coerceIn(-12f, 12f) * cell
        }
        if ((Offset(startX + p.t * w, y) - pos).getDistance() < 34f) return idx
    }
    return null
}

private fun linePathHit(g: RollGeometry, line: LineOverlay?, pos: Offset, cell: Float): Pair<Float, Float>? {
    if (line == null || line.locked) return null
    val startX = g.labelW + line.noteS * cell
    val w = cell * maxOf(1, line.noteLen)
    if (pos.x < startX || pos.x > startX + w) return null
    val baseTop = g.rowTop(line.midi)
    if (baseTop < 0) return null
    val t = ((pos.x - startX) / w).coerceIn(0f, 1f)
    val midY = baseTop + cell / 2f
    val v = if (line.vex) {
        ((midY + 1.5f * cell - pos.y) / cell / 7f * 1.15f).coerceIn(0f, 1.15f)
    } else {
        ((midY - pos.y) / cell).coerceIn(-12f, 12f)
    }
    return Pair(t, v)
}

private fun lineXYToTV(g: RollGeometry, line: LineOverlay, pos: Offset, cell: Float): Pair<Float, Float> {
    val startX = g.labelW + line.noteS * cell
    val w = cell * maxOf(1, line.noteLen)
    val t = ((pos.x - startX) / w).coerceIn(0f, 1f)
    val baseTop = g.rowTop(line.midi)
    val midY = baseTop + cell / 2f
    val v = if (line.vex) {
        ((midY + 1.5f * cell - pos.y) / cell / 7f * 1.15f).coerceIn(0f, 1.15f)
    } else {
        ((midY - pos.y) / cell).coerceIn(-12f, 12f)
    }
    return Pair(t, v)
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.panTwoFinger(
    hScroll: ScrollState,
    vScroll: ScrollState
) {
    while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Main)
        if (ev.changes.size < 2) break
        var dx = 0f
        var dy = 0f
        var n = 0
        for (c in ev.changes) {
            val d = c.previousPosition - c.position
            dx += d.x
            dy += d.y
            n++
            c.consume()
        }
        if (n > 0) {
            hScroll.dispatchRawDelta(dx / n)
            vScroll.dispatchRawDelta(dy / n)
        }
        if (ev.changes.all { it.changedToUp() }) break
    }
}

private fun DrawScope.drawStickyChrome(
    measurer: TextMeasurer,
    g: RollGeometry,
    labelFor: (Int) -> String,
    labelColorFor: (Int) -> Color,
    beatLen: Int,
    barLen: Int,
    scrollX: Float,
    scrollY: Float
) {
    val headerY = scrollY
    val labelX = scrollX

    drawRect(Color(0xFF0D1119), Offset(scrollX, headerY), Size(size.width, g.headerH))
    val barStyle = TextStyle(fontSize = 9.sp, color = Color(0x99FFFFFF))
    var b = 0
    while (b * barLen < g.steps) {
        val s0 = b * barLen
        val span = minOf(barLen, g.steps - s0)
        val x = g.labelW + s0 * g.cell
        drawRect(Color(0xFF1A2233), Offset(x, headerY), Size(span * g.cell, g.headerH))
        val layout = measurer.measure((b + 1).toString(), barStyle)
        drawText(layout, topLeft = Offset(x + 4f, headerY + (g.headerH - layout.size.height) / 2f))
        b++
    }
    for (step in 0 until g.steps) {
        val x = g.labelW + step * g.cell
        if (step % barLen == 0) {
            drawLine(Color(0xFF3A4A6B), Offset(x, headerY), Offset(x, headerY + g.headerH), 1.5f)
        } else if (step % beatLen == 0) {
            drawLine(Color(0xFF232D42), Offset(x, headerY), Offset(x, headerY + g.headerH), 1f)
        }
    }

    val rowStyle = TextStyle(fontSize = 10.sp)
    for (r in g.rows.indices) {
        val midi = g.rows[r]
        val y = g.headerH + r * g.cell
        drawRect(Color(0xFF10141D), Offset(labelX, y), Size(g.labelW, g.cell))
        val layout = measurer.measure(labelFor(midi), rowStyle.copy(color = labelColorFor(midi)))
        drawText(layout, topLeft = Offset(labelX + g.labelW - layout.size.width - 4f, y + (g.cell - layout.size.height) / 2f))
        drawLine(Color(0xFF263048), Offset(labelX, y + g.cell - 1f), Offset(labelX + g.labelW, y + g.cell - 1f), 1f)
    }

    drawRect(Color(0xFF0D1119), Offset(labelX, headerY), Size(g.labelW, g.headerH))
    drawLine(Color(0xFF263048), Offset(labelX, headerY + g.headerH - 1f), Offset(labelX + g.labelW, headerY + g.headerH - 1f), 1f)
    drawLine(Color(0xFF222A3D), Offset(labelX + g.labelW - 1f, headerY), Offset(labelX + g.labelW - 1f, headerY + g.headerH), 1f)
}

private fun DrawScope.drawRollBase(
    measurer: TextMeasurer,
    g: RollGeometry,
    labelFor: (Int) -> String,
    labelColorFor: (Int) -> Color,
    beatLen: Int,
    barLen: Int
) {
    drawRect(Color(0xFF08090D), Offset.Zero, Size(g.width, g.height))
    val barStyle = TextStyle(fontSize = 9.sp, color = Color(0x99FFFFFF))
    var b = 0
    while (b * barLen < g.steps) {
        val s0 = b * barLen
        val span = minOf(barLen, g.steps - s0)
        val x = g.labelW + s0 * g.cell
        drawRect(Color(0xFF1A2233), Offset(x, 0f), Size(span * g.cell, g.headerH))
        val layout = measurer.measure((b + 1).toString(), barStyle)
        drawText(layout, topLeft = Offset(x + 4f, (g.headerH - layout.size.height) / 2f))
        b++
    }
    val rowStyle = TextStyle(fontSize = 10.sp)
    for (r in g.rows.indices) {
        val midi = g.rows[r]
        val y = g.headerH + r * g.cell
        drawRect(Color(0xFF10141D), Offset(0f, y), Size(g.labelW, g.cell))
        drawRect(
            labelColorFor(midi).copy(alpha = 0.15f),
            Offset(g.labelW, y),
            Size(g.width - g.labelW, g.cell)
        )
        val layout = measurer.measure(labelFor(midi), rowStyle.copy(color = labelColorFor(midi)))
        drawText(
            layout,
            topLeft = Offset(g.labelW - layout.size.width - 4f, y + (g.cell - layout.size.height) / 2f)
        )
    }
    for (s in 0 until g.steps) {
        val x = g.labelW + s * g.cell
        drawRect(Color(0x0DFFFFFF), Offset(x, g.headerH), Size(1f, g.height - g.headerH))
        if (s % barLen == 0) {
            drawRect(Color(0xFF3A4A6B), Offset(x, 0f), Size(1.5f, g.height))
        } else if (s % beatLen == 0) {
            drawRect(Color(0xFF232D42), Offset(x, 0f), Size(1f, g.height))
        }
    }
    for (r in 0..g.rows.size) {
        val y = g.headerH + r * g.cell
        drawRect(Color(0xFF263048), Offset(0f, y), Size(g.width, 1f))
    }
}

private fun DrawScope.drawRollCell(
    g: RollGeometry,
    c: Cell,
    kind: Int,
    pitch: Color,
    specMode: Boolean,
    sel: Boolean,
    isGhost: Boolean,
    isPlay: Boolean
) {
    val rect = g.cellRect(c)
    if (isPlay) {
        drawRect(Color(0x1FFFFFFF), Offset(rect.left, rect.top), Size(rect.width, rect.height))
    }
    if (isGhost) {
        drawRect(Color(0x737CFFBE), Offset(rect.left + 1, rect.top + 1), Size(rect.width - 2, rect.height - 2))
        return
    }
    if (kind == KIND_NONE) {
        if (sel) {
            drawRect(Color(0xFF7DFFBE), Offset(rect.left, rect.top), Size(rect.width, rect.height), style = Stroke(2f))
        }
        return
    }
    if (specMode) {
        val half = rect.height / 2f
        if (kind and KIND_HALF1 != 0) {
            drawRect(
                pitch.copy(alpha = if (kind and KIND_HEAD != 0) 0.85f else 0.3f),
                Offset(rect.left + 1, rect.top + 1),
                Size(rect.width - 2, half - 1)
            )
        }
        if (kind and KIND_HALF2 != 0) {
            drawRect(
                pitch.copy(alpha = if (kind and KIND_HEAD != 0) 0.85f else 0.3f),
                Offset(rect.left + 1, rect.top + half),
                Size(rect.width - 2, half - 1)
            )
        }
    } else {
        val alpha = when {
            kind and KIND_HEAD != 0 -> if (kind and KIND_SOFT != 0) 0.55f else 1f
            else -> 0.42f
        }
        drawRect(pitch.copy(alpha = alpha), Offset(rect.left + 1, rect.top + 1), Size(rect.width - 2, rect.height - 2))
        if (kind and KIND_HEAD != 0 && kind and KIND_ACC != 0) {
            drawRect(Color.White, Offset(rect.left + 1, rect.top + 1), Size(rect.width - 2, rect.height - 2), style = Stroke(1.5f))
        }
    }
    if (sel) {
        drawRect(Color(0xFF7DFFBE), Offset(rect.left, rect.top), Size(rect.width, rect.height), style = Stroke(2.5f))
    }
}

private fun lineVal(pts: List<LinePt>, t: Float, def: Float): Float {
    if (pts.isEmpty()) return def
    if (t <= pts[0].t) return pts[0].v
    for (i in 1 until pts.size) {
        if (t <= pts[i].t) {
            val a = pts[i - 1]
            val b = pts[i]
            val k = (t - a.t) / maxOf(1e-6f, b.t - a.t)
            return a.v + (b.v - a.v) * k
        }
    }
    return pts.last().v
}

private fun DrawScope.drawLineOverlay(g: RollGeometry, line: LineOverlay) {
    val cell = g.cell
    val startX = g.labelW + line.noteS * cell
    val w = cell * maxOf(1, line.noteLen)
    val baseTop = g.rowTop(line.midi)
    if (baseTop < 0) return
    val midY = baseTop + cell / 2f
    fun toY(v: Float): Float {
        return if (line.vex) {
            midY + 1.5f * cell - (v.coerceIn(0f, 1.15f) / 1.15f) * 7f * cell
        } else {
            midY - v.coerceIn(-12f, 12f) * cell
        }
    }
    val col = if (line.vex) Color(0xFFFFC400) else Color(0xFF00E676)
    var prev = Offset(startX, toY(lineVal(line.pts, 0f, line.emptyVal)))
    for (i in 1..24) {
        val t = i / 24f
        val p = Offset(startX + t * w, toY(lineVal(line.pts, t, line.emptyVal)))
        drawLine(col, prev, p, strokeWidth = 3f)
        prev = p
    }
    for (p in line.pts) {
        drawCircle(Color.Black, 13f, Offset(startX + p.t * w, toY(p.v)))
        drawCircle(col, 10f, Offset(startX + p.t * w, toY(p.v)))
    }
}
