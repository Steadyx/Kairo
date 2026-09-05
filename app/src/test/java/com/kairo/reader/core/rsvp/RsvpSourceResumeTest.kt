package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpResumeCursor
import com.kairo.reader.data.rsvp.RsvpFrameIndexMap
import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpSourceResumeTest : ComprehensionRsvpTestBase() {
    @Test
    fun sourcePositionSurvivesRegenerationFromDifferentStart() {
        val tokens = listOf(w("Earlier"), p("."), w("mother-in-law"), w("later"))
        val full = engine.generateFrames(tokens, 0, stableConfig)
        val target = full.first { it.tokens.first().text == "in-" }
        val resumed = engine.generateFrames(tokens, 2, stableConfig)
        val aligned = RsvpFrameIndexMap.from(resumed).alignFrameIndex(2, target.resumeCursor, resumed.size)

        assertEquals("in-", resumed[aligned].tokens.first().text)
        assertEquals(7, RsvpResumeCursor.characterOffset(resumed[aligned].resumeCursor))
    }

    @Test
    fun changedChunkWidthResumesAtContainingSourceChunk() {
        val tokens = listOf(w("abcdefghijkl"))
        val original = engine.generateFrames(tokens, 0, stableConfig.copy(maxChunkLength = 3))
        val target = original[2]
        val resized = engine.generateFrames(tokens, 0, stableConfig.copy(maxChunkLength = 4))
        val aligned = RsvpFrameIndexMap.from(resized).alignFrameIndex(0, target.resumeCursor, resized.size)

        assertEquals(6, RsvpResumeCursor.characterOffset(target.resumeCursor))
        assertEquals(4, resized[aligned].tokens.first().highlightStart)
        assertEquals(8, resized[aligned].tokens.first().highlightEndExclusive)
    }

    @Test
    fun repeatedLongHyphenPartsKeepTheirOwnSourceOffsets() {
        val frames = engine.generateFrames(listOf(w("abcdef-abcdef-end")), 0, stableConfig.copy(maxChunkLength = 3))

        assertEquals(listOf(0, 3, 7, 10, 14), frames.map { RsvpResumeCursor.characterOffset(it.resumeCursor) })
        assertEquals(listOf(0, 0, 7, 7, 14), frames.map { it.displayOriginalStartCharacterOffset })
    }

    @Test
    fun resumeCursorCannotSelectAnotherOriginalToken() {
        val tokens = listOf(w("first"), w("second"))
        val frames = engine.generateFrames(tokens, 0, stableConfig)
        val aligned = RsvpFrameIndexMap.from(frames).alignFrameIndex(1, frames[0].resumeCursor, frames.size)

        assertEquals(1, frames[aligned].originalTokenIndex)
    }

    @Test
    fun oldExpandedCursorFallsBackToFirstChunkOfRequestedWord() {
        val frames = engine.generateFrames(listOf(w("mother-in-law")), 0, stableConfig)
        val aligned = RsvpFrameIndexMap.from(frames).alignFrameIndex(0, 2, frames.size)

        assertEquals("mother-", frames[aligned].tokens.first().text)
    }
}
