package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.defaultConfig
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTier
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTimingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpPunctuationTimingPolicyTest {
    @Test
    fun periodResolvesToSentenceEnd() {
        val tier =
            RsvpPunctuationTimingPolicy.resolveTier(
                token = punctuation("."),
                prevWord = word("Hello"),
                nextToken = word("there"),
            )

        assertEquals(RsvpPunctuationTier.SENTENCE_END, tier)
    }

    @Test
    fun commaResolvesToClauseBreak() {
        val tier =
            RsvpPunctuationTimingPolicy.resolveTier(
                token = punctuation(","),
                prevWord = word("Wait"),
                nextToken = word("again"),
            )

        assertEquals(RsvpPunctuationTier.CLAUSE_BREAK, tier)
    }

    @Test
    fun quotesResolveToSoftSeparator() {
        val tier =
            RsvpPunctuationTimingPolicy.resolveTier(
                token = punctuation("\""),
                prevWord = word("said"),
                nextToken = word("hello"),
            )

        assertEquals(RsvpPunctuationTier.SOFT_SEPARATOR, tier)
    }

    @Test
    fun decimalPointsResolveToNone() {
        val tier =
            RsvpPunctuationTimingPolicy.resolveTier(
                token = punctuation("."),
                prevWord = word("3"),
                nextToken = word("14"),
            )

        assertEquals(RsvpPunctuationTier.NONE, tier)
    }

    @Test
    fun multilingualSentenceMarksResolveToSentenceEnd() {
        val cjkTier =
            RsvpPunctuationTimingPolicy.resolveTier(
                token = punctuation("。"),
                prevWord = word("待って"),
                nextToken = word("次"),
            )
        val arabicTier =
            RsvpPunctuationTimingPolicy.resolveTier(
                token = punctuation("؟"),
                prevWord = word("مرحبا"),
                nextToken = word("التالي"),
            )

        assertEquals(RsvpPunctuationTier.SENTENCE_END, cjkTier)
        assertEquals(RsvpPunctuationTier.SENTENCE_END, arabicTier)
    }

    @Test
    fun multilingualSentenceMarksReceiveSentencePauseTiming() {
        val timing =
            RsvpPunctuationTimingPolicy.resolvePauseTiming(
                token = punctuation("。"),
                prevWord = word("待って"),
                nextToken = word("次"),
                config =
                RsvpConfig(
                    sentenceEndPauseMs = 220L,
                    minPauseScale = 0.6,
                ),
            )

        assertTrue(timing.baseMs > 0.0)
        assertTrue(timing.floorMs > 0.0)
    }

    @Test
    fun punctuationBudgetAndFloorAreIndependentOfWordSpeed() {
        val slow =
            RsvpPunctuationTimingPolicy.resolvePauseTiming(
                token = punctuation(","),
                prevWord = word("Wait"),
                nextToken = word("again"),
                config =
                RsvpConfig(
                    tempoMsPerWord = 120L,
                    commaPauseMs = 100L,
                ),
            )
        val fast =
            RsvpPunctuationTimingPolicy.resolvePauseTiming(
                token = punctuation(","),
                prevWord = word("Wait"),
                nextToken = word("again"),
                config =
                RsvpConfig(
                    tempoMsPerWord = 55L,
                    commaPauseMs = 100L,
                ),
            )

        assertEquals(slow.baseMs, fast.baseMs, 0.0)
        assertEquals(slow.floorMs, fast.floorMs, 0.0)
    }

    @Test
    fun balancedSteadyCommaKeepsAnAudibleBreath() {
        val timing =
            RsvpPunctuationTimingPolicy.resolvePauseTiming(
                token = punctuation(","),
                prevWord = word("Wait"),
                nextToken = word("again"),
                config = RsvpProfile.BALANCED.defaultConfig().copy(tempoMsPerWord = 150L),
            )

        assertTrue("Expected a substantial comma breath", timing.baseMs >= 180.0)
        assertTrue("Expected the comma floor to remain readable", timing.floorMs >= 150.0)
    }

    @Test
    fun clauseLeadingCommaBreathesLongerThanListComma() {
        val config = RsvpConfig(tempoMsPerWord = 150L, commaPauseMs = 160L)
        val listComma =
            RsvpPunctuationTimingPolicy.resolvePauseTiming(
                token = punctuation(","),
                prevWord = word("red"),
                nextToken = word("blue"),
                config = config,
            )
        val clauseComma =
            RsvpPunctuationTimingPolicy.resolvePauseTiming(
                token = punctuation(","),
                prevWord = word("wait"),
                nextToken = word("because"),
                config = config,
            )

        assertTrue(clauseComma.baseMs > listComma.baseMs * 1.1)
        assertTrue(clauseComma.floorMs > listComma.floorMs * 1.1)
    }

    @Test
    fun lowercaseRestartKeepsMoreThanACommaAfterFullStop() {
        val config =
            RsvpConfig(
                tempoMsPerWord = 150L,
                commaPauseMs = 150L,
                periodPauseMs = 330L,
            )
        val comma =
            RsvpPunctuationTimingPolicy.resolvePauseTiming(
                token = punctuation(","),
                prevWord = word("Wait"),
                nextToken = word("again"),
                config = config,
            )
        val period =
            RsvpPunctuationTimingPolicy.resolvePauseTiming(
                token = punctuation("."),
                prevWord = word("Wait"),
                nextToken = word("again"),
                config = config,
            )

        assertTrue("A real full stop must not collapse below a comma", period.baseMs > comma.baseMs)
    }

    @Test
    fun multilingualClauseMarksKeepTheirNaturalHierarchy() {
        val config =
            RsvpConfig(
                tempoMsPerWord = 150L,
                commaPauseMs = 150L,
                semicolonPauseMs = 260L,
                colonPauseMs = 240L,
            )
        val comma = timingFor("、", config)
        val semicolon = timingFor("؛", config)
        val colon = timingFor("：", config)
        val middleDot = timingFor("・", config)

        assertTrue(comma.baseMs > middleDot.baseMs * 2.0)
        assertTrue(semicolon.baseMs > comma.baseMs)
        assertTrue(colon.baseMs > comma.baseMs)
    }

    @Test
    fun questionSettlesLongerThanExclamation() {
        val config = RsvpConfig(tempoMsPerWord = 150L, sentenceEndPauseMs = 350L)
        val question = timingFor("?", config)
        val exclamation = timingFor("!", config)

        assertTrue(question.baseMs > exclamation.baseMs)
    }

    @Test
    fun ellipsisTimingSitsBetweenCommaAndFullStop() {
        val config =
            RsvpConfig(
                commaPauseMs = 120L,
                periodPauseMs = 300L,
                minPauseScale = 0.8,
            )
        val comma =
            RsvpPunctuationTimingPolicy.resolvePauseTiming(
                token = punctuation(","),
                prevWord = word("Wait"),
                nextToken = word("now"),
                config = config,
            )
        val ellipsis =
            RsvpPunctuationTimingPolicy.resolvePauseTiming(
                token = punctuation("\u2026"),
                prevWord = word("Wait"),
                nextToken = word("Now"),
                config = config,
            )
        val period =
            RsvpPunctuationTimingPolicy.resolvePauseTiming(
                token = punctuation("."),
                prevWord = word("Wait"),
                nextToken = word("Now"),
                config = config,
            )

        assertTrue("Expected ellipsis to linger more than a comma", ellipsis.baseMs > comma.baseMs)
        assertTrue("Expected ellipsis to stay softer than a full stop", ellipsis.baseMs < period.baseMs)
    }

    @Test
    fun sentenceEndContourDampensTailLiftWhenLandingIsActive() {
        val contour =
            RsvpPunctuationTimingPolicy.resolveBoundaryContour(
                token = punctuation("."),
                prevWord = word("Hello"),
                nextToken = word("Now"),
            )

        assertTrue(contour.landingHoldWeight > 0.0)
        assertTrue(contour.tailLiftWeight > 0.0)
        assertTrue(
            "Expected landing settle to temper the tail lift a little",
            contour.tailLiftWeight < (1.26 * 0.92),
        )
    }

    private fun word(text: String) = Token(text = text, type = TokenType.WORD)

    private fun punctuation(text: String) = Token(text = text, type = TokenType.PUNCTUATION)

    private fun timingFor(
        mark: String,
        config: RsvpConfig,
    ) =
        RsvpPunctuationTimingPolicy.resolvePauseTiming(
            token = punctuation(mark),
            prevWord = word("Wait"),
            nextToken = word("again"),
            config = config,
        )
}
