package com.kairo.reader.core.rsvp.timing

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.analysis.RsvpThoughtCue
import com.kairo.reader.core.rsvp.engine.BoundaryBefore
import com.kairo.reader.core.rsvp.engine.ContextSnapshot
import com.kairo.reader.core.rsvp.engine.FlowState
import com.kairo.reader.core.rsvp.engine.RhythmState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpBoundaryIntegrationTest {
    @Test
    fun integrationAndLandingShareOneBreathWhileExplicitPunctuationStillAddsTime() {
        val config = RsvpConfig(tempoMsPerWord = 150L, useAdaptiveTiming = false)
        val baseline = duration(config.copy(usePunctuationLandingHold = false), 0.0)
        val landing = duration(config, 0.0)
        val integration = duration(config.copy(usePunctuationLandingHold = false), 100.0)
        val both = duration(config, 100.0)
        assertTrue(landing > baseline)
        assertEquals(baseline + 100, integration)
        assertEquals(maxOf(landing, integration), both)
        assertTrue(duration(config.copy(periodPauseMs = config.periodPauseMs + 200), 100.0) > both)
    }

    private fun duration(config: RsvpConfig, integration: Double): Long = computeUnitDurationMs(
        RsvpUnitTimingInput(
            frameTokens = listOf(Token("finished", TokenType.WORD), Token(".", TokenType.PUNCTUATION)),
            config = config,
            contextBefore = ContextSnapshot(0, false),
            rhythm = RhythmState(1.0, 2.0, 2.0),
            flow = FlowState(1.0, 1.0, 1.0, 0.0),
            prevToken = null,
            prevWord = null,
            nextToken = Token("Next", TokenType.WORD),
            nextWord = Token("Next", TokenType.WORD),
            boundaryBefore = BoundaryBefore.NONE,
            thoughtCues = listOf(RsvpThoughtCue(0, 2, true, false, integration)),
        ),
    )
}
