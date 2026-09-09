package com.kairo.reader.ui.rsvp

import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpDisplayFramePolicyTest {
    @Test
    fun pausingDuringParagraphBreakRestoresTheWordForContextReview() {
        val previous = frame(word("previous"), durationMs = 120L)
        // Break frames reach the UI as whitespace-only punctuation markers.
        val paragraphPause = frame(Token(" ", TokenType.PUNCTUATION), durationMs = 420L)
        val next = frame(word("next"), durationMs = 120L)

        val displayed =
            resolveRsvpDisplayFrame(
                frames = listOf(previous, paragraphPause, next),
                frameIndex = 1,
                contextAssistMode = RsvpContextAssistMode.PREVIOUS_WORDS,
                isPlaying = false,
            )

        assertEquals(previous, displayed)
    }

    @Test
    fun otherContextModesKeepTheStructuralPauseFrame() {
        val previous = frame(word("previous"))
        val paragraphPause = frame(Token(" ", TokenType.PUNCTUATION))

        val displayed =
            resolveRsvpDisplayFrame(
                frames = listOf(previous, paragraphPause),
                frameIndex = 1,
                contextAssistMode = RsvpContextAssistMode.PREVIOUS_WORDS,
            )

        assertEquals(paragraphPause, displayed)
    }

    @Test
    fun leadingStructuralPauseUsesNextReadableFrame() {
        val pagePause = frame(Token(" ", TokenType.PUNCTUATION))
        val next = frame(word("opening"))

        val displayed =
            resolveRsvpDisplayFrame(
                frames = listOf(pagePause, next),
                frameIndex = 0,
                contextAssistMode = RsvpContextAssistMode.PREVIOUS_WORDS,
                isPlaying = false,
            )

        assertEquals(next, displayed)
    }

    @Test
    fun separationKeepsTheSameContextSourceInEveryMode() {
        val previous = frame(word("had"))
        val gap = frame(Token(" ", TokenType.PUNCTUATION), 20L).copy(isWordSeparation = true)
        for (mode in RsvpContextAssistMode.entries) {
            assertEquals(previous, resolveRsvpDisplayFrame(listOf(previous, gap), 1, mode))
        }
    }

    private fun frame(
        token: Token,
        durationMs: Long = 100L,
    ): RsvpFrame = RsvpFrame(tokens = listOf(token), durationMs = durationMs)

    private fun word(text: String): Token = Token(text, TokenType.WORD)
}
