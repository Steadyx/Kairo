package com.kairo.reader.core.rsvp.timing

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpSourceWordRampTest {
    private val config = RsvpConfig(startDelayMs = 0, endDelayMs = 0, rampUpFrames = 4, rampDownFrames = 0)
    private val word = Token("word", TokenType.WORD)

    @Test
    fun groupingConservesTheOpeningRampAllowance() {
        val singles = MutableList(12) { RsvpFrame(listOf(word), 100L, originalTokenIndex = it) }
        val pairs = MutableList(6) { RsvpFrame(listOf(word, word), 200L, originalTokenIndex = it * 2) }
        RsvpSessionTimingPolicy.applyInitialSessionRamps(singles, config)
        RsvpSessionTimingPolicy.applyInitialSessionRamps(pairs, config)
        assertEquals(singles.sumOf { it.durationMs }.toDouble(), pairs.sumOf { it.durationMs }.toDouble(), 4.0)
        assertEquals(100L, singles[4].durationMs)
        assertEquals(200L, pairs[2].durationMs)
    }

    @Test
    fun partsShareOneWordAndSeparationDoesNotAdvanceTheRamp() {
        val frames = listOf(
            RsvpFrame(listOf(word.copy(isSubwordChunk = true)), 50, originalTokenIndex = 0),
            RsvpFrame(listOf(word.copy(isSubwordChunk = true)), 50, originalTokenIndex = 0),
            RsvpFrame(emptyList(), 20, isWordSeparation = true),
            RsvpFrame(listOf(word), 100, originalTokenIndex = 1),
        )
        val progress = RsvpSessionTimingPolicy.sourceWordProgress(frames)
        assertEquals(listOf(0.0, 0.5, 1.0, 1.0, 2.0), progress.toList())
        assertEquals(1.0, RsvpSessionTimingPolicy.resumeRampMultiplier(config, 2, 0, progress, initialRampStartIndex = -1), 0.0)
    }

    @Test
    fun fractionalPartsEndExactlyAtTheWordBoundary() {
        val frames = List(6) { RsvpFrame(listOf(word.copy(isSubwordChunk = true)), 50, originalTokenIndex = 0) } +
            RsvpFrame(listOf(word), 100, originalTokenIndex = 1)
        val progress = RsvpSessionTimingPolicy.sourceWordProgress(frames)
        assertEquals(1.0, progress[6], 0.0)
        assertEquals(2.0, progress.last(), 0.0)
        assertEquals(
            1.175,
            RsvpSessionTimingPolicy.resumeRampMultiplier(config.copy(rampUpFrames = 1), 6, 6, progress),
            0.00001,
        )
    }

    @Test
    fun shortPassageResumeOutsideItsCappedOpeningStillReceivesPreparation() {
        val progress = DoubleArray(5) { it.toDouble() }
        val timing = config.copy(rampUpFrames = 5, startDelayMs = 200)
        assertEquals(0L, RsvpSessionTimingPolicy.resumeDelayMs(timing, 1, 1, progress))
        assertEquals(200L, RsvpSessionTimingPolicy.resumeDelayMs(timing, 2, 2, progress))
    }

    @Test
    fun resumeUsesWordsAndDoesNotDoubleTheEmbeddedOpeningRamp() {
        val frames = List(10) { RsvpFrame(listOf(word, word), 200, originalTokenIndex = it * 2) }
        val progress = RsvpSessionTimingPolicy.sourceWordProgress(frames)
        assertEquals(1.0, RsvpSessionTimingPolicy.resumeRampMultiplier(config, 0, 0, progress), 0.0)
        assertEquals(1.2625, RsvpSessionTimingPolicy.resumeRampMultiplier(config, 4, 4, progress), 0.00001)
        assertEquals(1.0, RsvpSessionTimingPolicy.resumeRampMultiplier(config, 6, 4, progress), 0.0)
        assertEquals(1.0, RsvpSessionTimingPolicy.resumeRampMultiplier(config.copy(rampUpFrames = 0), 4, 4, progress), 0.0)
    }
}
