package com.example.kairo.ui.rsvp

import com.example.kairo.core.model.RsvpConfig
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
}
