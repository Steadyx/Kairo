package com.example.kairo.data.books

import org.junit.Assert.assertTrue
import org.junit.Test

class EpubBookParserTest {
    private val parser = EpubBookParser(TestDispatchers)

    @Test
    fun extractPlainTextKeepsInlinePageBreakContent() {
        val html =
            "<p>Indiana and" +
                "<span class=\"right_1\" epub:type=\"pagebreak\" id=\"page_250\" title=\"250\"/>" +
                " Leo took up the rear.</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("Indiana and Leo took up the rear."))
    }

    @Test
    fun extractPlainTextKeepsClosedPageBreakSpanContent() {
        val html =
            "<p>Indiana and" +
                "<span epub:type=\"pagebreak\" id=\"page_250\" title=\"250\">250</span>" +
                " Leo took up the rear.</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("Indiana and Leo took up the rear."))
    }

    @Test
    fun extractPlainTextKeepsContentAfterRolePageBreak() {
        val html =
            "<p>Start" +
                "<span role=\"doc-pagebreak\" id=\"page_10\" title=\"10\"/>" +
                " end.</p>"

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
