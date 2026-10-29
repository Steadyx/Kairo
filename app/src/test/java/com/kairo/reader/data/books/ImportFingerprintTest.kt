package com.kairo.reader.data.books

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportFingerprintTest {
    @Test
    fun `text fingerprint ignores line ending differences`() {
        assertEquals(
            ImportFingerprint.textFingerprint("First line\nSecond line"),
            ImportFingerprint.textFingerprint("First line\r\nSecond line"),
        )
    }

    @Test
    fun sourceFingerprintIsStableForSameFileBytes() {
        val first =
            ImportFingerprint.sourceFingerprint(
                extension = "EPUB",
                input = ByteArrayInputStream("same book".toByteArray()),
            )
        val second =
            ImportFingerprint.sourceFingerprint(
                extension = "epub",
                input = ByteArrayInputStream("same book".toByteArray()),
            )

        assertEquals(first, second)
        assertTrue(first.startsWith("source:epub:"))
    }

    @Test
    fun detectedExtensionCanReplaceFingerprintExtensionWithoutRehashing() {
        val original =
            ImportFingerprint.sourceFingerprint(
                extension = "pdf",
                input = ByteArrayInputStream("docx package bytes".toByteArray()),
            )

        val corrected = ImportFingerprint.withSourceExtension(original, "DOCX")

        assertEquals(original.substringAfterLast(':'), corrected.substringAfterLast(':'))
        assertTrue(corrected.startsWith("source:docx:"))
    }

    @Test
    fun stableBookIdIsDerivedFromFingerprint() {
        val first = ImportFingerprint.bookIdForFingerprint("source:epub:abc")
        val second = ImportFingerprint.bookIdForFingerprint("source:epub:abc")
        val other = ImportFingerprint.bookIdForFingerprint("source:epub:def")

        assertEquals(first, second)
        assertNotEquals(first, other)
        assertTrue(first.value.startsWith("imported-"))
    }

    @Test
    fun contentFingerprintMatchesSameParsedBookContent() {
        val first =
            book(
                title = " Example Book ",
                authors = listOf("Ada"),
                chapterText = "First line.\r\nSecond line.",
            )
        val second =
            book(
                title = "example   book",
                authors = listOf("Ada"),
                chapterText = "First line.\nSecond line.",
            )
        val changed =
            book(
                title = "Example Book",
                authors = listOf("Ada"),
                chapterText = "Different text.",
            )

        assertEquals(
            ImportFingerprint.contentFingerprint(first),
            ImportFingerprint.contentFingerprint(second),
        )
        assertNotEquals(
            ImportFingerprint.contentFingerprint(first),
            ImportFingerprint.contentFingerprint(changed),
        )
    }

    @Test
    fun contentFingerprintIncludesMarkupAndLanguage() {
        val original = book("A book", listOf("Ada"), "Some text")
        val formatted = original.copy(
            chapters = original.chapters.map { it.copy(htmlContent = "<p><em>Some text</em></p>") },
        )
        assertNotEquals(ImportFingerprint.contentFingerprint(original), ImportFingerprint.contentFingerprint(formatted))
        assertNotEquals(
            ImportFingerprint.contentFingerprint(original),
            ImportFingerprint.contentFingerprint(original.copy(languageTag = "fr")),
        )
        assertNotEquals(
            ImportFingerprint.contentFingerprint(original),
            ImportFingerprint.contentFingerprint(original.copy(chapters = original.chapters.map { it.copy(isLinear = false) })),
        )
    }

    @Test
    fun illustratedBooksAreNotComparedAsTextOnly() {
        val original = book("A book", listOf("Ada"), "Some text")
        assertTrue(!original.hasImportImages())
        assertTrue(original.copy(coverImage = byteArrayOf(1)).hasImportImages())
        assertTrue(
            original.copy(chapters = original.chapters.map { it.copy(imagePaths = listOf("image.png")) })
                .hasImportImages(),
        )
    }

    @Test
    fun epubReimportRepairsLegacyExtrasWithoutChangingChapterIndices() {
        val chapter = book("Book", emptyList(), "First").chapters.single()
        val first = chapter.copy(index = 0, title = "First")
        val second = chapter.copy(index = 1, title = "Second", plainText = "Second")
        val notes = chapter.copy(index = 2, title = "Notes", plainText = "Note")
        val unused = chapter.copy(index = 3, title = "Extra", plainText = "Extra")
        assertEquals(
            mapOf(0 to true, 1 to true, 2 to false, 3 to false),
            resolveEpubLinearityRepair(listOf(first, second, notes, unused), listOf(first, second, notes.copy(isLinear = false))),
        )
        assertEquals(null, resolveEpubLinearityRepair(listOf(first, second), listOf(first, notes)))
        assertEquals(null, resolveEpubLinearityRepair(listOf(first, first.copy(index = 1)), listOf(first)))
        val imageA = chapter.copy(
            index = 4,
            title = "Image",
            plainText = "",
            htmlContent = "<img src=\"kairo_epub_assets/old/images/a.png\">"
        )
        val imageB = imageA.copy(index = 5, htmlContent = "<img src=\"kairo_epub_assets/old/images/b.png\">")
        val probeA = imageA.copy(index = 0, isLinear = true, htmlContent = imageA.htmlContent.replace("/old/", "/probe/"))
        val probeB = imageB.copy(index = 1, isLinear = false, htmlContent = imageB.htmlContent.replace("/old/", "/probe/"))
        assertEquals(mapOf(4 to true, 5 to false), resolveEpubLinearityRepair(listOf(imageA, imageB), listOf(probeA, probeB)))
    }

    private fun book(
        title: String,
        authors: List<String>,
        chapterText: String,
    ): Book =
        Book(
            id = BookId("book"),
            title = title,
            authors = authors,
            chapters =
            listOf(
                Chapter(
                    index = 0,
                    title = "Chapter",
                    htmlContent = "<p>$chapterText</p>",
                    plainText = chapterText,
                ),
            ),
        )
}
