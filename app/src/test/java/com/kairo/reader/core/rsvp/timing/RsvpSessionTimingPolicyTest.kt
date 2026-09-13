package com.kairo.reader.core.rsvp.timing

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpSessionTimingPolicyTest {
    @Test
    fun initialTimingStartsAtSelectedPositionWithoutSlowingEarlierNavigation() {
        val frames = MutableList(20) { RsvpFrame(listOf(Token("word", TokenType.WORD)), 100L) }
        val config = RsvpConfig(startDelayMs = 200L, endDelayMs = 50L, rampUpFrames = 2, rampDownFrames = 2)
        RsvpSessionTimingPolicy.applyInitialSessionRamps(frames, config, startFrameIndex = 10)
        assertEquals(100L, frames[0].durationMs)
        assertEquals(100L, frames[9].durationMs)
        assertEquals(335L, frames[10].durationMs)
        assertTrue(frames.last().durationMs > 150L)
    }

    @Test
    fun resumingBeforeOriginalStartGetsPreparationButOriginalStartIsNotDoubled() {
        val config = RsvpConfig(startDelayMs = 200L, rampUpFrames = 2)
        assertEquals(0L, RsvpSessionTimingPolicy.resumeDelayMs(config, 10, 10, initialRampStartIndex = 10))
        assertEquals(1.0, RsvpSessionTimingPolicy.resumeRampMultiplier(config, 10, 10, initialRampStartIndex = 10), 0.0)
        assertEquals(200L, RsvpSessionTimingPolicy.resumeDelayMs(config, 0, 0, initialRampStartIndex = 10))
        assertTrue(RsvpSessionTimingPolicy.resumeRampMultiplier(config, 0, 0, initialRampStartIndex = 10) > 1.0)
    }
}
