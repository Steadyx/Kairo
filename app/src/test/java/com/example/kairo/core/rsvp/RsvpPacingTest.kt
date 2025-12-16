package com.example.kairo.core.rsvp

import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFrame
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpPacingTest {

    @Test
    fun longWordsGetAReadableFloorAtVeryHighWpm() {
        val tokens = listOf(
            Token(text = "Supercalifragilisticexpialidocious", type = TokenType.WORD, syllableCount = 8)
        )

        val config = RsvpConfig(baseWpm = 2000)

        val duration = RsvpPacing.calculateFrameDurationMs(
            frameTokens = tokens,
            config = config,
            prevToken = null,
            nextToken = null
        )

        assertTrue("Expected long word floor to apply, got $duration ms", duration >= 130L)
    }

    @Test
    fun sentenceEndingPunctuationGetsAMinimumPauseAtHighWpm() {
        val tokens = listOf(
            Token(text = "Hello", type = TokenType.WORD),
            Token(text = ".", type = TokenType.PUNCTUATION, pauseAfterMs = 260L)
        )

        val config = RsvpConfig(baseWpm = 1500)
        val duration = RsvpPacing.calculateFrameDurationMs(tokens, config, prevToken = null, nextToken = null)

        assertTrue("Expected minimum punctuation pause to apply, got $duration ms", duration >= 170L)
    }

    @Test
    fun abbreviationDotsDoNotTriggerFullSentencePause() {
        val tokens = listOf(
            Token(text = "Dr", type = TokenType.WORD),
            Token(text = ".", type = TokenType.PUNCTUATION, pauseAfterMs = 260L)
        )

        val config = RsvpConfig(baseWpm = 1500)
        val duration = RsvpPacing.calculateFrameDurationMs(tokens, config, prevToken = null, nextToken = Token("Smith", TokenType.WORD))

        assertTrue("Expected abbreviation dot to stay snappy, got $duration ms", duration < 110L)
    }

    @Test
    fun paragraphBreakRespectsConfiguredMinimumPause() {
        val tokens = listOf(
            Token(text = "Hello", type = TokenType.WORD),
            Token(text = "\n", type = TokenType.PARAGRAPH_BREAK)
        )

        val config = RsvpConfig(baseWpm = 1500, paragraphPauseMs = 250L)
        val duration = RsvpPacing.calculateFrameDurationMs(tokens, config, prevToken = null, nextToken = null)

        assertTrue("Expected paragraph pause to apply, got $duration ms", duration >= 295L)
    }

    @Test
    fun playbackDelayAddsStartAndEndDelays() {
        val frames = listOf(
            RsvpFrame(
                tokens = listOf(Token(text = "Hello", type = TokenType.WORD)),
                durationMs = 0L,
                originalTokenIndex = 0
            )
        )

        val config = RsvpConfig(baseWpm = 300, startDelayMs = 250L, endDelayMs = 350L)
        val delayMs = RsvpPacing.playbackDelayMs(frames, frameIndex = 0, config = config)

        assertEquals(800L, delayMs)
    }
}

