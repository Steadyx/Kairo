package com.example.kairo.core.rsvp

import com.example.kairo.core.model.BlinkMode
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComprehensionRsvpEngineHeuristicsTest {
    private val engine = ComprehensionRsvpEngine()

    private fun w(text: String) =
        Token(
            text = text,
            type = TokenType.WORD,
            frequencyScore = 1.0,
            complexityMultiplier = 1.0,
            syllableCount = 1
        )

    private fun p(text: String) = Token(text = text, type = TokenType.PUNCTUATION)

    private fun pageBreak() = Token(text = "\u000C", type = TokenType.PAGE_BREAK)

    private val stableConfig =
        RsvpConfig(
            tempoMsPerWord = 200L,
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
            smoothingAlpha = 1.0,
            maxSpeedupFactor = 1000.0,
            maxSlowdownFactor = 1000.0,
            enablePhraseChunking = false,
        )

    @Test
    fun abbreviationDotsDoNotCauseSentencePause() {
        val abbrevTokens = listOf(w("Dr"), p("."), w("Alice"))
        val normalTokens = listOf(w("Hello"), p("."), w("Alice"))

        val abbrevFrames = engine.generateFrames(abbrevTokens, 0, stableConfig)
        val normalFrames = engine.generateFrames(normalTokens, 0, stableConfig)

        assertTrue(abbrevFrames.isNotEmpty() && normalFrames.isNotEmpty())
        assertTrue(
            "Abbreviation dot should be shorter than sentence dot",
            abbrevFrames[0].durationMs < normalFrames[0].durationMs - 80,
        )
    }

    @Test
    fun abbreviationAtSentenceEndKeepsSentencePause() {
        val config =
            stableConfig.copy(
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
            )

        val abbrevEnd = engine.generateFrames(
            tokens = listOf(w("Dr"), p(".")),
            startIndex = 0,
            config = config
        )
        val normalEnd = engine.generateFrames(
            tokens = listOf(w("Hello"), p(".")),
            startIndex = 0,
            config = config
        )

        assertTrue(abbrevEnd.isNotEmpty() && normalEnd.isNotEmpty())
        val diff = abs(abbrevEnd[0].durationMs - normalEnd[0].durationMs)
        assertTrue("Expected abbreviation at sentence end to pause like a sentence end", diff <= 5L)
    }

    @Test
    fun decimalPointsDoNotPause() {
        val decimalTokens = listOf(w("3"), p("."), w("14"), w("pi"))
        val sentenceTokens = listOf(w("3"), p("."), w("pi"))

        val decimalFrames = engine.generateFrames(decimalTokens, 0, stableConfig)
        val sentenceFrames = engine.generateFrames(sentenceTokens, 0, stableConfig)

        assertTrue(decimalFrames.isNotEmpty() && sentenceFrames.isNotEmpty())
        assertTrue(
            "Decimal point should not cause a sentence pause",
            decimalFrames[0].durationMs < sentenceFrames[0].durationMs - 80,
        )
    }

    @Test
    fun openingQuoteDoesNotAddExtraPause() {
        val config =
            stableConfig.copy(
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
            )

        val plain = engine.generateFrames(
            tokens = listOf(w("Hello")),
            startIndex = 0,
            config = config
        )[0].durationMs
        val quoted = engine.generateFrames(
            tokens = listOf(p("\""), w("Hello")),
            startIndex = 0,
            config = config
        )[0].durationMs

        assertTrue("Expected opening quote to avoid adding pause", abs(quoted - plain) <= 5L)
    }

    @Test
    fun closingQuoteDoesNotDoubleSentencePause() {
        val config =
            stableConfig.copy(
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
            )

        val base = engine.generateFrames(
            tokens = listOf(w("Hello"), p(".")),
            startIndex = 0,
            config = config
        )[0].durationMs
        val withQuote = engine.generateFrames(
            tokens = listOf(w("Hello"), p("."), p("\"")),
            startIndex = 0,
            config = config
        )[0].durationMs

        assertTrue("Expected closing quote not to add extra pause", abs(withQuote - base) <= 5L)
    }

    @Test
    fun thousandSeparatorsDoNotPause() {
        val numberTokens = listOf(w("1"), p(","), w("000"), w("items"))
        val commaTokens = listOf(w("Yes"), p(","), w("items"))

        val numberFrames = engine.generateFrames(numberTokens, 0, stableConfig)
        val commaFrames = engine.generateFrames(commaTokens, 0, stableConfig)

        assertTrue(numberFrames.isNotEmpty() && commaFrames.isNotEmpty())
        assertTrue(
            "Comma inside number should be shorter than a normal comma pause",
            numberFrames[0].durationMs < commaFrames[0].durationMs - 40,
        )
    }

    @Test
    fun hyphenatedWordsAddMicroPauseBetweenParts() {
        val config =
            stableConfig.copy(
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
            )

        val frames = engine.generateFrames(
            tokens = listOf(w("self-aware")),
            startIndex = 0,
            config = config
        )

        assertEquals(2, frames.size)
        val expectedFirst = config.tempoMsPerWord + (config.tempoMsPerWord / 4)
        assertTrue(
            "Expected first hyphen-part frame around ${expectedFirst}ms, got ${frames[0].durationMs}ms",
            frames[0].durationMs in (expectedFirst - 2)..(expectedFirst + 2),
        )
    }

    @Test
    fun signedNumericTokensAreNotSplit() {
        val tokens = listOf(w("-35c"))
        val frames = engine.generateFrames(tokens, 0, stableConfig)

        assertEquals(1, frames.size)
        val word =
            frames
                .first()
                .tokens
                .first { it.type == TokenType.WORD }
                .text
        assertEquals("-35c", word)
    }

    @Test
    fun pageBreakAddsMeaningfulPause() {
        val config = stableConfig.copy(tempoMsPerWord = 60L)

        val frames = engine.generateFrames(
            tokens = listOf(w("Hello"), pageBreak(), w("Next")),
            startIndex = 0,
            config = config
        )
        assertTrue("Expected at least 3 frames (word + break + word)", frames.size >= 3)

        val breakFrame = frames[1]
        assertTrue(
            "Expected break frame to contain no WORD tokens",
            breakFrame.tokens.none {
                it.type ==
                    TokenType.WORD
            }
        )
        assertTrue("Expected page break pause to be meaningful", breakFrame.durationMs >= 240L)
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
    fun sentenceStartGetsABoostAtHighSpeed() {
        val config = stableConfig.copy(tempoMsPerWord = 60L, sentenceEndPauseMs = 0L)

        val plainNext = engine.generateFrames(
            tokens = listOf(w("Hello"), w("Next")),
            startIndex = 0,
            config = config
        )[1].durationMs
        val boundaryNext =
            engine
                .generateFrames(
                    tokens = listOf(w("Hello"), p("."), w("Next")),
                    startIndex = 0,
                    config = config,
                )[1]
                .durationMs

        assertTrue(
            "Expected a sentence-start boost after a full stop",
            boundaryNext - plainNext >= 4L
        )
    }

    @Test
    fun clauseStartersGetExtraTimeAtHighSpeed() {
        val baseConfig =
            stableConfig.copy(
                tempoMsPerWord = 60L,
                startDelayMs = 0L,
                endDelayMs = 0L,
                rampUpFrames = 0,
                rampDownFrames = 0,
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
                sentenceEndPauseMs = 0L,
            )

        val withoutClause =
            engine
                .generateFrames(
                    tokens = listOf(w("because")),
                    startIndex = 0,
                    config = baseConfig.copy(useClausePausing = false)
                )
                .first()
                .durationMs
        val withClause =
            engine
                .generateFrames(
                    tokens = listOf(w("because")),
                    startIndex = 0,
                    config = baseConfig.copy(useClausePausing = true)
                )
                .first()
                .durationMs

        assertTrue(
            "Expected clause pacing to slow clause starters at high speed",
            withClause > withoutClause
        )
    }

    @Test
    fun blinkSeparationInsertsBlankFramesBetweenWords() {
        val config =
            stableConfig.copy(
                tempoMsPerWord = 80L,
                blinkMode = BlinkMode.SUBTLE,
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
                sentenceEndPauseMs = 0L,
                useClausePausing = false,
            )

        val tokens = listOf(w("test"), w("test"))

        val withoutBlink = engine.generateFrames(tokens, 0, config.copy(blinkMode = BlinkMode.OFF))
        val withBlink = engine.generateFrames(tokens, 0, config)

        assertEquals(2, withoutBlink.size)
        assertEquals(3, withBlink.size)
        assertTrue(withBlink[1].tokens.none { it.type == TokenType.WORD })
        assertTrue(withBlink[1].durationMs in 16L..22L)
        assertEquals(withoutBlink.sumOf { it.durationMs }, withBlink.sumOf { it.durationMs })
    }

    @Test
    fun structuralPauseAddsExtraTimeToBreakFrames() {
        val config =
            stableConfig.copy(
                tempoMsPerWord = 200L,
                paragraphPauseMs = 0L,
                startDelayMs = 0L,
                endDelayMs = 0L,
                rampUpFrames = 0,
                rampDownFrames = 0,
            )

        val tokens =
            listOf(
                w("Hello"),
                Token(text = "\n", type = TokenType.PARAGRAPH_BREAK, pauseAfterMs = 200L),
                w("Next"),
            )

        val frames = engine.generateFrames(tokens, 0, config)
        val breakFrame =
            frames.firstOrNull { it.tokens.none { t -> t.type == TokenType.WORD } }
                ?: error("Expected a break frame")

        assertTrue("Expected break frame to include extra pause", breakFrame.durationMs >= 340L)
    }

    @Test
    fun speakerTagsReadFasterWhenDialogueDetectionEnabled() {
        val baseConfig =
            stableConfig.copy(
                tempoMsPerWord = 200L,
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
                sentenceEndPauseMs = 0L,
                commaPauseMs = 0L,
                semicolonPauseMs = 0L,
                colonPauseMs = 0L,
                dashPauseMs = 0L,
                parenthesesPauseMs = 0L,
                quotePauseMs = 0L,
                paragraphPauseMs = 0L,
                useClausePausing = false,
                useDialogueDetection = false,
            )

        val tokens = listOf(w("he"), w("said"))
        val withoutDetection = engine.generateFrames(tokens, 0, baseConfig)
        val withDetection = engine.generateFrames(
            tokens,
            0,
            baseConfig.copy(useDialogueDetection = true)
        )

        assertTrue(withoutDetection.size >= 2 && withDetection.size >= 2)
        assertTrue(withDetection[0].durationMs < withoutDetection[0].durationMs)
        assertTrue(withDetection[1].durationMs < withoutDetection[1].durationMs)
    }
}
