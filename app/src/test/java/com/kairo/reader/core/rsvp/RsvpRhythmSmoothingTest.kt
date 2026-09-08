package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.engine.RhythmState
import com.kairo.reader.core.rsvp.segmentation.RHYTHM_BOUNDARY_CLAUSE
import com.kairo.reader.core.rsvp.segmentation.RHYTHM_BOUNDARY_HARD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpRhythmSmoothingTest {
    @Test
    fun zeroBoundaryStrengthIsExactlyTheLegacyBooleanPath() {
        val legacy = rhythm(alpha = 0.25)
        val explicit = rhythm(alpha = 0.25)
        val rawDurations = listOf(100.0, 220.0, 80.0, 160.0)

        val legacyDurations = rawDurations.map { raw -> legacy.apply(raw, isBoundary = false) }
        val explicitDurations =
            rawDurations.map { raw ->
                explicit.apply(raw, isBoundary = false, boundaryStrengthMilli = 0)
            }

        assertEquals(legacyDurations, explicitDurations)
        assertEquals(130.0, legacyDurations[1], 0.0)
    }

    @Test
    fun clauseBoundaryPartiallyReseedsWithoutBecomingAHardReset() {
        val ordinary = rhythm(alpha = 0.2)
        val clause = rhythm(alpha = 0.2)
        ordinary.apply(200.0, isBoundary = false)
        clause.apply(200.0, isBoundary = false)

        val ordinaryNext = ordinary.apply(100.0, isBoundary = false)
        val clauseNext =
            clause.apply(
                rawMs = 100.0,
                isBoundary = false,
                boundaryStrengthMilli = RHYTHM_BOUNDARY_CLAUSE,
            )

        assertTrue(clauseNext < ordinaryNext)
        assertTrue(clauseNext > 100.0)
    }

    @Test
    fun hardBoundariesStillReturnRawAndPartialReseedsStillRespectRateLimits() {
        val hard = rhythm(alpha = 0.2)
        hard.apply(200.0, isBoundary = false)
        assertEquals(
            80.0,
            hard.apply(
                rawMs = 80.0,
                isBoundary = false,
                boundaryStrengthMilli = RHYTHM_BOUNDARY_HARD,
            ),
            0.0,
        )

        val limited =
            RhythmState(
                smoothingAlpha = 0.2,
                maxSpeedupFactor = 1.2,
                maxSlowdownFactor = 1.2,
            )
        limited.apply(100.0, isBoundary = false)
        assertEquals(
            120.0,
            limited.apply(
                rawMs = 1000.0,
                isBoundary = false,
                boundaryStrengthMilli = RHYTHM_BOUNDARY_CLAUSE,
            ),
            0.0,
        )
    }

    @Test
    fun scoredSingleWordReadingPreservesFrameOwnershipAcrossSmoothingSettings() {
        val config =
            RsvpConfig(
                tempoMsPerWord = 150L,
                enablePhraseChunking = false,
                maxWordsPerUnit = 2,
                maxChunkLength = 40,
                startDelayMs = 0L,
                endDelayMs = 0L,
                rampUpFrames = 0,
                rampDownFrames = 0,
                smoothingAlpha = 0.2,
                maxSpeedupFactor = 1000.0,
                maxSlowdownFactor = 1000.0,
            )
        val tokens =
            listOf(
                Token(
                    text = "encyclopedic",
                    type = TokenType.WORD,
                    syllableCount = 5,
                    frequencyScore = 0.0,
                    complexityMultiplier = 1.8,
                ),
                Token(text = ",", type = TokenType.PUNCTUATION),
                Token(text = "it", type = TokenType.WORD, frequencyScore = 1.0),
            )
        val engine = ComprehensionRsvpEngine()
        val unsmoothed = engine.generateFrames(
            tokens,
            0,
            config.copy(smoothingAlpha = 1.0),
            RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH)
        )
        val scored =
            engine.generateFrames(
                tokens,
                0,
                config,
                RsvpGenerationOptions(
                    languagePolicy = RsvpLanguagePolicy.ENGLISH,

                ),
            )

        assertEquals(unsmoothed.map(RsvpFrame::tokens), scored.map(RsvpFrame::tokens))
        assertEquals(unsmoothed.first().durationMs, scored.first().durationMs)
        assertNotEquals(unsmoothed[1].durationMs, scored[1].durationMs)
        assertTrue(scored[1].durationMs > unsmoothed[1].durationMs)
    }

    private fun rhythm(alpha: Double): RhythmState =
        RhythmState(
            smoothingAlpha = alpha,
            maxSpeedupFactor = 1000.0,
            maxSlowdownFactor = 1000.0,
        )
}
