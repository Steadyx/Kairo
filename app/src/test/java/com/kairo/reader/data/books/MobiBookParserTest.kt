package com.kairo.reader.data.books

import com.kairo.reader.data.books.mobi.MobiContentProcessor
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MobiBookParserTest {
    private val parser = MobiBookParser(TestDispatchers)
    private val contentProcessor = MobiContentProcessor()

    @Test
    fun supportsMobiPrcAndAzwCaseInsensitively() {
        assertTrue(parser.supports("mobi"))
        assertTrue(parser.supports("PRC"))
        assertTrue(parser.supports(" AzW "))
        assertFalse(parser.supports("azw3"))
    }

    @Test
    fun extractPlainTextKeepsInlineMbpPageBreakContent() {
        val html = "<p>Indiana and<mbp:pagebreak/> Leo took up the rear.</p>"

        val text = contentProcessor.extractPlainText(html)

        assertTrue(text.contains("Indiana and\u000C Leo took up the rear."))
    }

    @Test
    fun extractPlainTextKeepsClassPageBreakContent() {
        val html = "<p>Indiana and<span class=\"pagebreak\"/> Leo took up the rear.</p>"

        val text = contentProcessor.extractPlainText(html)

        assertTrue(text.contains("Indiana and\u000C Leo took up the rear."))
    }

    @Test
    fun extractPlainTextKeepsContentAfterClassPageBreak() {
        val html = "<p>Start<span class=\"page-break\"/> end.</p>"

        val text = contentProcessor.extractPlainText(html)

        assertTrue(text.contains("Start\u000C end."))
    }

    @Test
    fun extractPlainTextKeepsPageBreakOnlyDocument() {
        val html = "<mbp:pagebreak/>"

        val text = contentProcessor.extractPlainText(html)

        assertEquals("\u000C", text)
    }

    @Test
    fun cleanMobiHtmlPreservesRawFormFeedPageBreaks() {
        val cleaned: String = contentProcessor.callPrivate("cleanMobiHtml", "Start\u000CEnd")

        assertEquals("Start\u000CEnd", cleaned)
    }

    @Test
    fun extractPlainTextDoesNotTreatSubstringClassAsPageBreak() {
        val html = "<p>Start<span class=\"not-pagebreak\">kept</span> end.</p>"

        val text = contentProcessor.extractPlainText(html)

        assertFalse(text.contains("\u000C"))
        assertTrue(text.contains("Startkept end."))
    }

    @Test
    fun extractPlainTextTreatsTokenizedClassAsPageBreak() {
        val html = "<p>Start<span class=\"marker page-break visible\"/> end.</p>"

        val text = contentProcessor.extractPlainText(html)

        assertTrue(text.contains("Start\u000C end."))
    }

    @Test
    fun extractPlainTextRemovesClosedClassPageBreakContent() {
        val html =
            "<p><span class=\"pagebreak\">12</span>" +
                "<span class=\"char-first\">T<span class=\"smallcaps\">" +
                "HE OPENING WORDS </span></span>remain intact.</p>"

        val text = contentProcessor.extractPlainText(html)

        assertTrue(text.contains("THE OPENING WORDS remain intact."))
    }

    @Test
    fun extractPlainTextRemovesClosedMbpPageBreakContent() {
        val html = "<p>Start<mbp:pagebreak>12</mbp:pagebreak> end.</p>"

        val text = contentProcessor.extractPlainText(html)

        assertTrue(text.contains("Start\u000C end."))
    }

    @Test
    fun extractPlainTextDecodesEntitiesAndPreservesParagraphs() {
        val html = "<p>Hello&nbsp;world &amp; friends.</p><p>Next&nbsp;para.</p>"

        val text = contentProcessor.extractPlainText(html)

        assertTrue(text.contains("Hello world & friends."))
        assertTrue(text.contains("Next para."))
        assertTrue(text.contains("friends.\n\nNext"))
    }

    @Test
    fun stripNoiseTitleBlocksOnlyRemovesLeadingFileLabels() {
        val html =
            "<p>Real opening.</p><p>Chapter 100</p><p>Should stay.</p>"

        val cleaned: String = contentProcessor.callPrivate("stripNoiseTitleBlocks", html)

        assertTrue(cleaned.contains("<p>Chapter 100</p>"))
    }

    @Test
    fun isFileTooLargeDetectsOversizedInputs() {
        val tooLarge: Boolean = parser.callPrivate("isFileTooLarge", Long.MAX_VALUE)
        val smallEnough: Boolean = parser.callPrivate("isFileTooLarge", 0L)

        assertTrue(tooLarge)
        assertFalse(smallEnough)
    }

    @Test
    fun readInputBytesWithLimitReturnsBytesWithinLimit() {
        val data = byteArrayOf(1, 2, 3)

        val result: ByteArray = parser.callPrivate(
            "readInputBytesWithLimit",
            ByteArrayInputStream(data),
            3L,
            {},
        )

        assertEquals(data.toList(), result.toList())
    }

    @Test
    fun readInputBytesWithLimitRejectsOversizedStreams() {
        try {
            parser.callPrivate<ByteArray>(
                "readInputBytesWithLimit",
                ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
                3L,
                {},
            )
            fail("Expected oversized stream to be rejected")
        } catch (error: java.lang.reflect.InvocationTargetException) {
            assertTrue(error.cause is IllegalArgumentException)
        }
    }
}
