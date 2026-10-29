package com.kairo.reader.data.books.mobi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
        assertEquals("Alpha beta gamma.", chapters[0].plainText)
        assertEquals("Delta epsilon zeta.", chapters[1].plainText)
        assertFalse(chapters[0].htmlContent.contains("Chapter One"))
        assertFalse(chapters[1].htmlContent.contains("Chapter Two"))
    }

    @Test
    fun splitHtmlIntoChaptersRemovesDuplicateHeadingInsideWrapper() {
        val html =
            """
            <html><body>
            <div class="chapter"><h1>Preface</h1><p>Before the story.</p></div>
            <div class="chapter"><h1>Chapter 1</h1><p>Opening scene.</p></div>
            </body></html>
            """.trimIndent()

        val chapters = contentProcessor.splitHtmlIntoChapters(html, "Fallback")

        assertEquals("Preface", chapters[0].title)
        assertEquals("Before the story.", chapters[0].plainText)
        assertFalse(chapters[0].htmlContent.contains("Preface"))
    }

    @Test
    fun headingSplittingPreservesPrefaceAndImageAndRewritesLinksToNewChapterIndexes() {
        val html = """
            <html><head><title>Metadata title</title></head><body>
            <p>Introductory text.</p><img src="intro.png">
            <h1>Chapter One</h1><p>First chapter.</p><a href="#end">Next</a>
            <h1>Chapter Two</h1><p id="end">Last chapter.</p>
            </body></html>
        """.trimIndent()

        val chapters = contentProcessor.splitHtmlIntoChapters(html, "Book")

        assertEquals(listOf("Introductory text.", "First chapter.\nNext", "Last chapter."), chapters.map { it.plainText })
        assertEquals(listOf("intro.png"), chapters[0].imagePaths)
        assertTrue(chapters[1].htmlContent.contains("kairo://chapter/2"))
        assertFalse(chapters[0].plainText.contains("Metadata title"))
    }

    @Test
    fun imageOnlyContentBeforeHeadingsIsRetained() {
        val html = "<body><img src='cover.png'><h1>First</h1><p>One.</p><h1>Second</h1><p>Two.</p></body>"

        val chapters = contentProcessor.splitHtmlIntoChapters(html, "Book")

        assertEquals(3, chapters.size)
        assertEquals(listOf("cover.png"), chapters.first().imagePaths)
        assertEquals("", chapters.first().plainText)
    }

    @Test
    fun longHtmlRetainsAllProseImagesMarkupAndChapterLinks() {
        val opening = "Essential start. " + "long passage ".repeat(3000)
        val html = """
            <h1>First</h1><p>$opening</p><img src='one.png'>
            <p><em>Authored emphasis</em> and references to chapter 1 and chapter 2.</p>
            <p><a href="#target">Navigate</a></p><img src='two.png'>
            <h1>Second</h1><p id="target">The ending.</p>
        """.trimIndent()

        val chapters = contentProcessor.splitHtmlIntoChapters(html, "Book")

        assertEquals(2, chapters.size)
        assertTrue(chapters[0].plainText.contains(opening))
        assertTrue(chapters[0].plainText.contains("references to chapter 1 and chapter 2"))
        assertTrue(chapters[0].htmlContent.contains("<em>Authored emphasis</em>"))
        assertTrue(chapters[0].htmlContent.contains("kairo://chapter/1"))
        assertEquals(listOf("one.png", "two.png"), chapters[0].imagePaths)
        assertEquals("The ending.", chapters[1].plainText)
    }

    @Test
    fun fallbackTextRetainsPrefaceAndDoesNotTreatInlineReferencesAsHeadings() {
        val prefix = "Important opening. " + "story ".repeat(5000)
        val text = "$prefix A note refers to chapter 1 and chapter 2."

        val chapters = contentProcessor.splitFallbackText(text)

        assertEquals(text.trim(), chapters.joinToString("\n\n") { it.plainText })
        assertTrue(chapters.first().plainText.startsWith("Important opening."))
    }

    @Test
    fun fallbackTextKeepsPrefaceBeforeSeparateHeadingLines() {
        val text = "Preface & introduction.\n\nChapter 1\nFirst.\n\nChapter 2\nSecond."

        val chapters = contentProcessor.splitFallbackText(text)

        assertEquals(listOf("Preface & introduction.", "Chapter 1\nFirst.", "Chapter 2\nSecond."), chapters.map { it.plainText })
        assertTrue(chapters.first().htmlContent.contains("Preface &amp; introduction."))
        assertEquals(listOf(0, 1, 2), chapters.map { it.index })
    }

    @Test
    fun oversizedImageChapterIsRejectedBeforeImportInsteadOfFailingInTheReader() {
        val image = "<img src='kairo_mobi_assets/book/images/one.png'>"
        contentProcessor.requireDisplayableImages(image.repeat(256))
        val error = assertThrows(IllegalArgumentException::class.java) {
            contentProcessor.requireDisplayableImages(image.repeat(257))
        }
        assertTrue(error.message.orEmpty().contains("too many images"))
        contentProcessor.requireDisplayableImages("<img src='https://example.com/a.png'>".repeat(257))
    }

    @Test
    fun chapterImagePathsKeepMoreThanSixDistinctFigures() {
        val html = (1..8).joinToString("") { "<img src='figure$it.png'>" }

        assertEquals((1..8).map { "figure$it.png" }, contentProcessor.extractImagePathsFromHtml(html))
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
