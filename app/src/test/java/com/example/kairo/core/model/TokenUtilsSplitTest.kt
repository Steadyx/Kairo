package com.example.kairo.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenUtilsSplitTest {
    @Test
    fun hyphenatedSplitPreservesLinkMetadataAndFinalPause() {
        val token =
            Token(
                text = "over-telling",
                type = TokenType.WORD,
                pauseAfterMs = 33L,
                linkChapterIndex = 7,
                isDialogue = true,
            )

        val split = splitTokenForRsvp(token, maxChunkLength = 32, subwordChunkPauseMs = 11L)

        assertEquals(listOf("over-", "telling"), split.map { it.text })
        assertEquals(listOf(7, 7), split.map { it.linkChapterIndex })
        assertEquals(listOf(0L, 33L), split.map { it.pauseAfterMs })
        assertTrue(split.all { it.isDialogue })
        assertTrue(split.all { it.highlightStart == null && it.highlightEndExclusive == null })
    }

    @Test
    fun longWordChunkingPreservesLinkMetadataAndUsesFinalPauseOnlyOnLastChunk() {
        val token =
            Token(
                text = "supercalifragilistic",
                type = TokenType.WORD,
                pauseAfterMs = 41L,
                linkChapterIndex = 3,
            )

        val split = splitTokenForRsvp(token, maxChunkLength = 5, subwordChunkPauseMs = 17L)

        assertTrue(split.size > 1)
        assertEquals(List(split.size) { 3 }, split.map { it.linkChapterIndex })
        assertTrue(split.all { it.isSubwordChunk })
        assertTrue(split.dropLast(1).all { it.pauseAfterMs == 17L })
        assertEquals(41L, split.last().pauseAfterMs)
        assertTrue(split.all { it.highlightStart != null && it.highlightEndExclusive != null })
    }
}
