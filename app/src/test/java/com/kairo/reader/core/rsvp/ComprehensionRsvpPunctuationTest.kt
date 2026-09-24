package com.kairo.reader.core.rsvp

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComprehensionRsvpPunctuationTest : ComprehensionRsvpTestBase() {
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
    fun singleWordFramesAreNotCompressedByFocalStress() {
        val config =
            punctuationConfig.copy(
                useAdaptiveTiming = false,
                useClausePausing = false,
                useProsodyPacing = false,
                useFocalStress = true,
            )

        val withFocal = engine.generateFrames(listOf(w("steady")), 0, config).first().durationMs
        val withoutFocal =
            engine
                .generateFrames(listOf(w("steady")), 0, config.copy(useFocalStress = false))
                .first()
                .durationMs

        assertEquals(
            "A single visible word is already the focal word and should not be shortened",
            withoutFocal,
            withFocal,
        )
    }

    @Test
    fun steadyTempoDoesNotStackEasyWordCompressionIntoFastBursts() {
        val config =
            punctuationConfig.copy(
                tempoMsPerWord = 150L,
                useAdaptiveTiming = false,
                useClausePausing = false,
                useProsodyPacing = false,
                useFocalStress = true,
            )

        val frames = engine.generateFrames(listOf(w("calm"), w("reading")), 0, config)

        assertTrue(frames.size >= 2)
        assertTrue(
            "Steady easy-word frames should remain close to the selected 150ms tempo: $frames",
            frames.take(2).all { it.durationMs >= 140L },
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
        val diff = abs(requireNotNull(abbrevEnd[0].punctuationHoldMs) - requireNotNull(normalEnd[0].punctuationHoldMs))
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
    fun periodPauseCanBeTunedSeparatelyFromOtherSentenceEndMarks() {
        val config =
            stableConfig.copy(
                sentenceEndPauseMs = 120L,
                periodPauseMs = 280L,
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
            )

        val period = engine.generateFrames(listOf(w("Hello"), p(".")), 0, config).first().durationMs
        val exclamation = engine.generateFrames(listOf(w("Hello"), p("!")), 0, config).first().durationMs

        assertTrue(
            "Expected period pause to be longer than generic sentence-end punctuation",
            period > exclamation + 100L,
        )
    }

    @Test
    fun multilingualSentenceMarksAddSentencePause() {
        val config =
            punctuationConfig.copy(
                sentenceEndPauseMs = 240L,
                periodPauseMs = 0L,
                usePunctuationLandingHold = false,
            )

        val plain =
            engine.generateFrames(
                tokens = listOf(w("待って")),
                startIndex = 0,
                config = config,
            )[0].durationMs
        val withCjkFullStop =
            engine.generateFrames(
                tokens = listOf(w("待って"), p("。"), w("次")),
                startIndex = 0,
                config = config,
            )[0].durationMs

        assertTrue(
            "Expected CJK sentence punctuation to add a visible sentence pause",
            withCjkFullStop > plain + 100L,
        )
    }

    @Test
    fun invalidPauseScaleValuesAreClampedBeforePlayback() {
        val frames =
            engine.generateFrames(
                tokens = listOf(w("Hello"), p("."), w("Next")),
                startIndex = 0,
                config =
                punctuationConfig.copy(
                    tempoMsPerWord = 0L,
                    minPauseScale = 1.5,
                    pauseScaleExponent = -2.0,
                ),
            )

        assertTrue(frames.isNotEmpty())
        assertTrue(frames.all { it.durationMs >= 40L })
    }

    @Test
    fun openingQuoteDoesNotAddExtraPause() {
        val config = punctuationConfig

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
        val config = punctuationConfig

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
    fun semicolonRestartsMoreGentlyThanPeriod() {
        val semicolonFrames = engine.generateFrames(
            tokens = listOf(w("Wait"), p(";"), w("then")),
            startIndex = 0,
            config = punctuationConfig,
        )
        val periodFrames = engine.generateFrames(
            tokens = listOf(w("Wait"), p("."), w("then")),
            startIndex = 0,
            config = punctuationConfig,
        )
        val plainFrames = engine.generateFrames(
            tokens = listOf(w("Wait"), w("then")),
            startIndex = 0,
            config = punctuationConfig,
        )

        assertTrue(semicolonFrames.size >= 2 && periodFrames.size >= 2 && plainFrames.size >= 2)
        assertTrue(
            "Expected semicolon to restart more than plain spacing",
            semicolonFrames[1].durationMs > plainFrames[1].durationMs + 5L,
        )
        assertTrue(
            "Expected semicolon to restart less than a full stop",
            semicolonFrames[1].durationMs < periodFrames[1].durationMs - 10L,
        )
    }

    @Test
    fun dashAndColonKeepAVisibleBreathInNonClauseContext() {
        val contourConfig =
            punctuationConfig.copy(
                commaPauseMs = 0L,
                periodPauseMs = 0L,
                sentenceEndPauseMs = 0L,
                semicolonPauseMs = 0L,
                colonPauseMs = 0L,
                dashPauseMs = 0L,
                usePunctuationLandingHold = false,
                useAdaptiveTiming = false,
                useClausePausing = false,
                useProsodyPacing = false,
            )

        val plain = engine.generateFrames(
            tokens = listOf(w("Wait"), w("again")),
            startIndex = 0,
            config = contourConfig,
        )[0].durationMs
        val comma = engine.generateFrames(
            tokens = listOf(w("Wait"), p(","), w("again")),
            startIndex = 0,
            config = contourConfig,
        )[0].durationMs
        val colon = engine.generateFrames(
            tokens = listOf(w("Wait"), p(":"), w("again")),
            startIndex = 0,
            config = contourConfig,
        )[0].durationMs
        val dash = engine.generateFrames(
            tokens = listOf(w("Wait"), p("—"), w("again")),
            startIndex = 0,
            config = contourConfig,
        )[0].durationMs

        assertTrue("Expected plain word baseline", plain > 0L)
        assertTrue("Expected comma to stay above plain", comma > plain)
        assertTrue("Expected colon to stay above comma", colon > comma)
        assertTrue("Expected em dash to stay above colon", dash > colon)
    }

    @Test
    fun clauseLeadingCommaAddsSoftRestartToNextWord() {
        val commaFrames = engine.generateFrames(
            tokens = listOf(w("Wait"), p(","), w("because")),
            startIndex = 0,
            config = punctuationConfig,
        )
        val plainFrames = engine.generateFrames(
            tokens = listOf(w("Wait"), w("because")),
            startIndex = 0,
            config = punctuationConfig,
        )

        assertTrue(commaFrames.size >= 2 && plainFrames.size >= 2)
        assertTrue(
            "Expected clause-leading comma to add a soft restart",
            commaFrames[1].durationMs > plainFrames[1].durationMs + 5L,
        )
    }

    @Test
    fun ellipsisPauseGetsStrongerBeforeSentenceLikeRestart() {
        val sentenceLikeFrames = engine.generateFrames(
            tokens = listOf(w("Wait"), p("\u2026"), w("Now")),
            startIndex = 0,
            config = punctuationConfig,
        )
        val inlineFrames = engine.generateFrames(
            tokens = listOf(w("Wait"), p("\u2026"), w("and")),
            startIndex = 0,
            config = punctuationConfig,
        )

        assertTrue(sentenceLikeFrames.isNotEmpty() && inlineFrames.isNotEmpty())
        assertTrue(
            "Expected ellipsis before a sentence-like restart to linger longer",
            sentenceLikeFrames[0].durationMs > inlineFrames[0].durationMs + 20L,
        )
        assertTrue(
            "Expected ellipsis to still be softer than a full stop",
            sentenceLikeFrames[0].durationMs <
                engine.generateFrames(
                    tokens = listOf(w("Wait"), p("."), w("Now")),
                    startIndex = 0,
                    config = punctuationConfig,
                )[0].durationMs,
        )
    }

    @Test
    fun negativeSessionRampValuesDoNotShortenFrames() {
        val baseline =
            engine.generateFrames(
                tokens = listOf(w("steady")),
                startIndex = 0,
                config = stableConfig,
            ).first().durationMs
        val withBadPersistedValues =
            engine.generateFrames(
                tokens = listOf(w("steady")),
                startIndex = 0,
                config =
                stableConfig.copy(
                    startDelayMs = -500L,
                    endDelayMs = -500L,
                    rampUpFrames = -3,
                    rampDownFrames = -3,
                ),
            ).first().durationMs

        assertEquals(baseline, withBadPersistedValues)
    }
}
