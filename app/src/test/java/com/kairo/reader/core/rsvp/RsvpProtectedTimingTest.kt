package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.analysis.RsvpReadingDemand
import com.kairo.reader.core.rsvp.engine.BoundaryBefore
import com.kairo.reader.core.rsvp.engine.ContextSnapshot
import com.kairo.reader.core.rsvp.engine.RhythmState
import com.kairo.reader.core.rsvp.timing.RsvpUnitTimingInput
import com.kairo.reader.core.rsvp.timing.computeUnitTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpProtectedTimingTest {
    private val easy = Token("cat", TokenType.WORD)
    private val config = RsvpConfig(
        tempoMsPerWord = 150L, minWordMs = 40L, longWordMinMs = 120L,
        useAdaptiveTiming = false, useProsodyPacing = false, useClausePausing = false,
        useDialogueDetection = false, usePunctuationLandingHold = false,
        startDelayMs = 0L, endDelayMs = 0L, rampUpFrames = 0, rampDownFrames = 0,
    )

    @Test
    fun difficultWordKeepsItsAllowanceAfterEasyWordsEvenWithNearFrozenRhythm() {
        val rhythm = rhythm()
        val baseline = duration(listOf(easy), rhythm)
        repeat(8) { duration(listOf(easy), rhythm) }
        val hard = duration(listOf(easy), rhythm, demands = listOf(RsvpReadingDemand(2.0)))
        val unsupported = duration(listOf(easy), rhythm(), config.copy(difficultWordSupport = 0.0), listOf(RsvpReadingDemand(2.0)))
        assertTrue(hard.protectedWordMs > 80L)
        assertTrue(hard.durationMs >= unsupported.durationMs + hard.protectedWordMs - 15L)
        assertTrue(kotlin.math.abs(duration(listOf(easy), rhythm).durationMs - baseline.durationMs) <= 1L)
    }

    @Test
    fun evenSmallAllowancesBypassSmoothing() {
        val rhythm = rhythm()
        val baseline = duration(listOf(easy), rhythm)
        repeat(8) { duration(listOf(easy), rhythm) }
        val held = duration(listOf(easy), rhythm, demands = listOf(RsvpReadingDemand(0.03)))
        assertTrue(held.protectedWordMs in 3L..4L)
        assertTrue(held.durationMs >= baseline.durationMs + 3L)
    }

    @Test
    fun explicitWordPausesAreAddedAfterTheDisplayFloor() {
        val settings = config.copy(minWordMs = 220L)
        val baseline = duration(listOf(easy), rhythm(), settings)
        val held = duration(listOf(easy.copy(pauseAfterMs = 50L)), rhythm(), settings)
        assertTrue(held.durationMs >= baseline.durationMs + 40L)
    }

    @Test
    fun groupingDoesNotLoseReadingTimeOrSlowTheNextSingleWord() {
        val rhythm = rhythm()
        for (count in listOf(1, 2, 3, 1, 3, 2, 1)) {
            val independent = duration(List(count) { easy }, rhythm())
            assertEquals(independent, duration(List(count) { easy }, rhythm))
        }
    }

    @Test
    fun supportScalesAllowanceAndLeavesFloorsAlone() {
        val demand = listOf(RsvpReadingDemand(1.0))
        val off = duration(listOf(easy), rhythm(), config.copy(difficultWordSupport = 0.0), demand)
        val normal = duration(listOf(easy), rhythm(), config, demand)
        val strong = duration(listOf(easy), rhythm(), config.copy(difficultWordSupport = 2.0), demand)
        assertEquals(0L, off.protectedWordMs)
        assertTrue(normal.protectedWordMs > 0L)
        assertTrue(kotlin.math.abs(strong.protectedWordMs - 2 * normal.protectedWordMs) <= 1L)
        for (support in listOf(0.0, 1.0, 2.0)) {
            assertEquals(220L, duration(listOf(easy), rhythm(), config.copy(minWordMs = 220L, difficultWordSupport = support)).durationMs)
        }
    }

    @Test
    fun easyCompanionsAndFinalPositionCannotDiluteAWordAllowance() {
        val hard = RsvpReadingDemand(2.0)
        val solo = duration(listOf(easy), rhythm(), demands = listOf(hard))
        val grouped = duration(listOf(easy, easy), rhythm(), demands = listOf(RsvpReadingDemand(0.0), hard))
        assertEquals(solo.protectedWordMs, grouped.protectedWordMs)
        val engine = ComprehensionRsvpEngine()
        val tokens = listOf(easy, Token("quizzacious", TokenType.WORD))
        val frames = engine.generateFrames(tokens, 0, config.copy(maxChunkLength = 30), RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH))
        assertTrue(frames.last().protectedWordMs > 0)
    }

    @Test
    fun displayedAllowanceSurvivesWordSeparation() {
        val tokens = listOf(easy, easy, Token("quizzacious", TokenType.WORD), easy)
        val frames = ComprehensionRsvpEngine().generateFrames(
            tokens,
            0,
            config.copy(blinkMode = BlinkMode.SUBTLE, maxChunkLength = 0),
            RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH),
        )
        val hardFrame = frames.first { !it.isWordSeparation && it.originalTokenIndex == 2 }
        assertTrue(hardFrame.protectedWordMs > 80L)
        assertTrue(hardFrame.durationMs > hardFrame.protectedWordMs)
        assertTrue(frames.none { it.isWordSeparation && it.protectedWordMs != 0L })
    }

    @Test
    fun readabilityFloorCannotConsumeTheDifficultWordAllowance() {
        for (tempo in listOf(50L, 150L, 300L)) {
            for (count in listOf(1, 2, 3)) {
                val settings = config.copy(tempoMsPerWord = tempo, minWordMs = 400L)
                val words = List(count) { easy }
                val demands = List(count) { RsvpReadingDemand(1.5) }
                val off = duration(words, rhythm(), settings.copy(difficultWordSupport = 0.0), demands)
                val on = duration(words, rhythm(), settings, demands)
                assertTrue("tempo=$tempo count=$count", on.durationMs >= off.durationMs + on.protectedWordMs - 1L)
            }
        }
    }

    private fun rhythm() = RhythmState(smoothingAlpha = 0.01, maxSpeedupFactor = 1.01, maxSlowdownFactor = 1.01)

    private fun duration(
        words: List<Token>,
        rhythm: RhythmState,
        settings: RsvpConfig = config,
        demands: List<RsvpReadingDemand> = emptyList(),
    ) = computeUnitTiming(
        RsvpUnitTimingInput(
            frameTokens = words, config = settings, contextBefore = ContextSnapshot(0, false), rhythm = rhythm,
            prevToken = easy, prevWord = easy, nextToken = easy, nextWord = easy, boundaryBefore = BoundaryBefore.NONE,
            readingDemands = demands,
        )
    )
}
