package com.kairo.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal data class WindowContainerMetrics(val widthPx: Int, val heightPx: Int, val widthDp: Dp, val heightDp: Dp,) {
    val isLandscape: Boolean
        get() = widthPx > heightPx

    val roundedWidthDp: Int
        get() = widthDp.value.roundToInt()

    val roundedHeightDp: Int
        get() = heightDp.value.roundToInt()

    fun isCompactLandscape(maxHeight: Dp): Boolean = isLandscape && heightDp <= maxHeight
}

@Composable
internal fun rememberWindowContainerMetrics(): WindowContainerMetrics {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    return remember(containerSize, density) {
        resolveWindowContainerMetrics(
            widthPx = containerSize.width,
            heightPx = containerSize.height,
            density = density,
        )
    }
}

internal fun resolveWindowContainerMetrics(
    widthPx: Int,
    heightPx: Int,
    density: Float,
): WindowContainerMetrics {
    require(density.isFinite() && density > 0f) { "Density must be finite and positive" }
    val resolvedWidthPx = widthPx.coerceAtLeast(0)
    val resolvedHeightPx = heightPx.coerceAtLeast(0)
    return WindowContainerMetrics(
        widthPx = resolvedWidthPx,
        heightPx = resolvedHeightPx,
        widthDp = (resolvedWidthPx / density).dp,
        heightDp = (resolvedHeightPx / density).dp,
    )
}
