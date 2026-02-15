package com.example.kairo.data.books.mobi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiHeaderParserTest {
    private val parser = MobiHeaderParser()

    @Test
    fun parseHeadersPrefersExthTitleAuthorsAndCoverRecord() {
        val record0 = ByteArray(384)
        record0[0] = 0
        record0[1] = 1 // compression = 1
        record0[8] = 0
        record0[9] = 5 // text record count = 5

        val headerOffset = MobiLimits.MOBI_HEADER_OFFSET
        writeAscii(record0, headerOffset, "MOBI")
        writeInt(record0, headerOffset + 4, 228) // header length
        writeInt(record0, headerOffset + 12, 65001) // UTF-8
        writeInt(record0, headerOffset + 28, 320) // full name offset
        writeInt(record0, headerOffset + 32, 11) // full name length
        writeInt(record0, headerOffset + MobiLimits.FIRST_IMAGE_INDEX_OFFSET, 42)
        writeInt(record0, headerOffset + MobiLimits.EXTH_FLAGS_OFFSET, MobiLimits.EXTH_PRESENT_FLAG)
        writeAscii(record0, 320, "Base Title  ")

        val exthStart = headerOffset + 228
        writeAscii(record0, exthStart, "EXTH")

        val authorPayload = "Alice;Bob".toByteArray()
        val coverPayload = byteArrayOf(0, 0, 0, 7)
        val titlePayload = "EXTH Title".toByteArray()
        val authorLength = 8 + authorPayload.size
        val coverLength = 8 + coverPayload.size
        val titleLength = 8 + titlePayload.size
        val exthLength = 12 + authorLength + coverLength + titleLength

        writeInt(record0, exthStart + 4, exthLength)
        writeInt(record0, exthStart + 8, 3)

        var cursor = exthStart + 12
        writeInt(record0, cursor, 100)
        writeInt(record0, cursor + 4, authorLength)
        authorPayload.copyInto(record0, cursor + 8)
        cursor += authorLength

        writeInt(record0, cursor, 201)
        writeInt(record0, cursor + 4, coverLength)
        coverPayload.copyInto(record0, cursor + 8)
        cursor += coverLength

        writeInt(record0, cursor, 503)
        writeInt(record0, cursor + 4, titleLength)
        titlePayload.copyInto(record0, cursor + 8)

        val headers = parser.parseHeaders(record0, "Fallback", "book.mobi")
        val header = headers.primary

        assertEquals("EXTH Title", header.title)
        assertEquals(listOf("Alice", "Bob"), header.authors)
        assertEquals(42, header.firstImageIndex)
        assertEquals(7, header.coverRecordIndex)
    }

    @Test
    fun parsePalmDocHeaderReadsCompressionAndRecordCount() {
        val record0 = ByteArray(16)
        record0[1] = 2 // compression = 2
        record0[8] = 0
        record0[9] = 9

        val palmDoc = parser.parsePalmDocHeader(record0)

        assertEquals(2, palmDoc.compression)
        assertEquals(9, palmDoc.textRecordCount)
    }

    @Test
    fun parseHeadersFallsBackWhenNoMobiHeader() {
        val record0 = ByteArray(32)

        val headers = parser.parseHeaders(record0, "Fallback Name", "sample.mobi")

        assertEquals("Fallback Name", headers.primary.title)
        assertTrue(headers.primary.authors.isEmpty())
        assertEquals(-1, headers.primary.firstImageIndex)
    }

    private fun writeInt(
        target: ByteArray,
        offset: Int,
        value: Int,
    ) {
        target[offset] = ((value shr 24) and 0xFF).toByte()
        target[offset + 1] = ((value shr 16) and 0xFF).toByte()
        target[offset + 2] = ((value shr 8) and 0xFF).toByte()
        target[offset + 3] = (value and 0xFF).toByte()
    }

    private fun writeAscii(
        target: ByteArray,
        offset: Int,
        value: String,
    ) {
        value.toByteArray().copyInto(target, offset)
    }
}
