package com.sonolume.spectra.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonolume.spectra.daw.DawUiState
import com.sonolume.spectra.daw.DawViewModel

@Composable
fun DawViewModel.collectUi(): DawUiState {
    val v by ui.collectAsStateWithLifecycle()
    return v
}

@Composable
fun TrackHead(
    title: String,
    tag: String,
    folded: Boolean,
    onFold: () -> Unit,
    onOpenInstrument: () -> Unit,
    onClear: () -> Unit,
    fxOpen: Boolean,
    onFx: () -> Unit,
    mute: Boolean,
    solo: Boolean,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    controls: @Composable RowScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallBtn(if (folded) "▸" else "▾", onFold)
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SpectraText)
            Text(tag, fontSize = 9.sp, color = SpectraMuted)
        }
        if (!folded) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToggleBtn("Mute", mute) { onMute() }
                ToggleBtn("Solo", solo) { onSolo() }
                controls()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToggleBtn("FX", fxOpen) { onFx() }
                SmallBtn("Clear", onClear, warn = true)
                SmallBtn("Open instrument", onOpenInstrument)
            }
        }
    }
}

@Composable
fun SmallBtn(text: String, onClick: () -> Unit, warn: Boolean = false, on: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    val bg = when {
        on -> Color(0xFF00AA66)
        warn -> Color(0xFF5A2323)
        else -> Color(0xFF2A3248)
    }
    val stroke = when {
        on -> Color(0xFF00FF88)
        warn -> Color(0xFFAA3333)
        else -> Color(0xFF42506D)
    }
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, stroke), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 9.sp, color = SpectraText, maxLines = 1, softWrap = false)
    }
}

@Composable
fun SmallBtn(text: String, warn: Boolean = false, on: Boolean = false, onClick: () -> Unit) {
    SmallBtn(text, onClick, warn, on)
}

@Composable
fun ToggleBtn(text: String, on: Boolean, onClick: () -> Unit) {
    SmallBtn(text, onClick, on = on)
}

@Composable
fun VolSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 9.sp, color = SpectraMuted)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f).height(30.dp)
        )
    }
}

@Composable
fun FxCheckRow(name: String, on: Boolean, onToggle: (Boolean) -> Unit, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
            Checkbox(checked = on, onCheckedChange = onToggle)
            Text(name, fontSize = 9.sp, color = SpectraText)
        }
        Column(Modifier.weight(1f, fill = false)) { content() }
    }
}

@Composable
fun FxSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 9.sp, color = SpectraMuted)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(30.dp)
        )
    }
}

@Composable
fun Hint(text: String) {
    Text(text, fontSize = 9.sp, color = SpectraMuted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp))
}
