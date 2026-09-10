package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.defaultConfig
import com.kairo.reader.core.rsvp.timing.sentenceWrapUpFactor
import com.kairo.reader.core.tokenization.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpNaturalPunctuationTest {
    private val engine = ComprehensionRsvpEngine()
    private val english = RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH)

    @Test
    fun fullStopHasTheSameHoldBeforeUppercaseAndLowercaseRestarts() {
        for (tempo in listOf(55L, 150L, 250L)) {
            for (profile in RsvpProfile.entries) {
                val config = profile.defaultConfig().copy(tempoMsPerWord = tempo)
                val upper = homeFrame("We went home. Then we rested.", config)
                val lower = homeFrame("We went home. then we rested.", config)
                assertEquals("$profile at $tempo ms", upper.punctuationHoldMs, lower.punctuationHoldMs)
                assertEquals("Capitalisation alone must not rush the ending", upper.durationMs, lower.durationMs)
            }
        }
    }

    @Test
    fun everyPresetKeepsAConsistentPunctuationHierarchyAtMultipleSpeeds() {
        for (tempo in listOf(55L, 150L, 250L)) {
            for (profile in RsvpProfile.entries) {
                val config = profile.defaultConfig().copy(tempoMsPerWord = tempo)
                fun hold(mark: String) = requireNotNull(homeFrame("We went home$mark Then we rested.", config).punctuationHoldMs)
                val comma = hold(",")
                val semicolon = hold(";")
                val colon = hold(":")
                val period = hold(".")
                assertTrue("$profile comma must breathe", comma >= config.commaPauseMs * config.minPauseScale)
                assertTrue("$profile semicolon must exceed comma", semicolon > comma)
                assertTrue("$profile colon must exceed comma", colon > comma)
                assertTrue("$profile full stop must exceed clause breaks", period > maxOf(semicolon, colon))
                assertTrue("$profile sentence ending must settle", hold("!") >= period)
            }
        }
    }

    @Test
    fun shortSentencesKeepTheirStopAndLongSentencesEarnMoreTime() {
        assertEquals(1.0, sentenceWrapUpFactor(1), 0.0)
        assertEquals(1.0, sentenceWrapUpFactor(6), 0.0)
        assertTrue(sentenceWrapUpFactor(22) > sentenceWrapUpFactor(6))
    }

    @Test
    fun liveSpeedChangesScaleReadingTimeSeparatelyFromPunctuation() {
        val config = RsvpProfile.BALANCED.defaultConfig()
        val frame = RsvpFrame(
            tokens = listOf(Token("home", TokenType.WORD), Token(".", TokenType.PUNCTUATION)),
            durationMs = 700L,
            punctuationHoldMs = 400L,
        )
        assertEquals(550L, scaledFrameDurationMs(frame, config, 75L, 0.5))
        assertEquals(700L, scaledFrameDurationMs(frame, config, 150L, 1.0))
        assertEquals(1000L, scaledFrameDurationMs(frame, config, 300L, 2.0))
        assertEquals(
            (60_000.0 / 550).toInt(),
            RsvpEstimatedReadingPace.estimateChapterPreviewWpm(config, listOf(frame), 150L, 75L),
        )
    }

    @Test
    fun punctuationHoldBelongsToTheLastVisiblePartOfASplitWord() {
        val config = RsvpProfile.FOCUS.defaultConfig().copy(maxChunkLength = 4, blinkMode = BlinkMode.ADAPTIVE)
        val frames = frames("characteristically. Next we rested.", config)
        val parts = frames.filter { it.originalTokenIndex == 0 && !it.isWordSeparation }
        assertTrue(parts.size > 1)
        assertTrue(parts.dropLast(1).all { it.punctuationHoldMs == 0L })
        assertTrue(parts.last().tokens.any { it.text == "." })
        assertTrue(requireNotNull(parts.last().punctuationHoldMs) > 0L)
        assertTrue(frames.filter { it.isWordSeparation }.all { it.punctuationHoldMs == 0L })
        assertTrue(frames.all { it.durationMs >= (it.punctuationHoldMs ?: 0L) })
    }

    @Test
    fun numericSeparatorsAndTitleAbbreviationsDoNotBecomeFullStops() {
        val config = RsvpProfile.BALANCED.defaultConfig()
        for (text in listOf("Dr. Alice arrived.", "3.14 is pi.", "1,000 items arrived.")) {
            val first = frames(text, config).first { it.tokens.any { token -> token.type == TokenType.WORD } }
            assertEquals("Numeric and title punctuation should flow: $text", 0L, first.punctuationHoldMs)
            val withoutPunctuation = first.copy(tokens = first.tokens.filter { it.type == TokenType.WORD })
            assertEquals(
                "Playback must use the same abbreviation decision as generation: $text",
                frameFloorMs(withoutPunctuation, config, 80L),
                frameFloorMs(first, config, 80L),
            )
        }
    }

    @Test
    fun shortEmphaticAnswersAreNotMistakenForAbbreviations() {
        val config = RsvpProfile.BALANCED.defaultConfig()
        for (text in listOf("YES. Then we rested.", "NO. Then we rested.", "No. then we rested.")) {
            val first = frames(text, config).first()
            assertTrue("An answer must retain its stop: $text", requireNotNull(first.punctuationHoldMs) >= config.periodPauseMs)
        }
        assertEquals(0L, frames("No. 7 arrived.", config).first().punctuationHoldMs)
        assertEquals(0L, frames("J. Smith arrived.", config).first().punctuationHoldMs)
    }

    private fun homeFrame(text: String, config: RsvpConfig) =
        frames(text, config).first { frame -> frame.tokens.any { it.text == "home" } }

    private fun frames(text: String, config: RsvpConfig): List<RsvpFrame> {
        val tokens = Tokenizer().tokenize(Chapter(0, "Punctuation fixture", "", text))
        return engine.generateFrames(tokens, 0, config, english)
    }
}
