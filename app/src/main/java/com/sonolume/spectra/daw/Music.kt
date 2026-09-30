package com.sonolume.spectra.daw

import kotlin.math.pow

const val NEB_LO = 29
const val NEB_HI = 74
const val SPEC_LO = 26
const val SPEC_HI = 98

val KICKS = intArrayOf(40, 41, 42, 43, 44, 45, 46)
val SNARES = intArrayOf(47, 48, 49, 50, 51, 52, 53)
val CYMS = intArrayOf(54, 55, 56, 57, 58, 59, 60)

val PITCH_COLORS = longArrayOf(
    0xFF3AFF00, 0xFF00FFEC, 0xFF008FFF, 0xFF0F00FB,
    0xFF6300BE, 0xFF6E0080, 0xFF980000, 0xFFC80000,
    0xFFF30000, 0xFFFF7800, 0xFFFFEF00, 0xFFAAFF00
)

private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

fun midiName(m: Int): String {
    val pc = ((m % 12) + 12) % 12
    return NOTE_NAMES[pc] + (m / 12 - 1)
}

fun midiFreq(m: Int): Float = (440.0 * 2.0.pow((m - 69) / 12.0)).toFloat()

fun drumKind(m: Int): Int = when {
    KICKS.contains(m) -> 0
    SNARES.contains(m) -> 1
    else -> 2
}

fun drumKindLetter(m: Int): String = when (drumKind(m)) {
    0 -> "K"
    1 -> "S"
    else -> "C"
}

private const val VEL_LO = 0.05f
private const val VEL_HI = 1.15f
private const val VEL_B1 = VEL_LO + (VEL_HI - VEL_LO) / 3f
private const val VEL_B2 = VEL_LO + 2f * (VEL_HI - VEL_LO) / 3f

fun drumZoneIdx(vel: Float): Int {    val v = vel.coerceIn(VEL_LO, VEL_HI)
    return when {
        v < VEL_B1 -> 0
        v < VEL_B2 -> 1
        else -> 2
    }
}

fun drumPos(kind: Int, vel: Float): Float {
    val v = vel.coerceIn(VEL_LO, VEL_HI)
    val c0 = (VEL_LO + VEL_B1) / 2f
    val c1 = (VEL_B1 + VEL_B2) / 2f
    val c2 = (VEL_B2 + VEL_HI) / 2f
    val a = when (kind) {
        2 -> floatArrayOf(0.08f, 0.5f, 0.97f)
        1 -> floatArrayOf(0.9f, 0.5f, 0.08f)
        else -> floatArrayOf(0.85f, 0.4f, 0.06f)
    }
    val p = when {
        v <= c0 -> a[0]
        v <= c1 -> a[0] + (a[1] - a[0]) * (v - c0) / (c1 - c0).coerceAtLeast(1e-6f)
        v <= c2 -> a[1] + (a[2] - a[1]) * (v - c1) / (c2 - c1).coerceAtLeast(1e-6f)
        else -> a[2]
    }
    return p.coerceIn(minOf(a[0], a[2]), maxOf(a[0], a[2]))
}

fun drumZoneName(midi: Int, vel: Float): String {    val z = drumZoneIdx(vel)
    return when (drumKind(midi)) {
        2 -> arrayOf("bell", "bow", "edge")[z]
        1 -> arrayOf("edge", "mid", "center")[z]
        else -> arrayOf("heel", "mid", "toe")[z]
    }
}

fun velCycle(v: Float): Float = when {
    v > 1f -> 0.65f
    v < 0.85f -> 0.95f
    else -> 1.15f
}

fun velWord(v: Float): String = when {
    v > 1f -> "accent"
    v < 0.85f -> "soft"
    else -> "normal"
}

fun waveToInt(w: String): Int = when (w) {
    "sine" -> 0
    "triangle" -> 1
    "sawtooth" -> 2
    else -> 3
}

fun beatLen(bottom: Int): Int = if (bottom >= 1) bottom else 4

fun barLen(top: Int, bottom: Int): Int = top.coerceIn(1, 24) * beatLen(bottom)

fun contentSteps(top: Int, bottom: Int, lastUsed: Int): Int {
    val bar = barLen(top, bottom)
    val bars = maxOf(1, ((lastUsed + 1 + bar - 1) / bar).coerceAtLeast(0))
    return maxOf(1, bars * bar)
}

fun stepDurSec(bpm: Int, bottom: Int): Float = 60f / bpm.coerceIn(12, 240) / beatLen(bottom)
