package com.kairo.reader.data.books

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Test

class BookTextDecoderTest {
    @Test
    fun decodeRecognizesQuotedAndUnquotedJapaneseCharsets() {
        listOf("Shift_JIS", "'Shift_JIS'", "\"Shift_JIS\"").forEach { declaration ->
            val source = "<meta charset=$declaration><p>日本語の文章です。</p>"
            assertEquals(source, BookTextDecoder.decode(source.toByteArray(Charset.forName("Shift_JIS"))))
        }
    }

    @Test
    fun bomOverridesConflictingHtmlAndExplicitCharsets() {
        val source = "<meta charset='Shift_JIS'><p>日本語</p>"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + source.toByteArray(Charsets.UTF_16LE)
        assertEquals(source, BookTextDecoder.decode(bytes, "windows-1252"))
    }

    @Test
    fun decodeHonoursUtf16LittleEndianBom() {
        val content = "A properly decoded chapter."
        val encoded = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + content.toByteArray(Charsets.UTF_16LE)

        assertEquals(content, BookTextDecoder.decode(encoded))
    }

    @Test
    fun decodeUsesDeclaredHtmlCharset() {
        val source = "<meta charset=windows-1252><p>Caf\u00e9 costs \u00a35.</p>"
        val encoded = source.toByteArray(Charset.forName("windows-1252"))

        assertEquals(source, BookTextDecoder.decode(encoded))
    }

    @Test
    fun decodeFallsBackToWindows1252WhenUtf8IsMalformed() {
        val encoded = "Smart \u201cquotes\u201d".toByteArray(Charset.forName("windows-1252"))

        assertEquals("Smart \u201cquotes\u201d", BookTextDecoder.decode(encoded))
    }
}
