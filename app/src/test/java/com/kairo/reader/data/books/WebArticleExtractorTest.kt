package com.kairo.reader.data.books

import com.kairo.reader.core.model.BookId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebArticleExtractorTest {
    private val extractor = WebArticleExtractor(TestDispatchers)

    @Test
    fun relatedTeaserBeforeMainDoesNotReplaceArticle() {
        val teaser = "Related teaser text. ".repeat(15)
        val article = "The real article text explains this important subject. ".repeat(5)
        val html = "<div class='article-related'><p>$teaser</p></div><main><p>$article</p></main>"

        val text = extractor.parseHtml(html, "https://example.com/story", BookId("main")).chapters.single().plainText

        assertTrue(text.contains("real article text"))
        assertFalse(text.contains("Related teaser"))
    }

    @Test
    fun longerUnrelatedArticleBeforeMainDoesNotReplaceMainContent() {
        val teaser = "A long unrelated teaser. ".repeat(30)
        val main = "The requested article is inside the main area. ".repeat(4)
        val html = "<article><p>$teaser</p></article><main><p>$main</p></main>"

        val text = extractor.parseHtml(html, "https://example.com/story", BookId("main")).chapters.single().plainText

        assertTrue(text.contains("requested article"))
        assertFalse(text.contains("unrelated teaser"))
    }

    @Test
    fun articleParagraphsContainingBoilerplatePhrasesArePreserved() {
        val paragraph = "The court examined the privacy policy and whether readers should subscribe to a service."
        val following = "Another paragraph contains the rest of this authored article. ".repeat(3)
        val html = "<article><p>$paragraph</p><p>$following</p></article>"

        val text = extractor.parseHtml(html, "https://example.com/story", BookId("phrases")).chapters.single().plainText

        assertTrue(text.contains(paragraph))
        assertTrue(text.contains("rest of this authored article"))
    }

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
