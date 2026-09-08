package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpGenerationOptionsTest {
    @Test
    fun languageResolutionHasNoBuildOrWidthGate() {
        val fixtures = mapOf(
            "en-GB" to RsvpLanguagePolicy.ENGLISH,
            "eng" to RsvpLanguagePolicy.ENGLISH,
            "fr" to RsvpLanguagePolicy.DEFAULT_NON_ENGLISH,
            "ja" to RsvpLanguagePolicy.CJK,
            "ar" to RsvpLanguagePolicy.RTL,
            null to RsvpLanguagePolicy.UNKNOWN,
            "und" to RsvpLanguagePolicy.UNKNOWN,
        )
        fixtures.forEach { (tag, policy) ->
            assertEquals(policy, RsvpGenerationOptions.fromLanguageTag(tag).languagePolicy)
        }
    }

    @Test
    fun everyLanguageAndPersistedWidthUsesBoundedScoredUnitsWithoutLosingText() {
        val tokens = listOf("a", "b", "c", "d", "e", "f", "g").map { Token(it, TokenType.WORD) }
        val engine = ComprehensionRsvpEngine()
        RsvpLanguagePolicy.entries.forEach { policy ->
            listOf(1, 2, 3, 4, 6, Int.MAX_VALUE).forEach { width ->
                val config = RsvpConfig(enablePhraseChunking = true, maxWordsPerUnit = width, maxCharsPerUnit = 30)
                val frames = engine.generateFrames(tokens, 0, config, RsvpGenerationOptions(policy))
                assertEquals(tokens.map(Token::text), frames.flatMap { it.tokens }.map(Token::text))
                assertTrue(frames.all { it.tokens.size in 1..minOf(width, 6) })
                assertTrue(frames.all { it.durationMs > 0 })
                assertTrue(frames.all { it.phraseStartTokenIndex != null })
            }
        }
    }

    @Test
    fun unknownLanguageCanGroupWithoutEnablingEnglishRules() {
        val tokens = listOf("le", "chat").map { Token(it, TokenType.WORD) }
        val config = RsvpConfig(enablePhraseChunking = true, maxWordsPerUnit = 2)
        val frames = ComprehensionRsvpEngine().generateFrames(tokens, 0, config)
        assertEquals(listOf("le", "chat"), frames.first().tokens.map(Token::text))
        assertEquals(RsvpLanguagePolicy.UNKNOWN, RsvpGenerationOptions.DEFAULT.languagePolicy)
    }

    @Test
    fun everyPaceEstimateUsesTheLanguageOfItsActualSample() {
        RsvpLanguagePolicy.entries.forEach { policy ->
            val sample = RsvpGenerationOptions(policy).asPaceEstimationOptions().asGenerationOptions()
            assertEquals(RsvpLanguagePolicy.ENGLISH, sample.languagePolicy)
        }
    }
}
