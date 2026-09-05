package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.RsvpResumeCursor
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import com.kairo.reader.core.rsvp.segmentation.RsvpDpSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpScoredSegmentationTest {
    private val engine = ComprehensionRsvpEngine()
    private val stableConfig =
        RsvpConfig(
            tempoMsPerWord = 180L,
            enablePhraseChunking = true,
            maxWordsPerUnit = 3,
            maxCharsPerUnit = 24,
            maxChunkLength = 32,
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
            smoothingAlpha = 1.0,
            maxSpeedupFactor = 1000.0,
            maxSlowdownFactor = 1000.0,
        )
    private val scoredOptions =
        RsvpGenerationOptions(
            languagePolicy = RsvpLanguagePolicy.ENGLISH,
            segmentationStrategy = RsvpSegmentationStrategy.SCORED_DP_V2,
        )

    @Test
    fun defaultOverloadMatchesExplicitLegacyFrames() {
        val tokens = listOf(word("in"), word("the"), word("quiet"), word("house"))

        val defaultFrames = engine.generateFrames(tokens, 0, stableConfig)
        val explicitLegacy =
            engine.generateFrames(tokens, 0, stableConfig, RsvpGenerationOptions.LEGACY)

        assertEquals(defaultFrames, explicitLegacy)
    }

    @Test
    fun scoredDpKeepsNaturalLeadPhraseSeparateFromDifficultWord() {
        val tokens =
            listOf(
                word("in"),
                word("the"),
                word("puzzle", frequency = 0.0, complexity = 1.8, syllables = 3),
            )

        val legacy = engine.generateFrames(tokens, 0, stableConfig)
        val scored = engine.generateFrames(tokens, 0, stableConfig, scoredOptions)

        assertEquals(listOf("in", "the", "puzzle"), legacy.first().words())
        assertEquals(listOf("in", "the"), scored.first().words())
        assertEquals(listOf("puzzle"), scored[1].words())
    }

    @Test
    fun scoredGoldenUsesStableNaturalPhraseSequence() {
        val tokens =
            listOf(
                word("in"),
                word("the"),
                word("puzzle", frequency = 0.0, complexity = 1.8, syllables = 3),
                word("on"),
                word("the"),
                word("table"),
            )

        val frames = engine.generateFrames(tokens, 0, stableConfig, scoredOptions)

        assertEquals(
            listOf(listOf("in", "the"), listOf("puzzle"), listOf("on", "the", "table")),
            frames.map { frame -> frame.words() },
        )
    }

    @Test
    fun artificialSixWordHorizonDoesNotChangeFirstDecision() {
        val sixWords = List(6) { index -> word("the$index") }
        val sevenWords = sixWords + word("the6")

        val sixDecision =
            RsvpDpSegmenter.selectWordCount(sixWords.expanded(), 0, stableConfig)
        val sevenDecision =
            RsvpDpSegmenter.selectWordCount(sevenWords.expanded(), 0, stableConfig)

        assertEquals(sixDecision.selectedWordCount, sevenDecision.selectedWordCount)
        assertEquals(sixDecision.pathScore, sevenDecision.pathScore)
    }

    @Test
    fun equalScoresPreferTheShorterFirstFrame() {
        assertTrue(RsvpDpSegmenter.preferCandidate(100, 1, 100, 2))
        assertFalse(RsvpDpSegmenter.preferCandidate(100, 2, 100, 1))
    }

    @Test
    fun disabledPhraseChunkingAllowsOnlySingleWordFrames() {
        val frames =
            engine.generateFrames(
                tokens = listOf(word("in"), word("the"), word("house")),
                startIndex = 0,
                config = stableConfig.copy(enablePhraseChunking = false),
                options = scoredOptions,
            )

        assertEquals(listOf(1, 1, 1), frames.map { it.words().size })
    }

    @Test
    fun persistedWidthsAboveThreeStayOnLegacyPath() {
        val config = stableConfig.copy(maxWordsPerUnit = 4, maxCharsPerUnit = 40)
        val tokens = listOf(word("in"), word("the"), word("quiet"), word("old"), word("house"))

        val legacy = engine.generateFrames(tokens, 0, config, RsvpGenerationOptions.LEGACY)
        val explicitScored = engine.generateFrames(tokens, 0, config, scoredOptions)

        assertEquals(legacy, explicitScored)
    }

    @Test
    fun segmentationIsIndependentOfTempo() {
        val tokens =
            listOf(
                word("in"),
                word("the"),
                word("puzzle", frequency = 0.0, complexity = 1.8, syllables = 3),
                word("on"),
                word("the"),
                word("table"),
            )
        val slow = engine.generateFrames(tokens, 0, stableConfig.copy(tempoMsPerWord = 240L), scoredOptions)
        val fast = engine.generateFrames(tokens, 0, stableConfig.copy(tempoMsPerWord = 80L), scoredOptions)

        assertEquals(
            slow.map { frame -> frame.words() },
            fast.map { frame -> frame.words() },
        )
    }

    @Test
    fun subwordsAndOverLimitFirstWordsAlwaysHaveSingleWordFallbacks() {
        val subwordTokens =
            listOf(
                word("frag", subword = true),
                word("ment", subword = true),
                word("in"),
            )
        val subwordFrames = engine.generateFrames(subwordTokens, 0, stableConfig, scoredOptions)
        val longFirstFrames =
            engine.generateFrames(
                listOf(word("lengthy"), word("in")),
                0,
                stableConfig.copy(maxCharsPerUnit = 3),
                scoredOptions,
            )

        assertEquals(listOf("frag"), subwordFrames.first().words())
        assertEquals(listOf("ment"), subwordFrames[1].words())
        assertEquals(listOf("lengthy"), longFirstFrames.first().words())
    }

    @Test
    fun scoredPunctuationAndExactResumeCoordinatesStayAuthoritative() {
        val tokens = listOf(word("in"), word("the"), punctuation(","), word("house"))

        val frames = engine.generateFrames(tokens, 0, stableConfig, scoredOptions)
        val resumed = engine.generateFrames(tokens, 1, stableConfig, scoredOptions)

        assertEquals(listOf("in", "the", ","), frames.first().tokens.map(Token::text))
        assertEquals(0, RsvpResumeCursor.characterOffset(frames.first().resumeCursor))
        assertEquals(0, frames.first().displayOriginalStartIndex)
        assertEquals(3, frames.first().displayOriginalEndExclusive)
        assertEquals(3, frames.first().nextOriginalTokenIndex)
        assertEquals(listOf("the", ","), resumed.first().tokens.map(Token::text))
        assertEquals(1, resumed.first().originalTokenIndex)
        assertEquals(0, RsvpResumeCursor.characterOffset(resumed.first().resumeCursor))
        assertEquals(3, resumed.first().nextOriginalTokenIndex)
    }

    @Test
    fun nonEnglishScoringUsesConservativeTwoWordPolicy() {
        val tokens = listOf(word("le"), word("chat"), word("tranquillement"))
        val nonEnglishOptions =
            scoredOptions.copy(languagePolicy = RsvpLanguagePolicy.DEFAULT_NON_ENGLISH)

        val frames =
            engine.generateFrames(
                tokens,
                0,
                stableConfig.copy(maxWordsPerUnit = 2),
                nonEnglishOptions,
            )

        assertEquals(listOf("le", "chat"), frames.first().words())
        assertEquals(listOf("tranquillement"), frames[1].words())
    }

    private fun word(
        text: String,
        frequency: Double = 1.0,
        complexity: Double = 1.0,
        syllables: Int = 1,
        subword: Boolean = false,
    ): Token =
        Token(
            text = text,
            type = TokenType.WORD,
            frequencyScore = frequency,
            complexityMultiplier = complexity,
            syllableCount = syllables,
            isSubwordChunk = subword,
        )

    private fun punctuation(text: String): Token = Token(text = text, type = TokenType.PUNCTUATION)

    private fun RsvpFrame.words(): List<String> =
        tokens.filter { it.type == TokenType.WORD }.map(Token::text)

    private fun List<Token>.expanded(): List<ExpandedToken> =
        mapIndexed { index, token ->
            ExpandedToken(
                token = token,
                originalIndex = index,
                expandedIndex = index,
                sourceCharacterStart = 0,
                sourceCharacterEndExclusive = token.text.length,
            )
        }
}
