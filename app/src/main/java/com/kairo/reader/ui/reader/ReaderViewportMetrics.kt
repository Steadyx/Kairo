package com.kairo.reader.ui.reader

import kotlin.math.roundToInt

internal fun resolveReaderViewportHeightDp(
    heightPx: Int,
    density: Float,
): Int {
    require(density.isFinite() && density > 0f) { "Density must be finite and positive" }
    if (heightPx <= 0) return 0
    return (heightPx / density).roundToInt().coerceAtLeast(1)
}

internal fun shouldRecordReaderViewportHeight(
    currentHeightPx: Int,
    measuredHeightPx: Int,
): Boolean = measuredHeightPx > 0 && measuredHeightPx != currentHeightPx
