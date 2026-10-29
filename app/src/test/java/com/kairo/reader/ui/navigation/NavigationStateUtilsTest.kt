package com.kairo.reader.ui.navigation

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.ui.rsvp.RsvpResumePoint
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationStateUtilsTest {
    @Test
    fun completedTimedReadingSkipsAuxiliaryChapters() {
        val target = resolveRsvpReturnTarget(
            resumePoint = RsvpResumePoint(tokenIndex = 2),
            currentChapterIndex = 0,
            chapterCount = 4,
            currentChapterTokens = listOf(word("Last")),
            linearChapterIndices = listOf(0, 2),
        )
        assertEquals(RsvpReturnTarget(chapterIndex = 2, tokenIndex = 0, resumeCursor = -1), target)
        val final = resolveRsvpReturnTarget(
            resumePoint = RsvpResumePoint(tokenIndex = 2),
            currentChapterIndex = 2,
            chapterCount = 4,
            currentChapterTokens = listOf(word("Last")),
            linearChapterIndices = listOf(0, 2),
        )
        assertEquals(2, final.chapterIndex)
    }

    @Test
    fun resolveWordIndex_returnsSentinelWhenMappingMissing() {
        assertEquals(-1, resolveWordIndex(wordCountByToken = null, tokenIndex = 4))
        assertEquals(-1, resolveWordIndex(wordCountByToken = IntArray(0), tokenIndex = 4))
    }

    @Test
    fun resolveWordIndex_clampsAgainstMappingBounds() {
        val wordCountByToken = intArrayOf(1, 1, 2)

        assertEquals(1, resolveWordIndex(wordCountByToken, tokenIndex = -4))
        assertEquals(2, resolveWordIndex(wordCountByToken, tokenIndex = 99))
    }

    @Test
    fun resolveRsvpReturnTarget_movesToNextChapterWhenResumePassesChapterEnd() {
        val target =
            resolveRsvpReturnTarget(
                resumePoint = RsvpResumePoint(tokenIndex = 10, resumeCursor = 3),
                currentChapterIndex = 1,
                chapterCount = 3,
                currentChapterTokens = listOf(word("Last")),
            )

        assertEquals(
            RsvpReturnTarget(
                chapterIndex = 2,
                tokenIndex = 0,
                resumeCursor = -1,
            ),
            target,
        )
    }

    @Test
    fun resolveRsvpReturnTarget_resolvesPunctuationToNearestWord() {
        val target =
            resolveRsvpReturnTarget(
                resumePoint = RsvpResumePoint(tokenIndex = 1, resumeCursor = 7),
                currentChapterIndex = 0,
                chapterCount = 1,
                currentChapterTokens = listOf(word("Hello"), punctuation(","), word("world")),
            )

        assertEquals(
            RsvpReturnTarget(
                chapterIndex = 0,
                tokenIndex = 2,
                resumeCursor = 7,
            ),
            target,
        )
    }

    @Test
    fun resolveRsvpReturnTarget_usesExplicitChapterWhenStayingInCurrentChapter() {
        val target =
            resolveRsvpReturnTarget(
                resumePoint = RsvpResumePoint(
                    chapterIndex = 4,
                    tokenIndex = 0,
                    resumeCursor = 2,
                ),
                currentChapterIndex = 3,
                chapterCount = 6,
                currentChapterTokens = listOf(word("Resume")),
            )

        assertEquals(
            RsvpReturnTarget(
                chapterIndex = 4,
                tokenIndex = 0,
                resumeCursor = 2,
            ),
            target,
        )
    }

    @Test
    fun resolveRsvpReturnTarget_keepsLastWordWhenResumePassesFinalChapterEnd() {
        val target =
            resolveRsvpReturnTarget(
                resumePoint = RsvpResumePoint(tokenIndex = 99, resumeCursor = 8),
                currentChapterIndex = 2,
                chapterCount = 3,
                currentChapterTokens = listOf(word("Final"), punctuation(".")),
            )

        assertEquals(
            RsvpReturnTarget(
                chapterIndex = 2,
                tokenIndex = 0,
                resumeCursor = -1,
            ),
            target,
        )
    }

    private fun word(text: String): Token = Token(text = text, type = TokenType.WORD)

    private fun punctuation(text: String): Token = Token(text = text, type = TokenType.PUNCTUATION)
}
