package com.kairo.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderViewportMetricsTest {
    @Test
    fun resolveReaderViewportHeightDp_convertsUsingCurrentDensity() {
        assertEquals(480, resolveReaderViewportHeightDp(heightPx = 1_201, density = 2.5f))
    }

    @Test
    fun resolveReaderViewportHeightDp_handlesTinyAndZeroMeasurements() {
        assertEquals(1, resolveReaderViewportHeightDp(heightPx = 1, density = 3f))
        assertEquals(0, resolveReaderViewportHeightDp(heightPx = 0, density = 3f))
        assertEquals(0, resolveReaderViewportHeightDp(heightPx = -1, density = 3f))
    }

    @Test
    fun shouldRecordReaderViewportHeight_ignoresDuplicatesAndInvalidMeasurements() {
        assertFalse(shouldRecordReaderViewportHeight(currentHeightPx = 1_200, measuredHeightPx = 1_200))
        assertFalse(shouldRecordReaderViewportHeight(currentHeightPx = 1_200, measuredHeightPx = 0))
        assertTrue(shouldRecordReaderViewportHeight(currentHeightPx = 1_200, measuredHeightPx = 1_201))
    }
}
