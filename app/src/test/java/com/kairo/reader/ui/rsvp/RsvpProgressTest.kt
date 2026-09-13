package com.kairo.reader.ui.rsvp

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpProgressTest {
    private val tokens = List(100) { Token("word", TokenType.WORD) }

    @Test
    fun halfwayLaunchUsesTheEntireChapter() {
        val progress = RsvpProgressBasis(tokens).at(49)
        assertEquals(50, progress.currentWord)
        assertEquals(100, progress.totalWords)
        assertEquals(0.5f, progress.fraction, 0.0001f)
    }

    @Test
    fun readerAndRsvpShareChapterProgressAtSeventyFivePercent() {
        val readerProgress = com.kairo.reader.core.model.chapterReadingProgress(
            wordCountByToken = com.kairo.reader.core.model.buildWordCountByToken(tokens),
            tokenIndex = 74,
            totalWords = 100,
        )
        val rsvpProgress = RsvpProgressBasis(tokens).at(74)
        assertEquals(readerProgress, rsvpProgress)
        assertEquals(75, rsvpProgress.percent)
        assertEquals(RsvpProgress(100, 100), RsvpProgressBasis(tokens).at(74, completed = true))
    }

    @Test
    fun punctuationAndStructuralTokensDoNotInflateWordProgress() {
        val source = listOf(
            Token("one", TokenType.WORD),
            Token(",", TokenType.PUNCTUATION),
            Token("two", TokenType.WORD),
        )
        val basis = RsvpProgressBasis(source)
        assertEquals(RsvpProgress(1, 2), basis.at(0))
        assertEquals(RsvpProgress(1, 2), basis.at(1))
        assertEquals(RsvpProgress(2, 2), basis.at(2))
    }

    @Test
    fun rebuildingAtTheSavedSourcePositionDoesNotResetProgress() {
        val beforeRotation = RsvpProgressBasis(tokens).at(74)
        val afterRotation = RsvpProgressBasis(tokens.toList()).at(74)
        assertEquals(beforeRotation, afterRotation)
        assertEquals(RsvpProgress(75, 100), afterRotation)
    }

    @Test
    fun emptyAndOutOfRangePositionsRemainBounded() {
        assertEquals(0f, RsvpProgressBasis(emptyList()).at(0).fraction, 0f)
        val basis = RsvpProgressBasis(tokens)
        assertEquals(RsvpProgress(0, 100), basis.at(-1))
        assertEquals(RsvpProgress(100, 100), basis.at(Int.MAX_VALUE))
    }
}
