package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.TableOfContentsEntry
import com.kairo.reader.core.model.TableOfContentsTarget
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChapterLinkPolicyTest {
    @Test
    fun tableOfContentsDestinationIsNotAnInteractiveReaderLink() {
        val book =
            Book(
                id = BookId("book"),
                title = "Book",
                authors = emptyList(),
                chapters =
                listOf(
                    chapter(index = 0, title = "Contents"),
                    chapter(index = 1, title = "Chapter One"),
                ),
                tableOfContents =
                listOf(
                    TableOfContentsEntry(
                        label = "Table of Contents",
                        depth = 0,
                        target = TableOfContentsTarget(chapterIndex = 0),
                    ),
                ),
            )
        val targets = resolveNonInteractiveChapterLinkTargets(book)
        val token = Token(text = "Chapter", type = TokenType.WORD, linkChapterIndex = 0)

        assertEquals(setOf(0), targets)
        assertNull(resolveInteractiveChapterLinkTarget(token, targets))
    }

    @Test
    fun denseNavigationChapterIsRecognisedWithoutAnEnglishTitle() {
        val links =
            (1..4).joinToString(separator = " ") { index ->
                "<a href=\"kairo://chapter/$index\">$index</a>"
            }
        val book =
            Book(
                id = BookId("book"),
                title = "Book",
                authors = emptyList(),
                chapters =
                listOf(
                    chapter(
                        index = 0,
                        title = "Sommaire",
                        htmlContent = "<p>$links</p>",
                        plainText = "Un Deux Trois Quatre",
                    ),
                    chapter(index = 1, title = "Chapitre Un"),
                ),
            )

        assertEquals(setOf(0), resolveNonInteractiveChapterLinkTargets(book))
    }

    @Test
    fun ordinaryInternalChapterLinkRemainsInteractive() {
        val token = Token(text = "Footnote", type = TokenType.WORD, linkChapterIndex = 3)

        assertEquals(3, resolveInteractiveChapterLinkTarget(token, nonInteractiveTargets = setOf(0)))
        assertTrue(resolveNonInteractiveChapterLinkTargets(bookWithOrdinaryChapters()).isEmpty())
    }

    private fun bookWithOrdinaryChapters(): Book =
        Book(
            id = BookId("book"),
            title = "Book",
            authors = emptyList(),
            chapters =
            listOf(
                chapter(index = 0, title = "Opening"),
                chapter(index = 1, title = "Chapter One"),
            ),
        )

    private fun chapter(
        index: Int,
        title: String,
        htmlContent: String = "<p>Chapter text.</p>",
        plainText: String = "Chapter text.",
    ): Chapter =
        Chapter(
            index = index,
            title = title,
            htmlContent = htmlContent,
            plainText = plainText,
        )
}
