package com.example.kairo.data.books

import org.junit.Assert.assertTrue
import org.junit.Test

class MobiBookParserTest {
    private val parser = MobiBookParser(TestDispatchers)

    @Test
    fun extractPlainTextKeepsInlineMbpPageBreakContent() {
        val html = "<p>Indiana and<mbp:pagebreak/> Leo took up the rear.</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("Indiana and Leo took up the rear."))
    }

    @Test
    fun extractPlainTextKeepsClassPageBreakContent() {
        val html = "<p>Indiana and<span class=\"pagebreak\"/> Leo took up the rear.</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("Indiana and Leo took up the rear."))
    }

    @Test
    fun extractPlainTextKeepsContentAfterClassPageBreak() {
        val html = "<p>Start<span class=\"page-break\"/> end.</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("Start end."))
    }

    @Test
    fun extractPlainTextDecodesEntitiesAndPreservesParagraphs() {
        val html = "<p>Hello&nbsp;world &amp; friends.</p><p>Next&nbsp;para.</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("Hello world & friends."))
        assertTrue(text.contains("Next para."))
        assertTrue(text.contains("friends.\n\nNext"))
    }
}
