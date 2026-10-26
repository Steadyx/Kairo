package com.kairo.reader.ui.library

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.RsvpConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryProgressTest {
    @Test
    fun buildLibraryEstimatedWpmByBookId_reusesEstimatePerLanguage() {
        val books =
            listOf(
                book(id = "first-english", wordCount = 600, languageTag = "en"),
                book(id = "second-english", wordCount = 800, languageTag = "en"),
                book(id = "french", wordCount = 500, languageTag = "fr"),
            )
        val estimatedLanguages = mutableListOf<String?>()

        val result =
            buildLibraryEstimatedWpmByBookId(
                books = books,
                config = RsvpConfig(),
                fallbackEstimatedWpm = 300,
            ) { _, languageTag ->
                estimatedLanguages += languageTag
                when (languageTag) {
                    "en" -> 320
                    "fr" -> 280
                    else -> 300
                }
            }

        assertEquals(
            mapOf(
                "first-english" to 320,
                "second-english" to 320,
                "french" to 280,
            ),
            result,
        )
        assertEquals(listOf("en", "fr"), estimatedLanguages)
    }

    @Test
    fun buildLibraryProgress_usesEstimatedWpmForEachBook() =
        runTest {
            val books =
                listOf(
                    book(id = "fast", wordCount = 600),
                    book(id = "slow", wordCount = 600),
                )

            val progress =
                buildLibraryProgress(
                    books = books,
                    positions = emptyList(),
                    estimatedWpmByBookId =
                    mapOf(
                        "fast" to 600,
                        "slow" to 300,
                    ),
                )

            assertEquals(1, progress.getValue("fast").remainingMinutes)
            assertEquals(2, progress.getValue("slow").remainingMinutes)
        }

    @Test
    fun legacyAuxiliaryPositionDoesNotPretendTheBookIsComplete() = runTest {
        val original = book(id = "book", wordCount = 600)
        val note = Chapter(index = 1, title = "Notes", htmlContent = "", plainText = "Note", isLinear = false)
        val result = buildLibraryProgress(
            books = listOf(original.copy(chapters = original.chapters + note)),
            positions = listOf(ReadingPosition(BookId("book"), 1, 0)),
            estimatedWpmByBookId = mapOf("book" to 300),
        )
        assertEquals(null, result["book"])
    }

    private fun book(
        id: String,
        wordCount: Int,
        languageTag: String? = null,
    ): Book =
        Book(
            id = BookId(id),
            title = id,
            authors = emptyList(),
            languageTag = languageTag,
            chapters =
            listOf(
                Chapter(
                    index = 0,
                    title = null,
                    htmlContent = "",
                    plainText = "",
                    wordCount = wordCount,
                ),
            ),
        )
}
