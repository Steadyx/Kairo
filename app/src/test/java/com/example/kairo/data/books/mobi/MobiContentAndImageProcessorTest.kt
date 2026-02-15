package com.example.kairo.data.books.mobi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiContentAndImageProcessorTest {
    private val contentProcessor = MobiContentProcessor()
    private val imageProcessor = MobiImageProcessor()

    @Test
    fun extractHtmlFallsBackToRawRecordsForUnknownCompression() {
        val record0 = ByteArray(16)
        val html = "<html><body><h1>Chapter 1</h1><p>Hello world.</p></body></html>"
        val textBytes = html.toByteArray()
        val data = ByteArray(record0.size + textBytes.size)
        record0.copyInto(data, 0)
        textBytes.copyInto(data, record0.size)

        val extracted =
            contentProcessor.extractHtml(
                data = data,
                recordOffsets = listOf(0, record0.size),
                compression = 17480,
                textRecordCount = 1,
                header =
                    MobiHeader(
                        title = "Book",
                        authors = emptyList(),
                        textCharset = Charsets.UTF_8,
                        firstImageIndex = -1,
                        coverRecordIndex = null,
                    ),
                firstImageIndexHint = -1,
            )

        assertTrue(extracted.contains("<h1>Chapter 1</h1>"))
    }

    @Test
    fun splitHtmlIntoChaptersUsesHeadingBoundaries() {
        val html =
            """
            <html><body>
            <h1>Chapter One</h1><p>Alpha beta gamma.</p>
            <h1>Chapter Two</h1><p>Delta epsilon zeta.</p>
            </body></html>
            """.trimIndent()

        val chapters = contentProcessor.splitHtmlIntoChapters(html, "Fallback")

        assertEquals(2, chapters.size)
        assertEquals("Chapter One", chapters[0].title)
        assertEquals("Chapter Two", chapters[1].title)
    }

    @Test
    fun extractCoverImageRecindicesFallsBackToFirstRecindex() {
        val html = "<html><body><img recindex=\"5\" alt=\"front\" /></body></html>"

        val recindices = contentProcessor.extractCoverImageRecindices(html)

        assertTrue(recindices.contains(5))
    }

    @Test
    fun rewriteImageSrcsResolvesRecindexAgainstBase() {
        val html = "<p><img recindex=\"1\" /></p>"
        val rewritten =
            imageProcessor.rewriteImageSrcs(
                html = html,
                imagePathByRecordIndex = mapOf(10 to "kairo_mobi_assets/abc/images/img_10.jpg"),
                recindexBase = 9,
            )

        assertTrue(rewritten.contains("img_10.jpg"))
    }
}
