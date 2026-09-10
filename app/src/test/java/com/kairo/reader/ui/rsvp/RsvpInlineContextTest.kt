package com.kairo.reader.ui.rsvp

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.graphics.Color
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpInlineContextTest {
    @Test
    fun cueContainsOnlyAlreadyReadWordsFromTheCurrentPhrase() {
        val tokens = "earlier sentence she had already left".split(" ").map(::word)
        val frame = RsvpFrame(listOf(tokens[4]), 200L, originalTokenIndex = 4, phraseStartTokenIndex = 2, phraseEndTokenIndexExclusive = 6)
        val context = requireNotNull(buildRsvpReadingContext(tokens, frame, Color.Black))
        assertEquals(listOf("she had", "had"), context.precedingCues.map { it.text })
        assertEquals("she had already left", context.pausedPhrase.text)
        assertFalse(context.precedingCues.any { "left" in it.text || "earlier" in it.text })
    }

    @Test
    fun phraseEntryHasNoTrailAndSplitWordsNeverLeakTheirUnreadRemainder() {
        val tokens = listOf(word("she"), word("ha-ha-ha"), word("left"))
        val entry = RsvpFrame(listOf(tokens[0]), 200L, phraseStartTokenIndex = 0, phraseEndTokenIndexExclusive = 3)
        assertTrue(requireNotNull(buildRsvpReadingContext(tokens, entry, Color.Black)).precedingCues.isEmpty())
        val split = entry.copy(
            tokens = listOf(word("ha-")),
            originalTokenIndex = 1,
            displayOriginalStartIndex = 1,
            displayOriginalEndExclusive = 2,
            displayOriginalStartCharacterOffset = 3,
            displayOriginalEndCharacterOffset = 6,
        )
        assertEquals(listOf("she"), requireNotNull(buildRsvpReadingContext(tokens, split, Color.Black)).precedingCues.map { it.text })
    }

    @Test
    fun ordinaryTransitionsDimButRepeatedWordsBlankAndPausingAlwaysRestoresTheWord() {
        val frame = RsvpFrame(emptyList(), 20L, isWordSeparation = true)
        val config = RsvpConfig(blinkMode = BlinkMode.SUBTLE)
        assertTrue(wordSeparationAlpha(frame, true, config) in 0.1f..0.8f)
        assertEquals(0f, wordSeparationAlpha(frame.copy(isRepeatedWordSeparation = true), true, config))
        assertEquals(1f, wordSeparationAlpha(frame, false, config))
        assertEquals(1f, wordSeparationAlpha(frame, true, config.copy(blinkMode = BlinkMode.OFF)))
    }

    @Test
    fun pulseWaitsForWholeRefreshIntervalsOn60And120HzDisplays() = runTest {
        for ((rate, expectedIntervals) in listOf(60f to 1, 90f to 2, 120f to 2)) {
            var callbacks = 0
            val clock = object : MonotonicFrameClock {
                override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                    callbacks++
                    return onFrame((callbacks * 1_000_000_000.0 / rate).toLong())
                }
            }
            withContext(clock) { awaitWordSeparationPulse(20L, rate) }
            assertEquals(expectedIntervals + 1, callbacks)
        }
        assertEquals(1, separationRefreshCount(20L, Float.NaN))
    }

    @Test
    fun continuousCarriesAcrossPhrasesButNeverAcrossParagraphs() {
        val tokens = listOf(word("she"), word("left"), word("then"), word("returned"))
        val frame = RsvpFrame(listOf(tokens[2]), 200L, originalTokenIndex = 2, phraseStartTokenIndex = 2, phraseEndTokenIndexExclusive = 4)
        assertTrue(requireNotNull(buildRsvpReadingContext(tokens, frame, Color.Black)).precedingCues.isEmpty())
        val continuous = requireNotNull(buildRsvpReadingContext(tokens, frame, Color.Black, RsvpContextAssistMode.SENTENCE_TICKER))
        assertEquals(listOf("she left", "left"), continuous.precedingCues.map { it.text })
        assertEquals(listOf("returned"), continuous.followingCues.map { it.text })
        val newParagraph = tokens.toMutableList().apply { this[1] = Token("", TokenType.PARAGRAPH_BREAK) }
        assertTrue(
            requireNotNull(
                buildRsvpReadingContext(newParagraph, frame, Color.Black, RsvpContextAssistMode.SENTENCE_TICKER)
            ).precedingCues.isEmpty()
        )
    }

    @Test
    fun clauseLooksPastThePlaybackPhraseButStopsAtClausePunctuation() {
        val tokens = listOf(
            word("Earlier"), Token(".", TokenType.PUNCTUATION),
            word("she"), word("had"), word("already"), word("left"), word("the"), word("station"),
            Token(";", TokenType.PUNCTUATION), word("later"),
        )
        // The thought planner ends this short phrase on "already", before the clause ends.
        val frame = RsvpFrame(listOf(tokens[4]), 200L, originalTokenIndex = 4, phraseStartTokenIndex = 2, phraseEndTokenIndexExclusive = 5)
        val clause = requireNotNull(buildRsvpReadingContext(tokens, frame, Color.Black, RsvpContextAssistMode.FULL_CLAUSE))
        assertEquals(listOf("she had", "had"), clause.precedingCues.map { it.text })
        assertEquals(listOf("left the station;", "left the", "left"), clause.followingCues.map { it.text })
        assertEquals("she had already left the station;", clause.pausedPhrase.text)
        assertEquals(null, buildRsvpReadingContext(tokens, frame, Color.Black, RsvpContextAssistMode.OFF))
        val previous = requireNotNull(buildRsvpReadingContext(tokens, frame, Color.Black))
        assertEquals("she had already", previous.pausedPhrase.text)
        assertTrue(previous.followingCues.isEmpty())
    }

    @Test
    fun continuousSpansClausesWhileClauseResetsAtTheFirstWordOfEachClause() {
        val tokens = listOf(word("she"), word("left"), word("but").copy(isClauseBoundary = true), word("we"), word("stayed"))
        val frame = RsvpFrame(listOf(tokens[2]), 200L, originalTokenIndex = 2, phraseStartTokenIndex = 2, phraseEndTokenIndexExclusive = 4)
        val clause = requireNotNull(buildRsvpReadingContext(tokens, frame, Color.Black, RsvpContextAssistMode.FULL_CLAUSE))
        val continuous = requireNotNull(buildRsvpReadingContext(tokens, frame, Color.Black, RsvpContextAssistMode.SENTENCE_TICKER))
        val previous = requireNotNull(buildRsvpReadingContext(tokens, frame, Color.Black))
        assertTrue(clause.precedingCues.isEmpty())
        assertEquals("but we stayed", clause.pausedPhrase.text)
        assertEquals("she left", continuous.precedingCues.first().text)
        assertEquals("we stayed", continuous.followingCues.first().text)
        assertEquals("she left but we stayed", continuous.pausedPhrase.text)
        assertTrue(previous.precedingCues.isEmpty())
        assertTrue(previous.followingCues.isEmpty())
    }

    @Test
    fun continuousPreviewsPastSentenceEndsButStopsAtParagraphs() {
        val tokens = listOf(
            word("she"),
            word("left"),
            Token(".", TokenType.PUNCTUATION),
            word("We"),
            word("stayed"),
            Token("", TokenType.PARAGRAPH_BREAK),
            word("Elsewhere")
        )
        val frame = RsvpFrame(listOf(tokens[1]), 200L, originalTokenIndex = 1)
        val clause = requireNotNull(buildRsvpReadingContext(tokens, frame, Color.Black, RsvpContextAssistMode.FULL_CLAUSE))
        val continuous = requireNotNull(buildRsvpReadingContext(tokens, frame, Color.Black, RsvpContextAssistMode.SENTENCE_TICKER))
        assertTrue(clause.followingCues.isEmpty())
        assertEquals(". We stayed", continuous.followingCues.first().text)
        assertFalse(continuous.pausedPhrase.text.contains("Elsewhere"))
    }

    @Test
    fun largerCueScalesWithReadingTextWithoutExceedingIt() {
        assertEquals(30f, inlineContextFontSizeSp(40f), 0f)
        assertEquals(36f, inlineContextFontSizeSp(64f), 0f)
        assertEquals(12f, inlineContextFontSizeSp(12f), 0f)
    }

    private fun word(text: String) = Token(text, TokenType.WORD)
}
