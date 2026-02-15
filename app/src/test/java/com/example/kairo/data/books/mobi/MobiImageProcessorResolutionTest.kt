package com.example.kairo.data.books.mobi

import com.example.kairo.data.books.callPrivate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiImageProcessorResolutionTest {
    private val processor = MobiImageProcessor()

    @Test
    fun resolveRecindexToRecordIndicesSupportsZeroAndOneBasedOffsets() {
        val indices: Set<Int> = processor.callPrivate("resolveRecindexToRecordIndices", 2, 9, 20)

        assertTrue(indices.contains(11))
        assertTrue(indices.contains(10))
    }

    @Test
    fun resolveCoverRecordIndicesIncludesAbsoluteAndRelativeCandidates() {
        val indices: Set<Int> = processor.callPrivate("resolveCoverRecordIndices", 2, 10, 9, 20)

        assertTrue(indices.contains(2))
        assertTrue(indices.contains(12))
        assertTrue(indices.contains(11))
    }

    @Test
    fun resolveImagePathHandlesOneBasedRecindexFallback() {
        val path: String? =
            processor.callPrivate(
                "resolveImagePath",
                1,
                mapOf(10 to "kairo_mobi_assets/book/images/img_10.jpg"),
                9,
            )

        assertEquals("kairo_mobi_assets/book/images/img_10.jpg", path)
    }
}
