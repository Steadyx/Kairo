package com.kairo.reader.ui.navigation

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPositionPolicyTest {
    @Test
    fun savedAndSearchTargetsOnlyUpdateProgressForKnownLinearChapters() {
        val id = BookId("book")
        val book = Book(
            id = id,
            title = "Book",
            authors = emptyList(),
            chapters = listOf(
                Chapter(0, "Chapter", "<p>Text</p>", "Text"),
                Chapter(1, "Notes", "<p>Note</p>", "Note", isLinear = false),
            ),
        )
        assertTrue(isLinearLibraryTarget(listOf(book), id, 0))
        assertFalse(isLinearLibraryTarget(listOf(book), id, 1))
        assertFalse(isLinearLibraryTarget(listOf(book), id, 2))
        assertFalse(isLinearLibraryTarget(emptyList(), id, 0))
    }
}
