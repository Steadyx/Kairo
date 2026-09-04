package com.kairo.reader.core.export

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.HighlightColor
import com.kairo.reader.core.model.SavedAnnotation
import com.kairo.reader.core.model.SavedAnnotationItem
import com.kairo.reader.core.model.SavedAnnotationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteExportCoreTest {
    @Test
    fun bookScopeIncludesOnlyNotesForTheRequestedSourceInReadingOrder() {
        val firstBook = book("book-a", "Zeta Source")
        val secondBook = book("book-b", "Alpha Source")
        val annotations =
            listOf(
                annotation(firstBook, "late", chapter = 1, token = 20),
                annotation(secondBook, "other", chapter = 0, token = 1),
                annotation(firstBook, "highlight", chapter = 0, token = 1, kind = SavedAnnotationKind.HIGHLIGHT),
                annotation(firstBook, "early", chapter = 0, token = 9),
            )

        val document =
            NoteExportDocumentBuilder.build(
                scope = NoteExportScope.Book(firstBook.id.value),
                annotations = annotations,
                booksInLibraryOrder = listOf(secondBook, firstBook),
                generatedAt = 500L,
            )

        assertEquals(listOf("Zeta Source"), document.sources.map { it.title })
        assertEquals(listOf("early", "late"), document.sources.single().entries.map { it.note })
        assertEquals(listOf("Chapter 1", "Chapter 2"), document.sources.single().entries.map { it.chapterTitle })
    }

    @Test
    fun allScopeUsesLibraryOrderAndRejectsAnEmptyNoteSet() {
        val firstBook = book("book-a", "First")
        val secondBook = book("book-b", "Second")
        val document =
            NoteExportDocumentBuilder.build(
                scope = NoteExportScope.All,
                annotations =
                listOf(
                    annotation(secondBook, "second", chapter = 0, token = 1),
                    annotation(firstBook, "first", chapter = 0, token = 1),
                ),
                booksInLibraryOrder = listOf(firstBook, secondBook),
                generatedAt = 0L,
            )

        assertEquals(listOf("First", "Second"), document.sources.map { it.title })
        val failure =
            assertThrows(NoteExportResolutionException::class.java) {
                NoteExportDocumentBuilder.build(
                    scope = NoteExportScope.All,
                    annotations = listOf(annotation(firstBook, "highlight", 0, 1, SavedAnnotationKind.HIGHLIGHT)),
                    booksInLibraryOrder = listOf(firstBook),
                    generatedAt = 0L,
                )
            }
        assertEquals(NoteExportResolutionFailure.NO_NOTES, failure.failure)
    }

    @Test
    fun markdownEscapesLiteralUserTextAndPreservesQuotedLines() {
        val document =
            NoteExportDocument(
                scope = NoteExportScope.Single("note"),
                generatedAt = 0L,
                sources =
                listOf(
                    NoteExportSource(
                        title = "# Source & <study>",
                        authors = listOf("A *Writer*"),
                        entries =
                        listOf(
                            NoteExportEntry(
                                chapterTitle = "[Methods]",
                                chapterNumber = 1,
                                note = "- literal\n<script>alert(1)</script>",
                                passage = "first > line\nsecond | line",
                                highlightColor = HighlightColor.BLUE,
                                createdAt = 1L,
                                updatedAt = 2L,
                            ),
                        ),
                    ),
                ),
            )

        val markdown = NoteExportMarkdownRenderer(TestLocalization).render(document)

        assertTrue(markdown.contains("## \\# Source &amp; &lt;study&gt;"))
        assertTrue(markdown.contains("\\- literal\n&lt;script&gt;alert\\(1\\)&lt;/script&gt;"))
        assertTrue(markdown.contains("> first &gt; line\n> second \\| line"))
        assertFalse(markdown.contains("<script>"))
    }

    @Test
    fun filenamesKeepUnicodeWhileRemovingUnsafeCharactersAndCodePointTruncating() {
        val fileName =
            NoteExportFileNames.suggestedFileName(
                scope = NoteExportScope.Book("book"),
                format = NoteExportFormat.MARKDOWN,
                date = "2026-09-04",
                sourceTitle = "研究/ملاحظات: résumé 🚀",
                allNotesTitle = "Kairo notes",
                singleNoteLabel = "note",
            )
        assertEquals("研究 ملاحظات résumé 🚀 - 2026-09-04.md", fileName)

        val truncated = NoteExportFileNames.sanitizeBasename("😀😀😀", "fallback", maxCodePoints = 2)
        assertEquals("😀😀", truncated)
    }

    private fun book(
        id: String,
        title: String,
    ): Book =
        Book(
            id = BookId(id),
            title = title,
            authors = listOf("Researcher"),
            chapters =
            listOf(
                Chapter(0, "Chapter 1", "", ""),
                Chapter(1, "Chapter 2", "", ""),
            ),
        )

    private fun annotation(
        book: Book,
        note: String,
        chapter: Int,
        token: Int,
        kind: SavedAnnotationKind = SavedAnnotationKind.NOTE,
    ): SavedAnnotationItem =
        SavedAnnotationItem(
            annotation =
            SavedAnnotation(
                id = "$note-$chapter-$token",
                bookId = book.id,
                chapterIndex = chapter,
                startTokenIndex = token,
                endTokenIndex = token + 2,
                selectedText = "Quoted $note",
                note = if (kind == SavedAnnotationKind.NOTE) note else "",
                color = HighlightColor.YELLOW,
                kind = kind,
                createdAt = token.toLong(),
                updatedAt = token.toLong(),
            ),
            book = book,
            chapterCount = book.chapters.size,
        )
}

private object TestLocalization : NoteExportLocalization {
    override val authorLabel = "Authors"
    override val unknownAuthor = "Unknown"
    override val noteLabel = "Note"
    override val passageLabel = "Passage"
    override val createdLabel = "Created"
    override val updatedLabel = "Updated"

    override fun documentTitle(scope: NoteExportScope) = "Notes"

    override fun formatDate(timestamp: Long) = "date-$timestamp"

    override fun exportedOn(formattedDate: String) = "Exported $formattedDate"

    override fun contentsSummary(noteCount: Int, sourceCount: Int) = "$noteCount notes, $sourceCount sources"

    override fun chapterFallback(chapterNumber: Int) = "Chapter $chapterNumber"

    override fun continued(label: String) = "$label continued"

    override fun pageNumber(pageNumber: Int) = "Page $pageNumber"
}
