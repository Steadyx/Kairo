package com.kairo.reader.data.local

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.HighlightColor
import com.kairo.reader.core.model.ReadingSession
import com.kairo.reader.core.model.ReadingSessionMode
import com.kairo.reader.core.model.SavedAnnotation
import com.kairo.reader.core.model.SavedAnnotationKind
import com.kairo.reader.core.model.TableOfContentsEntry
import com.kairo.reader.core.model.TableOfContentsTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {
    @Test
    fun importedMetadataCannotCreateOversizedRows() {
        val oversized = "界".repeat(1_048_576)
        val book = Book(BookId("large"), oversized, listOf(oversized), languageTag = oversized, chapters = emptyList())
        val entity = book.toEntity()
        assertEquals(4096, entity.title.length)
        assertEquals(16384, Converters().fromAuthors(entity.authors).length)
        assertEquals(128, entity.languageTag?.length)
        val chapter = Chapter(0, oversized, "<p>Body</p>", "Body").toEntity(book.id)
        assertEquals(4096, chapter.title?.length)
        assertEquals("Body", chapter.plainText)
        val entry = TableOfContentsEntry(oversized, 0, TableOfContentsTarget(0)).toEntity(book.id, 0)
        assertEquals(4096, entry.label.length)
    }

    @Test
    fun metadataLimitsPreserveUnicodeAndOrdinaryMetadata() {
        val title = "😀".repeat(5000)
        val book = Book(BookId("unicode"), title, listOf("Ada", "Grace"), chapters = emptyList())
        val entity = book.toEntity()
        assertEquals("😀".repeat(4096), entity.title)
        assertEquals(book.authors, entity.authors)
        val ordinary = book.copy(title = "A normal title", languageTag = "en-GB")
        assertEquals(ordinary, ordinary.toEntity().toDomain(emptyList()))
    }

    @Test
    fun chapterToEntityPreservesDeferredWordCount() {
        val chapter =
            Chapter(
                index = 0,
                title = "Large chapter",
                htmlContent = "<p>one two three</p>",
                plainText = "one two three",
                wordCount = 0,
            )

        val entity = chapter.toEntity(BookId("book"))

        assertEquals(0, entity.wordCount)
    }

    @Test
    fun tableOfContentsEntryRoundTripsAuthoredDestination() {
        val entry =
            TableOfContentsEntry(
                label = "The Crossing",
                depth = 2,
                target = TableOfContentsTarget(chapterIndex = 4, characterOffset = 315),
            )

        val restored = entry.toEntity(BookId("book"), entryIndex = 7).toDomain()

        assertEquals(entry, restored)
    }

    @Test
    fun bookMapperBuildsChapterFallbackWhenMigratedBookHasNoStoredTableOfContents() {
        val entity =
            BookEntity(
                id = "legacy-book",
                title = "Legacy book",
                authors = emptyList(),
                languageTag = null,
                coverImage = null,
            )
        val chapters =
            listOf(
                ChapterEntity(
                    bookId = entity.id,
                    index = 1,
                    title = "The Crossing",
                    htmlContent = "",
                    plainText = "",
                ),
                ChapterEntity(
                    bookId = entity.id,
                    index = 0,
                    title = "The Arrival",
                    htmlContent = "",
                    plainText = "",
                ),
            )

        val restored = entity.toDomain(chapters = chapters, tableOfContentsEntries = emptyList())

        assertEquals(listOf("The Arrival", "The Crossing"), restored.tableOfContents.map { it.label })
        assertEquals(listOf(0, 1), restored.tableOfContents.map { it.target?.chapterIndex })
    }

    @Test
    fun savedAnnotationRoundTripsThroughEntity() {
        val annotation =
            SavedAnnotation(
                id = "note-1",
                bookId = BookId("book"),
                chapterIndex = 2,
                startTokenIndex = 12,
                endTokenIndex = 18,
                selectedText = "A useful phrase",
                note = "Remember this",
                color = HighlightColor.BLUE,
                kind = SavedAnnotationKind.NOTE,
                createdAt = 100L,
                updatedAt = 200L,
            )

        assertEquals(annotation, annotation.toEntity().toDomain())
    }

    @Test
    fun readingSessionRoundTripsThroughEntity() {
        val session =
            ReadingSession(
                id = "session-1",
                bookId = BookId("book"),
                mode = ReadingSessionMode.BIONIC,
                startedAt = 1_000L,
                endedAt = 601_000L,
                activeDurationMs = 600_000L,
                startChapterIndex = 1,
                startTokenIndex = 10,
                endChapterIndex = 2,
                endTokenIndex = 30,
                wordsRead = 2_000,
                effectiveWpm = 200,
                isWordCountEstimated = false,
            )

        assertEquals(session, session.toEntity().toDomain())
    }

    @Test
    fun unknownStoredEnumsUseSafeDefaults() {
        val annotation =
            SavedAnnotationEntity(
                id = "legacy-note",
                bookId = "book",
                chapterIndex = 0,
                startTokenIndex = 0,
                endTokenIndex = 1,
                selectedText = "Text",
                note = "",
                color = "UNKNOWN",
                kind = "UNKNOWN",
                createdAt = 0L,
                updatedAt = 0L,
            ).toDomain()

        assertEquals(HighlightColor.YELLOW, annotation.color)
        assertEquals(SavedAnnotationKind.HIGHLIGHT, annotation.kind)
    }
}
