package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.analysis.RsvpPhraseBoundaries
import com.kairo.reader.core.rsvp.analysis.analyzeExpandedTokens
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import com.kairo.reader.core.tokenization.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpExpressiveReadingTest : ComprehensionRsvpTestBase() {
    private val english = RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH)

    @Test
    fun connectedUsesOfAmbiguousWordsStayInsideOneThought() {
        for (text in listOf(
            "I want that one",
            "We met after school",
            "I saw the day before yesterday",
            "Butter and jam",
            "We left before spring"
        )) {
            val frames = frames(text)
            assertEquals(text, 1, frames.mapNotNull { it.phraseStartTokenIndex }.distinct().size)
        }
    }

    @Test
    fun pairedConnectivesBreatheBeforeThePair() {
        val tokens = RsvpPhraseBoundaries.annotate(tokenize("We continued even though it rained"), RsvpLanguagePolicy.ENGLISH)
        assertTrue(tokens.first { it.text == "even" }.isClauseBoundary)
        assertFalse(tokens.first { it.text == "though" }.isClauseBoundary)
    }

    @Test
    fun dependentClausesShareTheirBoundaryAcrossGroupingWidths() {
        for (width in 1..3) {
            val config = stableConfig.copy(enablePhraseChunking = width > 1, maxWordsPerUnit = width, maxCharsPerUnit = 32)
            for (text in listOf("I waited before opening the door", "I stayed because she asked", "We left after he called")) {
                val tokens = tokenize(text)
                val frames = engine.generateFrames(tokens, 0, config, english)
                assertEquals(text, 2, frames.mapNotNull { it.phraseStartTokenIndex }.distinct().size)
                val boundary = tokens.indexOfFirst { it.text in setOf("before", "because", "after") }
                assertTrue(frames.none { it.originalTokenIndex < boundary && it.displayOriginalEndExclusive > boundary })
            }
        }
    }

    @Test
    fun unknownAndNonEnglishBooksDoNotAcquireEnglishWordListBoundaries() {
        val tokens = tokenize("I stayed because she asked")
        for (language in RsvpLanguagePolicy.entries.filter { it != RsvpLanguagePolicy.ENGLISH }) {
            assertFalse(RsvpPhraseBoundaries.annotate(tokens, language).any { it.isClauseBoundary })
            val frames = engine.generateFrames(tokens, 0, stableConfig, RsvpGenerationOptions(language))
            assertEquals(language.name, 1, frames.mapNotNull { it.phraseStartTokenIndex }.distinct().size)
        }
    }

    @Test
    fun authorEmphasisChangesOnlyItsOwnExposureAndRespectsTheProsodyToggle() {
        val neutral = tokenize("I said Tuesday")
        val marked = Tokenizer().tokenize(Chapter(0, null, "<p>I said <em>Tuesday</em></p>", "I said Tuesday"))
        val config = stableConfig.copy(useFocalStress = false, useAnticipatoryLanding = false)
        val before = engine.generateFrames(neutral, 0, config, english)
        val after = engine.generateFrames(marked, 0, config, english)
        assertEquals(before.take(2).map { it.durationMs }, after.take(2).map { it.durationMs })
        assertTrue(after.last().durationMs > before.last().durationMs)
        val disabled = config.copy(useProsodyPacing = false, useFocalStress = true)
        assertEquals(
            engine.generateFrames(neutral, 0, disabled, english).map { it.durationMs },
            engine.generateFrames(marked, 0, disabled, english).map { it.durationMs },
        )
    }

    @Test
    fun clearPhraseFocusDoesNotDefaultToTheLongestWord() {
        val analysis = analyze("She photographed the cat")
        assertEquals(setOf(3), analysis.focalWordIndices)
        val ambiguous = analyze("Mysterious visitors arrived quietly")
        assertEquals(setOf(0, 1, 2, 3), ambiguous.focalWordIndices)
    }

    @Test
    fun authoredShortWordKeepsFocusAndSplitWordResumeCursorsRemainDistinct() {
        val tokens = tokenize("I said representatives").map { it.copy(authorEmphasis = it.text == "I" || it.text == "representatives") }
        val config = stableConfig.copy(maxChunkLength = 6)
        val frames = engine.generateFrames(tokens, 0, config, english)
        val chunks = frames.filter { it.originalTokenIndex == 2 }
        assertTrue(chunks.size > 1)
        assertEquals(chunks.size, chunks.map { it.resumeCursor }.distinct().size)
        assertTrue(chunks.all { frame -> frame.tokens.filter { it.type == TokenType.WORD }.all { it.authorEmphasis } })
        assertEquals(listOf(0, 1, 2), frames.map { it.originalTokenIndex }.distinct())
    }

    private fun tokenize(text: String) = Tokenizer().tokenize(Chapter(0, null, "", text))

    private fun frames(text: String) = engine.generateFrames(tokenize(text), 0, stableConfig, english)

    private fun analyze(text: String) = analyzeExpandedTokens(
        tokenize(text).mapIndexed { index, token -> ExpandedToken(token, index, index, 0, token.text.length) },
        stableConfig,
        RsvpLanguagePolicy.ENGLISH,
    )
}
