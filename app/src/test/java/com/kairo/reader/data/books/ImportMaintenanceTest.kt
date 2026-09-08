package com.kairo.reader.data.books

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportMaintenanceTest {
    @Test
    fun sourceLimitRejectsUnknownLengthStreamsBeforeWritingOverBudget() {
        val output = ByteArrayOutputStream()
        assertThrows(ImportSourceTooLargeException::class.java) {
            ImportFingerprint.sourceFingerprint("epub", ByteArrayInputStream(ByteArray(9)), output, maxBytes = 8L)
        }
        assertTrue(output.size() <= 8)
        assertThrows(ImportSourceTooLargeException::class.java) {
            ImportFingerprint.sourceFingerprint("epub", ByteArrayInputStream(ByteArray(9)), maxBytes = 8L)
        }
    }

    @Test
    fun sourceLimitAcceptsExactBoundaryAndKeepsFingerprintStable() {
        val bytes = ByteArray(8) { it.toByte() }
        val output = ByteArrayOutputStream()
        val actual = ImportFingerprint.sourceFingerprint("epub", bytes.inputStream(), output, maxBytes = 8L)
        assertEquals(ImportFingerprint.sourceFingerprint("epub", bytes.inputStream()), actual)
        assertTrue(bytes.contentEquals(output.toByteArray()))
    }

    @Test
    fun cancelledStagingStopsBeforeReadingOrWriting() {
        val output = ByteArrayOutputStream()
        assertThrows(CancellationException::class.java) {
            ImportFingerprint.sourceFingerprint("epub", ByteArray(8).inputStream(), output, checkActive = {
                throw CancellationException("cancelled")
            })
        }
        assertEquals(0, output.size())
    }

    @Test
    fun compressedByteSizeDoesNotBypassCoverDimensionLimit() {
        assertTrue(CoverImageOptimizer.needsOptimization(100, 6000, 4000))
        assertTrue(CoverImageOptimizer.needsOptimization(300_000, 100, 100))
        assertFalse(CoverImageOptimizer.needsOptimization(100, 1080, 1080))
    }
}
