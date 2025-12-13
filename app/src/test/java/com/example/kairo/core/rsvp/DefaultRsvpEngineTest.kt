package com.example.kairo.core.rsvp

import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRsvpEngineTest {

    private val engine = DefaultRsvpEngine()

    private fun w(text: String, complexity: Double = 1.0) =
        Token(text = text, type = TokenType.WORD, complexityMultiplier = complexity)

    private fun p(text: String) =
        Token(text = text, type = TokenType.PUNCTUATION)

    private val config = RsvpConfig(
        baseWpm = 300,
        wordsPerFrame = 1,
        maxChunkLength = 50,
        startDelayMs = 0L,
        endDelayMs = 0L,
        rampUpFrames = 0,
        rampDownFrames = 0,
        useAdaptiveTiming = false,
        useClausePausing = false,
        useDialogueDetection = false
    )

    @Test
    fun abbreviationDotsDoNotCauseSentencePause() {
        val abbrevTokens = listOf(w("Dr"), p("."), w("Alice"))
        val normalTokens = listOf(w("Hello"), p("."), w("Alice"))

        val abbrevFrames = engine.generateFrames(abbrevTokens, 0, config)
        val normalFrames = engine.generateFrames(normalTokens, 0, config)

        assertTrue(abbrevFrames.isNotEmpty() && normalFrames.isNotEmpty())
        assertTrue(
            "Abbreviation dot should be shorter than sentence dot",
            abbrevFrames[0].durationMs < normalFrames[0].durationMs - 80
        )
    }

    @Test
    fun decimalPointsDoNotPause() {
        val decimalTokens = listOf(w("3"), p("."), w("14"), w("pi"))
        val sentenceTokens = listOf(w("3"), p("."), w("pi"))

        val decimalFrames = engine.generateFrames(decimalTokens, 0, config)
        val sentenceFrames = engine.generateFrames(sentenceTokens, 0, config)

        assertTrue(decimalFrames.isNotEmpty() && sentenceFrames.isNotEmpty())
        assertTrue(
            "Decimal point should not cause a sentence pause",
            decimalFrames[0].durationMs < sentenceFrames[0].durationMs - 80
        )
    }

    @Test
    fun thousandSeparatorsDoNotPause() {
        val numberTokens = listOf(w("1"), p(","), w("000"), w("items"))
        val commaTokens = listOf(w("Yes"), p(","), w("items"))

        val numberFrames = engine.generateFrames(numberTokens, 0, config)
        val commaFrames = engine.generateFrames(commaTokens, 0, config)

        assertTrue(numberFrames.isNotEmpty() && commaFrames.isNotEmpty())
        assertTrue(
            "Comma inside number should be shorter than a normal comma pause",
            numberFrames[0].durationMs < commaFrames[0].durationMs - 40
        )
    }

    @Test
    fun hyphenatedWordsAddMicroPauseBetweenParts() {
        val tokens = listOf(w("self-aware"))
        val frames = engine.generateFrames(tokens, 0, config)

        // Split into two frames: "self-" and "aware"
        assertEquals(2, frames.size)

        val baseMsPerWord = 60_000.0 / config.baseWpm
        val expectedFirstFrameMin = (baseMsPerWord * 1.15).toLong()
        val expectedFirstFrameMax = (baseMsPerWord * 1.45).toLong()

        assertTrue(
            "First hyphen part should include a small extra pause",
            frames[0].durationMs in expectedFirstFrameMin..expectedFirstFrameMax
        )
    }

    @Test
    fun signedNumericTokensAreNotSplit() {
        val tokens = listOf(w("-35c"))
        val frames = engine.generateFrames(tokens, 0, config)

        assertEquals(1, frames.size)
        val word = frames.first().tokens.first { it.type == TokenType.WORD }.text
        assertEquals("-35c", word)
    }
}
