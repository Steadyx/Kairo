package com.example.kairo.ui.rsvp

import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFrame
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.rsvp.ComprehensionRsvpEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpFrameAlignmentTest {
    private val engine = ComprehensionRsvpEngine()

    @Test
    fun alignFrameIndexTargetsLastSplitForToken() {
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

        assertEquals(frames.lastIndex, aligned)
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
