package com.sonolume.spectra.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SpectraBg = Color(0xFF08090D)
val SpectraPanel = Color(0xFF141926)
val SpectraBorder = Color(0xFF263048)
val SpectraText = Color(0xFFE8EAF0)
val SpectraMuted = Color(0x99FFFFFF)
val SpectraAccent = Color(0xFF7CC8FF)
val SpectraGreen = Color(0xFF00C853)
val SpectraAmber = Color(0xFFFFC400)

private val Scheme = darkColorScheme(
    primary = SpectraAccent,
    onPrimary = Color.Black,
    background = SpectraBg,
    onBackground = SpectraText,
    surface = SpectraPanel,
    onSurface = SpectraText,
    surfaceVariant = Color(0xFF1D2435),
    onSurfaceVariant = SpectraMuted,
    outline = SpectraBorder
)

@Composable
fun SpectraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
