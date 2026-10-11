package com.kairo.reader.ui.rsvp

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.ComprehensionRsvpEngine
import com.kairo.reader.data.rsvp.RsvpFrameSet
import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpFrameAlignmentTest {
    private val engine = ComprehensionRsvpEngine()

    @Test
    fun replacementFramesAlignToLiveResumeCursorBeforePlayback() {
        val frames = listOf(0 to 0, 4 to 40, 4 to 44, 5 to 50).map { (tokenIndex, cursor) ->
            RsvpFrame(
                tokens = listOf(Token("word", TokenType.WORD)),
                durationMs = 120L,
                originalTokenIndex = tokenIndex,
                resumeCursor = cursor,
            )
        }
        val book = RsvpBookContext(BookId("book"), 0, emptyList(), startIndex = 1)
        for (oldFrameIndex in listOf(0, 10)) {
            val runtime = RsvpRuntimeState().apply {
                frameIndex = oldFrameIndex
                currentTokenIndex = 4
                currentResumeCursor = 44
            }

            alignRuntimeToFrameSet(runtime, RsvpFrameSet(frames, 120L), book)

            assertEquals(2, runtime.frameIndex)
            assertEquals(2, runtime.rampStartFrameIndex)
            assertEquals(4, runtime.currentTokenIndex)
            assertEquals(44, runtime.currentResumeCursor)
        }
    }

    @Test
    fun alignFrameIndexTargetsFirstSplitForFreshTokenStart() {
        val config =
            RsvpConfig(
                maxChunkLength = 4,
                subwordChunkPauseMs = 0L,
                startDelayMs = 0L,
                endDelayMs = 0L,
                rampUpFrames = 0,
                rampDownFrames = 0,
                enablePhraseChunking = false,
            )
        val tokens =
            listOf(
                Token(
                    text = "supercalifragilisticexpialidocious",
                    type = TokenType.WORD,
                    syllableCount = 8,
                    frequencyScore = 0.1,
                    complexityMultiplier = 1.2,
                ),
            )

        val frames = engine.generateFrames(tokens, startIndex = 0, config = config)
        val aligned = alignFrameIndex(frames, tokenIndex = 0)

        assertEquals(0, aligned)
    }

    @Test
    fun alignFrameIndexPrefersWordFrameOverBlinkSeparator() {
        val frames =
            listOf(
                RsvpFrame(
                    tokens = listOf(Token(text = "Hello", type = TokenType.WORD)),
                    durationMs = 120L,
                    originalTokenIndex = 0,
                    resumeCursor = 10,
                ),
                RsvpFrame(
                    tokens = listOf(Token(text = " ", type = TokenType.PUNCTUATION)),
                    durationMs = 30L,
                    originalTokenIndex = 0,
                    resumeCursor = 10,
                ),
                RsvpFrame(
                    tokens = listOf(Token(text = "World", type = TokenType.WORD)),
                    durationMs = 120L,
                    originalTokenIndex = 1,
                    resumeCursor = 11,
                ),
            )

        val aligned = alignFrameIndex(frames, tokenIndex = 0)

        assertEquals(0, aligned)
    }

    @Test
    fun alignFrameIndexUsesExactResumeCursorWhenAvailable() {
        val frames = engine.generateFrames(
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
            RsvpConfig(
                maxChunkLength = 4,
                subwordChunkPauseMs = 0L,
                startDelayMs = 0L,
                endDelayMs = 0L,
                rampUpFrames = 0,
                rampDownFrames = 0,
                enablePhraseChunking = false,
            ),
        )

        val targetFrame = frames[1]

        val aligned = alignFrameIndex(frames, tokenIndex = 0, resumeCursor = targetFrame.resumeCursor)

        assertEquals(1, aligned)
    }
}
