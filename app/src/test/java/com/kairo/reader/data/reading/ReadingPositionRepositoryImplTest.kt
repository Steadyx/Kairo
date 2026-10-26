package com.kairo.reader.data.reading

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingPosition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPositionRepositoryImplTest {
    @Test
    fun imagePageZeroIsAQualifiedPosition() {
        val current = ReadingPosition(BookId("book"), 3, 0, imagePageIndex = 2)
        val next = ReadingPosition(BookId("book"), 0, 0, imagePageIndex = 0)
        assertFalse(shouldIgnoreUnqualifiedStartOverwrite(current, next))
        assertTrue(shouldIgnoreUnqualifiedStartOverwrite(current, ReadingPosition(BookId("book"), 0, 0)))
    }

    @Test
    fun unqualifiedBookStartDoesNotOverwriteExistingProgress() {
        val current =
            ReadingPosition(
                bookId = BookId("book"),
                chapterIndex = 2,
                tokenIndex = 120,
                wordIndex = 80,
                rsvpResumeCursor = 120,
            )
        val staleStart =
            ReadingPosition(
                bookId = BookId("book"),
                chapterIndex = 0,
                tokenIndex = 0,
            )

        assertTrue(shouldIgnoreUnqualifiedStartOverwrite(current, staleStart))
    }

    @Test
    fun qualifiedBookStartCanStillBeSaved() {
        val current =
            ReadingPosition(
                bookId = BookId("book"),
                chapterIndex = 2,
                tokenIndex = 120,
                wordIndex = 80,
            )
        val deliberateStart =
            ReadingPosition(
                bookId = BookId("book"),
                chapterIndex = 0,
                tokenIndex = 0,
                wordIndex = 0,
            )

        assertFalse(shouldIgnoreUnqualifiedStartOverwrite(current, deliberateStart))
    }

    @Test
    fun startGuardOnlyAppliesToSameBook() {
        val current =
            ReadingPosition(
                bookId = BookId("other"),
                chapterIndex = 2,
                tokenIndex = 120,
                wordIndex = 80,
            )
        val staleStart =
            ReadingPosition(
                bookId = BookId("book"),
                chapterIndex = 0,
                tokenIndex = 0,
            )

        assertFalse(shouldIgnoreUnqualifiedStartOverwrite(current, staleStart))
    }
}
