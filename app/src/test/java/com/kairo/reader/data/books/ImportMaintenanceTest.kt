package com.kairo.reader.data.books

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportMaintenanceTest {
    @Test
    fun compressedByteSizeDoesNotBypassCoverDimensionLimit() {
        assertTrue(CoverImageOptimizer.needsOptimization(100, 6000, 4000))
        assertTrue(CoverImageOptimizer.needsOptimization(300_000, 100, 100))
        assertFalse(CoverImageOptimizer.needsOptimization(100, 1080, 1080))
    }
}
