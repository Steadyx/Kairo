package com.example.kairo.data.books

import com.example.kairo.data.books.mobi.MobiContentProcessor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiBookParserTest {
    private val parser = MobiBookParser(TestDispatchers)
    private val contentProcessor = MobiContentProcessor()

    @Test
    fun extractPlainTextKeepsInlineMbpPageBreakContent() {
        val html = "<p>Indiana and<mbp:pagebreak/> Leo took up the rear.</p>"

        val text = contentProcessor.extractPlainText(html)

        assertTrue(text.contains("Indiana and Leo took up the rear."))
    }

    @Test
    fun extractPlainTextKeepsClassPageBreakContent() {
        val html = "<p>Indiana and<span class=\"pagebreak\"/> Leo took up the rear.</p>"

        val text = contentProcessor.extractPlainText(html)

        assertTrue(text.contains("Indiana and Leo took up the rear."))
    }

    @Test
    fun extractPlainTextKeepsContentAfterClassPageBreak() {
        val html = "<p>Start<span class=\"page-break\"/> end.</p>"

        val text = contentProcessor.extractPlainText(html)

        assertTrue(text.contains("Start end."))
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
    fun isFileTooLargeDetectsOversizedInputs() {
        val tooLarge: Boolean = parser.callPrivate("isFileTooLarge", Long.MAX_VALUE)
        val smallEnough: Boolean = parser.callPrivate("isFileTooLarge", 0L)

        assertTrue(tooLarge)
        assertFalse(smallEnough)
    }
}
