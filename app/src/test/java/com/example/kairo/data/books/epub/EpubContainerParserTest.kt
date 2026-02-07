package com.example.kairo.data.books.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubContainerParserTest {
    private val parser = EpubContainerParser()

    @Test
    fun parseReturnsOrderedCandidatePaths() {
        val xml =
            """
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="alt/book.opf" media-type="text/plain"/>
                <rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """.trimIndent()

        val result = parser.parse(xml)

        assertEquals("OPS/package.opf", result.path)
        assertEquals(listOf("OPS/package.opf", "alt/book.opf"), result.candidatePaths)
    }

    @Test
    fun parseLenientMalformedStillReturnsCandidateList() {
        val xml = "<container><rootfiles><rootfile full-path='OPS/content.opf'"

        val result = parser.parse(xml)

        assertTrue(result.usedLenientFallback)
        assertEquals("OPS/content.opf", result.path)
        assertTrue(result.candidatePaths.contains("OPS/content.opf"))
    }
}
