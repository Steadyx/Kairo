package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.engine.applyBlinkSeparation
import com.kairo.reader.ui.rsvp.frameLoadConfigKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSeparationTest {
    private val config = RsvpConfig(tempoMsPerWord = 200L, blinkMode = BlinkMode.SUBTLE)

    @Test
    fun repeatedFunctionWordsAreSeparatedAtOrdinarySpeedsWithoutLosingSourcePosition() {
        val first = frame("had").copy(originalTokenIndex = 7, phraseStartTokenIndex = 5, phraseEndTokenIndexExclusive = 9)
        val frames = mutableListOf(first, frame("had"))
        applyBlinkSeparation(frames, config)
        assertEquals(3, frames.size)
        val gap = frames[1]
        assertTrue(gap.isWordSeparation)
        assertTrue(gap.isRepeatedWordSeparation)
        assertEquals(20L, gap.durationMs)
        assertEquals(400L, frames.sumOf { it.durationMs })
        assertEquals(first.originalTokenIndex, gap.originalTokenIndex)
        assertEquals(first.resumeCursor, gap.resumeCursor)
        assertEquals(first.phraseStartTokenIndex, gap.phraseStartTokenIndex)
        assertEquals(first.phraseEndTokenIndexExclusive, gap.phraseEndTokenIndexExclusive)
    }

    @Test
    fun bothLegacyEnabledModesHaveTheSameBehaviour() {
        val subtle = mutableListOf(frame("complexity"), frame("varies"))
        val adaptive = subtle.toMutableList()
        applyBlinkSeparation(subtle, config)
        applyBlinkSeparation(adaptive, config.copy(blinkMode = BlinkMode.ADAPTIVE))
        assertTrue(subtle.any { it.isWordSeparation })
        assertEquals(subtle, adaptive)
    }

    @Test
    fun separationNeverStealsTheMinimumWordExposureOrSplitsChunks() {
        val protected = mutableListOf(frame("had").copy(durationMs = 50L), frame("had"))
        applyBlinkSeparation(protected, config.copy(minWordMs = 50L))
        assertEquals(2, protected.size)
        val chunk = frame("calm").copy(tokens = listOf(word("calm"), word("water")))
        val chunks = mutableListOf(chunk, frame("next"))
        applyBlinkSeparation(chunks, config)
        assertEquals(listOf(chunk, frame("next")), chunks)
    }

    @Test
    fun offDisablesSeparationAndAllLegacyContextModesSupportIt() {
        val disabled = config.copy(blinkMode = BlinkMode.OFF)
        val frames = mutableListOf(frame("had"), frame("had"))
        applyBlinkSeparation(frames, disabled)
        assertFalse(frames.any { it.isWordSeparation })
        assertNotEquals(frameLoadConfigKey(config), frameLoadConfigKey(disabled))
        for (mode in RsvpContextAssistMode.entries) {
            val enabled = mutableListOf(frame("had"), frame("had"))
            applyBlinkSeparation(enabled, config.copy(contextAssistMode = mode))
            assertTrue(enabled.any { it.isWordSeparation })
        }
    }

    private fun word(text: String) = Token(text, TokenType.WORD, frequencyScore = 1.0)

    private fun frame(text: String) = RsvpFrame(listOf(word(text)), durationMs = 200L)
}
