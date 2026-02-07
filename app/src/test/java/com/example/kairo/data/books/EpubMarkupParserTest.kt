package com.example.kairo.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubMarkupParserTest {
    private val parser = EpubMarkupParser()

    @Test
    fun renderPlainTextSkipsScriptAndPreservesReadableText() {
        val html = "<div><p>Hello<br/>world</p><script>bad()</script><p>Next</p></div>"

        val document = parser.parse(html)
        val text = EpubMarkupInspector.renderPlainText(document)

        assertTrue(text.contains("Hello"))
        assertTrue(text.contains("world"))
        assertTrue(text.contains("Next"))
        assertFalse(text.contains("bad()"))
    }

    @Test
    fun extractImageSourcesReadsImgAndSvgImageTags() {
        val html =
            """
            <div>
              <img src="images/cover.jpg" />
              <svg><image xlink:href="images/inline.svg"/></svg>
            </div>
            """.trimIndent()

        val document = parser.parse(html)
        val sources = EpubMarkupInspector.extractImageSources(document)

        assertEquals(listOf("images/cover.jpg", "images/inline.svg"), sources)
    }

    @Test
    fun countTagOccurrencesHonorsLimit() {
        val html = "<div><a>a</a><a>b</a><a>c</a></div>"

        val document = parser.parse(html)
        val count = EpubMarkupInspector.countTagOccurrences(document, "a", limit = 2)

        assertEquals(2, count)
    }

    @Test
    fun firstTextInTagsFindsHeadingWithNestedNodes() {
        val html = "<html><body><h2><span>Chapter</span> <em>One</em></h2></body></html>"

        val document = parser.parse(html)
        val title = EpubMarkupInspector.firstTextInTags(document, setOf("h1", "h2", "h3"))

        assertEquals("Chapter One", title?.replace(Regex("\\s+"), " ")?.trim())
    }
}
