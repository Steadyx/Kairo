package com.kairo.reader.data.books

import com.kairo.reader.core.model.BookId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebArticleExtractorTest {
    private val extractor = WebArticleExtractor(TestDispatchers)

    @Test
    fun quotationDivisionsAndRepeatedAuthoredParagraphsStayReadable() {
        val passage = "This deliberately repeated paragraph is part of the authored article and should remain in place."
        val html = "<article><blockquote><div>First</div><div>second</div><p>$passage</p><p>$passage</p></blockquote></article>"
        val text = extractor.parseHtml(html, "https://example.com/story", BookId("repeated")).chapters.single().plainText
        assertEquals(listOf("First second", passage, passage), text.split("\n\n"))
    }

    @Test
    fun nestedBlocksKeepEveryPassageOnceInSourceOrder() {
        val passages = listOf(
            "He said.",
            "First paragraph contains a distinct and useful quoted passage.",
            "Nested quotation has its own distinct paragraph of readable text.",
            "The end.",
            "List introduction directly owned by the list item.",
            "A paragraph inside the list item contains more readable words.",
            "List ending directly owned by the list item.",
        )
        val html = """
            <article><blockquote>${passages[0]}<p>${passages[1]}</p>
            <blockquote><p>${passages[2]}</p></blockquote>${passages[3]}</blockquote>
            <ul><li>${passages[4]}<p>${passages[5]}</p>${passages[6]}</li></ul></article>
        """.trimIndent()
        val text = extractor.parseHtml(html, "https://example.com/story", BookId("nested")).chapters.single().plainText
        assertEquals(passages, text.split("\n\n"))
    }

    @Test
    fun parseHtmlExtractsReadableArticleText() {
        val book =
            extractor.parseHtml(
                html =
                """
                    <html lang="en">
                      <head>
                        <title>Browser title</title>
                        <meta property="og:title" content="A Clear Piece | example.com">
                        <meta name="author" content="Ada Writer">
                      </head>
                      <body>
                        <nav>Home Pricing Subscribe</nav>
                        <article>
                          <h1>A Clear Piece</h1>
                          <p>This is the first useful paragraph of the article, with enough text to read.</p>
                          <p>This is the second useful paragraph, kept separate so RSVP gets a clean break.</p>
                        </article>
                        <footer>All rights reserved</footer>
                      </body>
                    </html>
                """.trimIndent(),
                normalizedUrl = "https://example.com/story",
                bookId = BookId("article"),
            )

        val chapter = book.chapters.single()
        assertEquals("A Clear Piece", book.title)
        assertEquals(listOf("Ada Writer"), book.authors)
        assertEquals("en", book.languageTag)
        assertTrue(chapter.plainText.contains("first useful paragraph"))
        assertTrue(chapter.plainText.contains("\n\nThis is the second useful paragraph"))
        assertFalse(chapter.plainText.contains("Home Pricing"))
        assertFalse(chapter.plainText.startsWith("A Clear Piece"))
        assertTrue(chapter.wordCount > 0)
    }

    @Test
    fun parseHtmlFallsBackToHostAuthor() {
        val book =
            extractor.parseHtml(
                html =
                """
                    <html>
                      <body>
                        <main>
                          <p>Readable article text appears here with enough words for import.</p>
                          <p>Another paragraph gives the extractor a useful body to keep.</p>
                        </main>
                      </body>
                    </html>
                """.trimIndent(),
                normalizedUrl = "https://news.example.com/post",
                bookId = BookId("article"),
            )

        assertEquals(listOf("news.example.com"), book.authors)
    }
}
