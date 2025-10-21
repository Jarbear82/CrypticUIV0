package com.tau.cryptic_ui_v0.views

import androidx.compose.ui.graphics.Color

/**
 * Stores the generated hex color and its raw RGB components.
 */
data class NodeColorInfo(val hex: String, val rgb: IntArray, val composeColor: Color, val composeFontColor: Color)

/**
 * Generates a consistent hex color string and RGB array from a label string.
 */
fun labelToColor(label: String): NodeColorInfo {
    val hash = label.hashCode()
    val r = (hash shr 16) and 0xFF
    val g = (hash shr 8) and 0xFF
    val b = hash and 0xFF
    val hex = String.format("#%02X%02X%02X", r, g, b)
    val rgb = intArrayOf(r, g, b)
    val composeColor = Color(r, g, b)
    val fontColor = getFontColor(rgb)
    val composeFontColor = if (fontColor == "#FFFFFF") Color.White else Color.Black
    return NodeColorInfo(hex, rgb, composeColor, composeFontColor)
}

/**
 * Calculates perceived luminance and returns black or white for best contrast.
 */
fun getFontColor(rgb: IntArray): String {
    // Standard luminance formula
    val luminance = (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255
    // Use white text on dark backgrounds, black text on light backgrounds
    return if (luminance < 0.5) "#FFFFFF" else "#000000"
}