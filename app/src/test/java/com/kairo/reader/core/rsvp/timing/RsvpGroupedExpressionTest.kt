package com.kairo.reader.core.rsvp.timing

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.ComprehensionRsvpEngine
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy
import com.kairo.reader.core.rsvp.analysis.multiWordPenalty
import com.kairo.reader.core.rsvp.engine.BoundaryBefore
import com.kairo.reader.core.rsvp.engine.ContextSnapshot
import com.kairo.reader.core.rsvp.engine.PhraseContour
import com.kairo.reader.core.rsvp.engine.RhythmState
import com.kairo.reader.core.rsvp.engine.RsvpWordExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpGroupedExpressionTest {
    private val config = RsvpConfig(
        tempoMsPerWord = 200L,
        minWordMs = 1L,
        difficultWordSupport = 0.0,
        useAdaptiveTiming = false,
        useClausePausing = false,
        useDialogueDetection = false,
        usePunctuationLandingHold = false,
        useFocalStress = false,
        startDelayMs = 0L,
        endDelayMs = 0L,
        rampUpFrames = 0,
        rampDownFrames = 0,
    )

    @Test
    fun anAccentOnTheSecondOrThirdWordIsNeitherLostNorAppliedToEveryWord() {
        val accent = RsvpWordExpression(anticipatoryLanding = 1.1)
        val singleDelta = duration(listOf(accent)) - duration(listOf(RsvpWordExpression()))
        assertTrue(singleDelta > 0)
        for (width in 2..3) {
            val neutral = List(width) { RsvpWordExpression() }
            val baseline = duration(neutral)
            val first = duration(neutral.mapIndexed { index, cue -> if (index == 0) accent else cue })
            val last = duration(neutral.mapIndexed { index, cue -> if (index == width - 1) accent else cue })
            assertEquals(first, last)
            assertEquals(singleDelta * multiWordPenalty(width), (last - baseline).toDouble(), 2.0)
        }
    }

    @Test
    fun tailContoursBelongToTheirOwnWordAndStillObeyTheProsodyToggle() {
        val neutral = List(3) { RsvpWordExpression() }
        val shaped = neutral.dropLast(1) + RsvpWordExpression(phraseContour = PhraseContour(0.2, 0.0))
        assertTrue(duration(shaped) > duration(neutral))
        assertEquals(duration(neutral, false), duration(shaped, false))
    }

    @Test
    fun engineCarriesALandingThatFallsInsideAGroup() {
        val tokens = listOf(word("in"), word("the"), word("room"), Token(".", TokenType.PUNCTUATION))
        val engine = ComprehensionRsvpEngine()
        val options = RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH)
        val grouped = config.copy(enablePhraseChunking = true, maxWordsPerUnit = 3, maxCharsPerUnit = 32)
        val withLanding = engine.generateFrames(tokens, 0, grouped.copy(useAnticipatoryLanding = true), options)
        val withoutLanding = engine.generateFrames(tokens, 0, grouped.copy(useAnticipatoryLanding = false), options)
        val index = withLanding.indexOfFirst { it.tokens.any { token -> token.text == "the" } }
        val frame = withLanding[index]
        assertTrue(frame.tokens.count { it.type == TokenType.WORD } > 1)
        assertTrue(frame.originalTokenIndex < 1)
        assertTrue(frame.durationMs > withoutLanding[index].durationMs)
    }

    private fun duration(expressions: List<RsvpWordExpression>, prosody: Boolean = true): Long = computeUnitDurationMs(
        RsvpUnitTimingInput(
            frameTokens = List(expressions.size) { word("oak") },
            config = config.copy(useProsodyPacing = prosody),
            contextBefore = ContextSnapshot(0, false),
            rhythm = RhythmState(1.0, 1000.0, 1000.0),
            prevToken = null,
            prevWord = null,
            nextToken = null,
            nextWord = null,
            boundaryBefore = BoundaryBefore.NONE,
            wordExpressions = expressions,
        ),
    )

    private fun word(text: String) = Token(text, TokenType.WORD)
}
