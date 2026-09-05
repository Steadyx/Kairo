package com.kairo.reader.data.books

import com.kairo.reader.core.model.BookId
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFileParserEngineTest {
    @Test
    fun markdownImportUsesHeadingAndPreservesParagraphs() {
        val book = parse(
            extension = "md",
            displayName = "fallback.md",
            source = "# A Better Title\n\nFirst paragraph.\n\nSecond paragraph.",
        )

        assertEquals("A Better Title", book.title)
        assertEquals("First paragraph.\n\nSecond paragraph.", book.chapters.single().plainText)
        assertTrue(book.chapters.single().htmlContent.contains("<p>First paragraph.</p>"))
    }

    @Test
    fun htmlImportRemovesExecutableContentAndKeepsBlockOrder() {
        val book = parse(
            extension = "html",
            displayName = "article.html",
            source =
            """
            <html lang="en"><head><title>Safe Reading</title><meta name="author" content="Ada"></head>
            <body><script>alert('no')</script><h1>Opening</h1><p>First paragraph.</p>
            <p>Second <strong>paragraph</strong>.</p><iframe src="https://example.com"></iframe></body></html>
            """.trimIndent(),
        )

        assertEquals("Safe Reading", book.title)
        assertEquals(listOf("Ada"), book.authors)
        assertEquals("en", book.languageTag)
        val plainText = book.chapters.single().plainText
        assertTrue(plainText.contains("First paragraph."))
        assertTrue(plainText.contains("Second paragraph."))
        assertTrue(plainText.indexOf("First paragraph.") < plainText.indexOf("Second paragraph."))
        assertFalse(book.chapters.single().htmlContent.contains("script", ignoreCase = true))
        assertFalse(book.chapters.single().htmlContent.contains("iframe", ignoreCase = true))
    }

    @Test
    fun htmlImportPreservesParagraphsWithoutAddingTextToHtml() {
        val chapter = parse(
            extension = "html",
            displayName = "paragraphs.html",
            source = "<p>First paragraph.</p><p>Second paragraph.</p>",
        ).chapters.single()

        assertEquals("First paragraph.\n\nSecond paragraph.", chapter.plainText)
        assertEquals("<p>First paragraph.</p>\n<p>Second paragraph.</p>", chapter.htmlContent)
    }

    @Test
    fun htmlImportLineBreakDoesNotBecomeAWord() {
        val chapter = parse(
            extension = "html",
            displayName = "lines.html",
            source = "<p>First<br>Second</p>",
        ).chapters.single()

        assertFalse(chapter.plainText.contains("\\n"))
        assertTrue(chapter.plainText.contains("First\n"))
        assertFalse(chapter.htmlContent.contains("\\n"))
        val document = Jsoup.parse(chapter.htmlContent)
        assertEquals("First Second", document.text())
        assertEquals(1, document.select("br").size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun htmlImportRejectsDocumentsWithoutReadableText() {
        parse(
            extension = "html",
            displayName = "empty.html",
            source = "<html><body><script>onlyCode()</script></body></html>",
        )
    }

    private fun parse(
        extension: String,
        displayName: String,
        source: String,
    ) =
        TextFileParserEngine.parse(
            BinaryBookParseRequest(
                bookId = BookId("text-test"),
                bytes = source.toByteArray(),
                sourceDisplayName = displayName,
                sourceExtension = extension,
            ),
        )
}
