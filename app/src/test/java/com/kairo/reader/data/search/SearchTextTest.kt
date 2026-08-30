package com.kairo.reader.data.search

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.LibrarySearchResult
import com.kairo.reader.core.model.LibrarySearchResultKind
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextTest {
    @Test
    fun findsRepeatedMatchesCaseInsensitivelyWithinLimit() {
        val offsets = findSearchMatchOffsets("Flow then flow then FLOW", "flow", limit = 2)

        assertEquals(listOf(0, 10), offsets)
    }

    @Test
    fun findsGreekFinalSigmaCaseInsensitively() {
        assertEquals(listOf(0), findSearchMatchOffsets("ΟΣ", "οσ", limit = 1))
    }

    @Test
    fun snippetAddsEllipsesAndNormalizesWhitespace() {
        val text = "Before words\n\nThe searched phrase appears after words"

        val snippet =
            buildSearchSnippet(
                text = text,
                matchOffset = text.indexOf("searched"),
                matchLength = "searched".length,
                contextCharacters = 5,
            )

        assertEquals("…The searched phra…", snippet)
    }

    @Test
    fun snippetContextDoesNotSplitSupplementaryCharacters() {
        val text = "😀" + "a".repeat(55) + "needle"

        val snippet =
            buildSearchSnippet(
                text = text,
                matchOffset = text.indexOf("needle"),
                matchLength = "needle".length,
                contextCharacters = 56,
            )

        assertEquals(text, snippet)
        assertEquals(0x1F600, snippet.codePointAt(0))
    }

    @Test
    fun sqlLikePatternEscapesWildcardCharacters() {
        assertEquals("%100\\%\\_done\\\\now%", "100%_done\\now".toSqlLikePattern())
    }

    @Test
    fun fairMergeRoundRobinsGroupsWithinLimit() {
        val groups =
            listOf(
                listOf(result("book-1"), result("book-2")),
                listOf(result("passage-1"), result("passage-2")),
                listOf(result("saved-1")),
            )

        assertEquals(
            listOf("book-1", "passage-1", "saved-1", "book-2"),
            fairMergeSearchResults(groups, limit = 4).map { it.id },
        )
    }

    @Test
    fun phraseMatchResolvesItsFullTokenRange() {
        val tokens =
            listOf(
                Token("one", TokenType.WORD),
                Token("two", TokenType.WORD),
                Token("three", TokenType.WORD),
            )

        assertEquals(
            1..2,
            resolveSearchTokenRange(
                plainText = "one two three",
                tokens = tokens,
                matchOffset = 4,
                matchLength = "two three".length,
            ),
        )
    }

    @Test
    fun unresolvedPassageResolvesItsFullPhraseRangeAfterChapterLoad() {
        val tokens =
            listOf(
                Token("one", TokenType.WORD),
                Token("two", TokenType.WORD),
                Token("three", TokenType.WORD),
            )
        val result =
            LibrarySearchResult(
                id = "late-passage",
                kind = LibrarySearchResultKind.PASSAGE,
                bookId = BookId("book"),
                bookTitle = "Book",
                chapterIndex = 0,
                chapterTitle = null,
                tokenIndex = 0,
                matchStartCodePointOffset = 4,
                matchLengthCodePoints = "two three".length,
                title = "Passage",
                snippet = "two three",
            )

        assertEquals(
            1..2,
            resolveSearchResultTokenRange(result, "one two three", tokens),
        )
    }

    @Test
    fun codePointOffsetConvertsToUtf16AfterSupplementaryCharacters() {
        val plainText = "😀😀😀 abc needle"

        assertEquals(11, codePointOffsetToUtf16Offset(plainText, codePointOffset = 8))
    }

    @Test
    fun supplementaryCharacterInsideQueryResolvesTheFullTokenRange() {
        val plainText = "one 😀 two three"
        val query = "😀 two"
        val tokens =
            listOf(
                Token("one", TokenType.WORD),
                Token("😀", TokenType.WORD),
                Token("two", TokenType.WORD),
                Token("three", TokenType.WORD),
            )
        val result =
            LibrarySearchResult(
                id = "unicode-passage",
                kind = LibrarySearchResultKind.PASSAGE,
                bookId = BookId("book"),
                bookTitle = "Book",
                chapterIndex = 0,
                chapterTitle = null,
                tokenIndex = 0,
                matchStartCodePointOffset = 4,
                matchLengthCodePoints = query.codePointCount(0, query.length),
                title = "Passage",
                snippet = query,
            )

        assertEquals(1..2, resolveSearchResultTokenRange(result, plainText, tokens))
    }

    @Test
    fun normalizationBoundsRawInputBeforeTrimming() {
        val normalized = normalizeLibrarySearchQuery(" ".repeat(250) + "x".repeat(250))

        assertEquals(150, normalized.length)
    }
}

private fun result(id: String) =
    com.kairo.reader.core.model.LibrarySearchResult(
        id = id,
        kind = com.kairo.reader.core.model.LibrarySearchResultKind.BOOK,
        bookId = com.kairo.reader.core.model.BookId("book"),
        bookTitle = "Book",
        chapterIndex = 0,
        chapterTitle = null,
        tokenIndex = 0,
        title = id,
        snippet = "",
    )
