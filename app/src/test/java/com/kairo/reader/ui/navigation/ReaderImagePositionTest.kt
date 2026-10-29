package com.kairo.reader.ui.navigation

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.data.local.toDomain
import com.kairo.reader.data.local.toEntity
import com.kairo.reader.ui.reader.ChapterData
import com.kairo.reader.ui.reader.ReaderImageBlock
import com.kairo.reader.ui.reader.ReaderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderImagePositionTest {
    @Test
    fun auxiliaryChapterDoesNotOverwriteLinearReadingPosition() {
        val chapter = ChapterData(
            tokens = emptyList(),
            plainText = "",
            paragraphs = emptyList(),
            blocks = listOf(ReaderImageBlock("note", 0)),
            firstWordIndex = -1,
            imagePaths = listOf("note"),
            pages = emptyList(),
            wordCountByToken = IntArray(0),
            totalWords = 0,
        )
        assertNull(buildReaderPosition(BookId("book"), ReaderUiState(isLinearChapter = false, chapterData = chapter)))
        assertNull(buildTimedReaderPosition(BookId("book"), ReaderUiState(isLinearChapter = false, chapterData = chapter), 3, 2))
        val linear = ReaderUiState(chapterIndex = 1, isLinearChapter = true, chapterData = chapter)
        val saved = requireNotNull(buildTimedReaderPosition(BookId("book"), linear, 3, 2))
        assertEquals(1, saved.chapterIndex)
        assertEquals(3, saved.tokenIndex)
        assertEquals(2, saved.rsvpResumeCursor)
    }

    @Test
    fun nonlinearChapterSurvivesMapping() {
        val chapter = Chapter(4, "Notes", "<p>notes</p>", "notes", isLinear = false)
        assertEquals(chapter, chapter.toEntity(BookId("book")).toDomain())
    }

    @Test
    fun imagePageAndChapterSurvivePositionMapping() {
        val chapter = ChapterData(
            tokens = emptyList(),
            plainText = "",
            paragraphs = emptyList(),
            blocks = listOf(ReaderImageBlock("a", 0), ReaderImageBlock("b", 1)),
            firstWordIndex = -1,
            imagePaths = listOf("a", "b"),
            pages = emptyList(),
            wordCountByToken = IntArray(0),
            totalWords = 0,
        )
        val state = ReaderUiState(chapterIndex = 3, pageIndexOverride = 1, chapterData = chapter)
        val position = requireNotNull(buildReaderPosition(BookId("book"), state))
        assertEquals(3, position.chapterIndex)
        assertEquals(1, position.imagePageIndex)
        assertEquals(position, position.toEntity().toDomain())
        assertEquals(0, buildReaderPosition(BookId("book"), state.copy(pageIndexOverride = null))?.imagePageIndex)
        assertNull(buildReaderPosition(BookId("book"), state.copy(chapterData = null)))
    }
}
