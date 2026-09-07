package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.analysis.RsvpThoughtPlan
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import com.kairo.reader.core.rsvp.timing.coordinateRsvpExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpThoughtFlowTest : ComprehensionRsvpTestBase() {
    @Test
    fun protectedModalRetainsExtraTimeAfterStrongRhythmSmoothing() {
        val tokens = listOf(
            w("She"), w("said"), w("he"), w("could"), w("leave"), p("."),
            w("She"), w("said"), w("he"), w("should"), w("leave"), p(".")
        )
        val config = stableConfig.copy(smoothingAlpha = 0.1)
        val english = engine.generateFrames(tokens, 0, config, RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH))
        val unknown = engine.generateFrames(tokens, 0, config)
        val emphasized = english.first { it.originalTokenIndex == 9 }
        val neutral = unknown.first { it.originalTokenIndex == 9 }
        assertTrue(emphasized.durationMs > neutral.durationMs)
    }

    @Test
    fun contrastProtectsShortModalWordsAndNegationWithExplicitEnglishPolicy() {
        val tokens = listOf(
            w("She"), w("said"), w("he"), w("could"), w("leave"), p("."),
            w("She"), w("never"), w("said"), w("he"), w("should"), p(".")
        )
        val cues = plan(tokens)
        assertTrue(cues.getValue(7).protectedEmphasis)
        assertTrue(cues.getValue(10).protectedEmphasis)
        assertFalse(cues.getValue(3).protectedEmphasis)
        val unknown = plan(tokens, language = RsvpLanguagePolicy.UNKNOWN)
        assertFalse(unknown.getValue(7).protectedEmphasis)
        assertFalse(unknown.getValue(10).protectedEmphasis)
    }

    @Test
    fun repeatedContrastWordKeepsProtection() {
        val cues = plan(listOf(w("ready"), p(","), w("but"), w("ready"), p(".")))
        assertTrue(cues.getValue(3).protectedEmphasis)
    }

    @Test
    fun integrationTimeBelongsOnlyToTheDensePhraseLandingAndIsCapped() {
        val easy = listOf(w("we"), w("are"), w("ready"), p("."))
        val dense = List(12) { w("concept$it").copy(frequencyScore = 0.1, complexityMultiplier = 1.8) } + p(".")
        assertEquals(0.0, plan(easy).values.sumOf { it.integrationHoldMs }, 0.0)
        val cues = plan(dense)
        assertTrue(cues.getValue(11).integrationHoldMs > 0.0)
        assertEquals(1, cues.values.count { it.integrationHoldMs > 0.0 })
        assertTrue(cues.values.sumOf { it.integrationHoldMs } <= stableConfig.adaptiveDifficultyMaxHoldMs)
        assertEquals(0.0, plan(dense, stableConfig.copy(useAdaptiveTiming = false)).values.sumOf { it.integrationHoldMs }, 0.0)
    }

    @Test
    fun abbreviationsAndDecimalsStayInsideTheirThought() {
        val tokens = listOf(w("Dr"), p("."), w("Smith"), w("paid"), w("3"), p("."), w("14"), p("."))
        val cues = plan(tokens)
        assertEquals(1, cues.values.map { it.startTokenIndex }.distinct().size)
        assertTrue(cues.getValue(6).isLastWord)
        assertFalse(cues.getValue(4).isLastWord)
    }

    @Test
    fun phraseMetadataAndSourceCoverageSurviveGroupingAndSplitWords() {
        val tokens = listOf(w("in"), w("the"), w("extraordinary"), p(","), w("but"), w("not"), w("today"), p("."))
        val config = stableConfig.copy(enablePhraseChunking = true, maxWordsPerUnit = 2, maxChunkLength = 6)
        val options = RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH, RsvpSegmentationStrategy.SCORED_DP_V2)
        val frames = engine.generateFrames(tokens, 0, config, options)
        val words = frames.filter { it.tokens.any { token -> token.type == TokenType.WORD } }
        assertEquals(
            tokens.indices.filter { tokens[it].type == TokenType.WORD },
            words.flatMap { frame ->
                (frame.displayOriginalStartIndex until frame.displayOriginalEndExclusive).filter { tokens[it].type == TokenType.WORD }
            }.distinct()
        )
        assertEquals(2, words.count { it.endsPhrase })
        assertTrue(words.all { it.phraseStartTokenIndex != null && it.phraseEndTokenIndexExclusive != null })
        val chunks = words.filter { it.originalTokenIndex == 2 }
        assertTrue(chunks.size > 1)
        assertEquals(chunks.size, chunks.map { it.resumeCursor }.distinct().size)
    }

    @Test
    fun simultaneousExpressionCuesAreBoundedAndNeutralIsIdentity() {
        assertEquals(1.0, coordinateRsvpExpression(1.0, 1.0), 0.0)
        assertTrue(coordinateRsvpExpression(1.3, 1.3, 1.3, 1.3) < 1.3 * 1.3)
        assertTrue(coordinateRsvpExpression(0.8, 0.8, 0.8) >= 0.8)
    }

    private fun plan(
        tokens: List<Token>,
        config: RsvpConfig = stableConfig,
        language: RsvpLanguagePolicy = RsvpLanguagePolicy.ENGLISH,
    ) = RsvpThoughtPlan.analyze(
        tokens.mapIndexed { index, token ->
            ExpandedToken(token, index, index, 0, token.text.length)
        },
        config,
        language
    )
}
