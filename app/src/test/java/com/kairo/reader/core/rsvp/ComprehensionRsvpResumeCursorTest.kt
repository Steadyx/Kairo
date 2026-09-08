package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComprehensionRsvpResumeCursorTest : ComprehensionRsvpTestBase() {
    @Test
    fun paragraphBreakFramesKeepOwnOriginalTokenIndex() {
        val tokens = listOf(
            w("Hello"),
            Token(text = "\n\n", type = TokenType.PARAGRAPH_BREAK),
            w("World"),
        )

        val frames = engine.generateFrames(tokens, 0, stableConfig)
        val breakFrame = frames.firstOrNull { frame ->
            frame.tokens.none { it.type == TokenType.WORD }
        } ?: error("Expected a paragraph break frame")

        assertEquals(1, breakFrame.originalTokenIndex)
    }

    @Test
    fun splitLongWordFramesExposeDistinctResumeCursors() {
        val frames =
            engine.generateFrames(
                tokens =
                listOf(
                    Token(
                        text = "supercalifragilisticexpialidocious",
                        type = TokenType.WORD,
                        syllableCount = 8,
                        frequencyScore = 0.1,
                        complexityMultiplier = 1.2,
                    ),
                ),
                startIndex = 0,
                config =
                stableConfig.copy(
                    maxChunkLength = 4,
                    subwordChunkPauseMs = 0L,
                ),
            )

        assertEquals(frames.size, frames.map { it.resumeCursor }.distinct().size)
        assertTrue(frames.all { it.displayOriginalEndExclusive == 1 })
    }

    @Test
    fun repeatedHyphenPartsExposeDistinctVisualCharacterRanges() {
        val frames =
            engine.generateFrames(
                tokens = listOf(w("ha-ha-ha")),
                startIndex = 0,
                config = stableConfig,
            )

        assertEquals(listOf("ha-", "ha-", "ha"), frames.map { it.tokens.first().text })
        assertEquals(listOf(0, 3, 6), frames.map { it.displayOriginalStartCharacterOffset })
        assertEquals(listOf(3, 6, 8), frames.map { it.displayOriginalEndCharacterOffset })
        assertTrue(frames.all { it.displayOriginalStartIndex == 0 })
        assertTrue(frames.all { it.displayOriginalEndExclusive == 1 })
    }

    @Test
    fun phraseChunkingRecordsNextOriginalTokenIndex() {
        val config =
            stableConfig.copy(
                enablePhraseChunking = true,
                maxWordsPerUnit = 3,
                maxCharsPerUnit = 24,
            )
        val tokens = listOf(w("in"), w("the"), w("house"), w("today"))

        val firstFrame = engine.generateFrames(tokens, 0, config, RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH)).first()

        assertEquals(0, firstFrame.originalTokenIndex)
        assertEquals(3, firstFrame.nextOriginalTokenIndex)
    }

    @Test
    fun trailingQuotesStayWithSentenceEnd() {
        val frames =
            engine.generateFrames(
                tokens =
                listOf(
                    p("\""),
                    w("Hello"),
                    p("."),
                    p("\""),
                    w("Next"),
                ),
                startIndex = 0,
                config = stableConfig,
            )

        assertTrue(frames.size >= 2)
        val quoteCount = frames[0].tokens.count {
            it.type == TokenType.PUNCTUATION &&
                it.text == "\""
        }
        assertEquals("Expected opening + closing quote in the first unit", 2, quoteCount)
        assertEquals("Next", frames[1].tokens.first { it.type == TokenType.WORD }.text)
    }

    @Test
    fun openingQuoteAfterSentenceEndStartsNextUnit() {
        val frames =
            engine.generateFrames(
                tokens =
                listOf(
                    w("word"),
                    p("."),
                    p("\u201C"),
                    w("Get"),
                ),
                startIndex = 0,
                config = stableConfig,
            )

        assertTrue(frames.size >= 2)
        assertEquals(listOf("word", "."), frames[0].tokens.map { it.text })
        assertEquals(listOf("\u201C", "Get"), frames[1].tokens.map { it.text })
        assertEquals(2, frames[1].displayOriginalStartIndex)
        assertEquals(4, frames[1].displayOriginalEndExclusive)
        assertEquals(3, frames[1].originalTokenIndex)
    }

    @Test
    fun nestedOpeningPunctuationHasOneVisualSourceRange() {
        val frames =
            engine.generateFrames(
                tokens =
                listOf(
                    w("Earlier"),
                    p("."),
                    p("("),
                    p("\u201C"),
                    w("Hello"),
                    p(","),
                    w("there"),
                    p("."),
                    p("\u201D"),
                    p(")"),
                ),
                startIndex = 0,
                config = stableConfig,
            )

        val quotedFrame = frames.first { frame ->
            frame.tokens.any { token -> token.type == TokenType.WORD && token.text == "Hello" }
        }
        assertEquals(listOf("(", "\u201C", "Hello", ","), quotedFrame.tokens.map { it.text })
        assertEquals(2, quotedFrame.displayOriginalStartIndex)
        assertEquals(6, quotedFrame.displayOriginalEndExclusive)
        assertEquals(4, quotedFrame.originalTokenIndex)
    }

    @Test
    fun cjkPairedPunctuationBelongsToOneVisualFrameRange() {
        val frames =
            engine.generateFrames(
                tokens =
                listOf(
                    p("\u300C"),
                    w("\u4F60\u597D"),
                    p("\uFF01"),
                    p("\u300D"),
                    w("\u7EE7\u7EED"),
                ),
                startIndex = 0,
                config = stableConfig,
            )

        assertEquals(listOf("\u300C", "\u4F60\u597D", "\uFF01", "\u300D"), frames[0].tokens.map { it.text })
        assertEquals(0, frames[0].displayOriginalStartIndex)
        assertEquals(4, frames[0].displayOriginalEndExclusive)
        assertEquals(1, frames[0].originalTokenIndex)
    }
}
