package com.kairo.reader.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowContainerMetricsTest {
    @Test
    fun resolveWindowContainerMetrics_convertsPixelsUsingDensity() {
        val metrics =
            resolveWindowContainerMetrics(
                widthPx = 2_001,
                heightPx = 1_201,
                density = 2.5f,
            )

        assertEquals(800.4f, metrics.widthDp.value, FLOAT_TOLERANCE)
        assertEquals(480.4f, metrics.heightDp.value, FLOAT_TOLERANCE)
        assertEquals(800, metrics.roundedWidthDp)
        assertEquals(480, metrics.roundedHeightDp)
        assertTrue(metrics.isLandscape)
    }

    @Test
    fun isCompactLandscape_usesUnroundedHeightAtBoundary() {
        val atBoundary = resolveWindowContainerMetrics(widthPx = 2_000, heightPx = 1_200, density = 2.5f)
        val overBoundary = resolveWindowContainerMetrics(widthPx = 2_001, heightPx = 1_201, density = 2.5f)

        assertTrue(atBoundary.isCompactLandscape(480.dp))
        assertFalse(overBoundary.isCompactLandscape(480.dp))
    }

    @Test
    fun isCompactLandscape_requiresLandscapeOrientation() {
        val portrait = resolveWindowContainerMetrics(widthPx = 600, heightPx = 900, density = 2f)
        val square = resolveWindowContainerMetrics(widthPx = 600, heightPx = 600, density = 2f)

        assertFalse(portrait.isCompactLandscape(480.dp))
        assertFalse(square.isCompactLandscape(480.dp))
    }

    @Test
    fun resolveWindowContainerMetrics_handlesTinyAndZeroContainers() {
        val tiny = resolveWindowContainerMetrics(widthPx = 1, heightPx = 1, density = 3f)
        val zero = resolveWindowContainerMetrics(widthPx = 0, heightPx = 0, density = 3f)

        assertEquals(1f / 3f, tiny.widthDp.value, FLOAT_TOLERANCE)
        assertEquals(0, tiny.roundedWidthDp)
        assertEquals(0.dp, zero.widthDp)
        assertEquals(0.dp, zero.heightDp)
        assertFalse(zero.isLandscape)
    }

    private companion object {
        const val FLOAT_TOLERANCE = 0.0001f
    }
}
