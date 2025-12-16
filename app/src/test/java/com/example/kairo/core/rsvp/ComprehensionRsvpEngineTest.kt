package com.example.kairo.core.rsvp

import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import org.junit.Assert.assertTrue
import org.junit.Test

class ComprehensionRsvpEngineTest {

    private val engine = ComprehensionRsvpEngine()

    @Test
    fun longWordsNeverFlashTooFastAtHighWpm() {
        val config = RsvpConfig(
            tempoMsPerWord = 60L,
            longWordMinMs = 180L,
            longWordChars = 10,
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
            enablePhraseChunking = false
        )

        val frames = engine.generateFrames(
            tokens = listOf(
                Token(
                    text = "Supercalifragilisticexpialidocious",
                    type = TokenType.WORD,
                    syllableCount = 8,
                    frequencyScore = 0.15,
                    complexityMultiplier = 1.6
                )
            ),
            startIndex = 0,
            config = config
        )

        assertTrue(frames.isNotEmpty())
        assertTrue("Expected >= ${config.longWordMinMs}ms, got ${frames[0].durationMs}ms", frames[0].durationMs >= config.longWordMinMs)
    }

    @Test
    fun sentenceEndPauseIsNotAbruptAtHighWpm() {
        val config = RsvpConfig(
            tempoMsPerWord = 65L,
            sentenceEndPauseMs = 240L,
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
            enablePhraseChunking = false
        )

        val plain = engine.generateFrames(
            tokens = listOf(Token(text = "Hello", type = TokenType.WORD, frequencyScore = 1.0)),
            startIndex = 0,
            config = config
        ).first().durationMs

        val withPeriod = engine.generateFrames(
            tokens = listOf(
                Token(text = "Hello", type = TokenType.WORD, frequencyScore = 1.0),
                Token(text = ".", type = TokenType.PUNCTUATION)
            ),
            startIndex = 0,
            config = config
        ).first().durationMs

        assertTrue("Expected punctuation to add a meaningful pause", withPeriod - plain >= 100L)
    }

    @Test
    fun parentheticalContentGetsSlowedForComprehension() {
        val config = RsvpConfig(
            tempoMsPerWord = 120L,
            parentheticalMultiplier = 1.25,
            parenthesesPauseMs = 0L,
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
            enablePhraseChunking = false
        )

        val framesWithParens = engine.generateFrames(
            tokens = listOf(
                Token(text = "He", type = TokenType.WORD, frequencyScore = 1.0),
                Token(text = "(", type = TokenType.PUNCTUATION),
                Token(text = "really", type = TokenType.WORD, syllableCount = 2, frequencyScore = 0.7, complexityMultiplier = 1.1),
                Token(text = ")", type = TokenType.PUNCTUATION),
                Token(text = "left", type = TokenType.WORD, frequencyScore = 0.8)
            ),
            startIndex = 0,
            config = config
        )

        val reallyUnit = framesWithParens.firstOrNull { it.tokens.any { t -> t.type == TokenType.WORD && t.text == "really" } }
            ?: error("Expected a unit containing 'really'")

        val framesPlain = engine.generateFrames(
            tokens = listOf(Token(text = "really", type = TokenType.WORD, syllableCount = 2, frequencyScore = 0.7, complexityMultiplier = 1.1)),
            startIndex = 0,
            config = config
        )

        assertTrue(reallyUnit.durationMs > framesPlain.first().durationMs)
    }

    @Test
    fun rhythmClampsAbruptSpeedupsAfterSlowUnits() {
        val config = RsvpConfig(
            tempoMsPerWord = 120L,
            smoothingAlpha = 1.0,
            maxSpeedupFactor = 1.1,
            maxSlowdownFactor = 10.0,
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
            enablePhraseChunking = false
        )

        val frames = engine.generateFrames(
            tokens = listOf(
                Token(text = "tiny", type = TokenType.WORD, frequencyScore = 1.0),
                Token(text = "Supercalifragilisticexpialidocious", type = TokenType.WORD, syllableCount = 8, frequencyScore = 0.15, complexityMultiplier = 1.6),
                Token(text = "tiny", type = TokenType.WORD, frequencyScore = 1.0)
            ),
            startIndex = 0,
            config = config
        )

        assertTrue(frames.size >= 3)
        val slow = frames[1].durationMs.toDouble()
        val next = frames[2].durationMs.toDouble()
        assertTrue("Expected clamp: next($next) >= slow/${
            config.maxSpeedupFactor
        }", next >= slow / config.maxSpeedupFactor)
    }
}
