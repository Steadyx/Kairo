package com.example.kairo.data.books.mobi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiBinaryTest {
    @Test
    fun decompressPalmDocCopiesLiteralRuns() {
        val input = byteArrayOf(3, 'A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte())

        val decoded = MobiBinary.decompressPalmDoc(input)

        assertEquals("ABC", String(decoded))
    }

    @Test
    fun decompressPalmDocHandlesSpaceCharacterEncoding() {
        val input = byteArrayOf(0xC1.toByte())

        val decoded = MobiBinary.decompressPalmDoc(input)

        assertEquals(" A", String(decoded))
    }

    @Test
    fun detectImageTypeRecognizesPng() {
        val pngHeader =
            byteArrayOf(
                0x89.toByte(),
                0x50.toByte(),
                0x4E.toByte(),
                0x47.toByte(),
                0x0D.toByte(),
                0x0A.toByte(),
                0x1A.toByte(),
                0x0A.toByte(),
                0x00.toByte(),
                0x00.toByte(),
                0x00.toByte(),
                0x0D.toByte(),
            )

        val type = MobiBinary.detectImageType(pngHeader)

        assertTrue(type?.extension == "png")
    }
}
