package com.kairo.reader.ui.rsvp

import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.timing.RsvpSessionTimingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpThoughtPlaybackTest {
    @Test
    fun pausedReplayKeepsPreparationEvenAfterABriefPause() {
        var now = 0L
        val runtime = RsvpRuntimeState(monotonicTimeMs = { now })
        runtime.isPlaying = false
        runtime.replayPreparationPending = true
        now = 100L
        resumePlayback(runtime)
        assertEquals(1.0, runtime.resumePreparationScale, 0.0)
        assertEquals(false, runtime.replayPreparationPending)
    }

    @Test
    fun replayUsesTheWholePlannedPhraseAndTreatsLaterSplitChunksAsInsideIt() {
        val frames = listOf(frame(0, 0, 2), frame(1, 0, 2), frame(2, 2, 4), frame(2, 2, 4), frame(3, 2, 4))
        assertEquals(0, findPlannedReplayFrameIndex(frames, 1))
        assertEquals(0, findPlannedReplayFrameIndex(frames, 2))
        assertEquals(2, findPlannedReplayFrameIndex(frames, 3))
        assertEquals(2, findPlannedReplayFrameIndex(frames, 4))
    }

    @Test
    fun recoveryWaitsForPhrasesAndRepeatedReplayRetainsMoreTime() {
        val runtime = RsvpRuntimeState()
        registerRsvpRegression(runtime, true)
        val initial = runtime.comprehensionPaceScale
        repeat(100) { recoverRsvpRegressionPace(runtime, true, endsPhrase = false) }
        assertEquals(initial, runtime.comprehensionPaceScale)
        recoverRsvpRegressionPace(runtime, true, endsPhrase = true)
        assertEquals(initial, runtime.comprehensionPaceScale)
        registerRsvpRegression(runtime, true)
        assertTrue(runtime.comprehensionPaceScale > initial)
        repeat(20) { recoverRsvpRegressionPace(runtime, true, endsPhrase = true) }
        assertEquals(1f, runtime.comprehensionPaceScale)
    }

    @Test
    fun resumePreparationMeasuresTheInterruptionWithoutChangingTheSelectedTempo() {
        var now = 0L
        val runtime = RsvpRuntimeState(monotonicTimeMs = { now })
        runtime.currentTempoMsPerWord = 200L
        runtime.isPlaying = false
        now = 500L
        resumePlayback(runtime)
        val brief = runtime.resumePreparationScale
        runtime.isPlaying = false
        now = 20_000L
        resumePlayback(runtime)
        assertTrue(brief < runtime.resumePreparationScale)
        assertEquals(1.0, runtime.resumePreparationScale, 0.0)
        assertEquals(200L, runtime.currentTempoMsPerWord)
        assertTrue(RsvpSessionTimingPolicy.resumePreparationScale(-1L) >= 0.0)
    }

    @Test
    fun peripheralContentAndReservedWidthStayFixedThroughoutAThought() {
        val tokens = "before this active thought ends next words".split(" ").map(::word)
        val first = frame(2, 2, 5)
        val last = frame(4, 2, 5)
        assertEquals(resolveStablePeripheralWindow(tokens, first), resolveStablePeripheralWindow(tokens, last))
        val window = requireNotNull(resolveStablePeripheralWindow(tokens, first))
        assertEquals(2, window.focusStartIndex)
        assertEquals(5, window.focusEndExclusive)
        val frames = listOf(frame(0, 0, 2), frame(1, 0, 2), first, frame(3, 2, 5), last)
        assertEquals(2..4, resolveThoughtEnvelopeFrameRange(frames, 2))
        assertEquals(2..4, resolveThoughtEnvelopeFrameRange(frames, 4))
    }

    @Test
    fun peripheralContextDoesNotCrossParagraphs() {
        val tokens = listOf(
            word("before"),
            Token("\n", TokenType.PARAGRAPH_BREAK),
            word("thought"),
            Token("\n", TokenType.PARAGRAPH_BREAK),
            word("after")
        )
        val window = requireNotNull(resolveStablePeripheralWindow(tokens, frame(2, 2, 3)))
        assertEquals(2, window.startIndex)
        assertEquals(3, window.endExclusive)
    }

    private fun word(text: String) = Token(text, TokenType.WORD)

    private fun frame(index: Int, start: Int, end: Int) = RsvpFrame(
        tokens = listOf(word("word")),
        durationMs = 200L,
        originalTokenIndex = index,
        phraseStartTokenIndex = start,
        phraseEndTokenIndexExclusive = end,
        endsPhrase = index == end - 1,
    )
}
