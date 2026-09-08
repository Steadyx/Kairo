package com.kairo.reader.ui.rsvp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpContextAssistTest {
    @Test
    fun guideBandHeightReservesSymmetricSpaceAroundFocusText() {
        val textHeight = 45.dp

        val bandHeight = orpGuideBandHeight(textHeight, guideThickness = 1f)

        assertEquals(79.dp, bandHeight)
    }

    @Test
    fun focusEnvelopeRangeStaysStableWithinEachFrameBlock() {
        assertEquals(0 until 12, resolveContextEnvelopeFrameRange(0, 30, blockSize = 6))
        assertEquals(0 until 12, resolveContextEnvelopeFrameRange(5, 30, blockSize = 6))
        assertEquals(6 until 18, resolveContextEnvelopeFrameRange(6, 30, blockSize = 6))
        assertEquals(6 until 18, resolveContextEnvelopeFrameRange(11, 30, blockSize = 6))
    }

    @Test
    fun peripheralCueFontSizeDoesNotDependOnTheDisplayedWord() {
        assertEquals(48f, stableContextCueFontSizeSp(48f), 0.0001f)
    }

    @Test
    fun cueSlotsStayOutsideTheFocusGapAtExtremeHorizontalPositions() {
        val left =
            resolveContextCueSlots(
                availableWidth = 400.dp,
                focusLeftReserve = 80.dp,
                focusRightReserve = 80.dp,
                horizontalBias = HORIZONTAL_BIAS_MIN,
                minimumCueWidth = 48.dp,
                cueInnerPadding = 8.dp,
            )
        val right =
            resolveContextCueSlots(
                availableWidth = 400.dp,
                focusLeftReserve = 80.dp,
                focusRightReserve = 80.dp,
                horizontalBias = HORIZONTAL_BIAS_MAX,
                minimumCueWidth = 48.dp,
                cueInnerPadding = 8.dp,
            )

        assertEquals(0.dp, left.previousWidth)
        assertFalse(left.hasPreviousRoom)
        assertTrue(left.hasUpcomingRoom)
        assertEquals(0.dp, right.upcomingWidth)
        assertTrue(right.hasPreviousRoom)
        assertFalse(right.hasUpcomingRoom)
        assertEquals(400.dp, left.previousWidth + left.focusGap + left.upcomingWidth)
        assertEquals(400.dp, right.previousWidth + right.focusGap + right.upcomingWidth)
    }

    @Test
    fun cueSlotsIncludeAStableInnerSafetyBuffer() {
        val slots =
            resolveContextCueSlots(
                availableWidth = 400.dp,
                focusLeftReserve = 80.dp,
                focusRightReserve = 80.dp,
                horizontalBias = CENTER_BIAS,
                minimumCueWidth = 48.dp,
                cueInnerPadding = 8.dp,
            )

        assertEquals(112.dp, slots.previousWidth)
        assertEquals(176.dp, slots.focusGap)
        assertEquals(112.dp, slots.upcomingWidth)
    }

    @Test
    fun tickerFocusAlignmentToleratesDifferentWhitespaceRules() {
        val alignment =
            resolveContextTickerFocusAlignment(
                sourceText = "\u300C \u4F60\u597D\u300D",
                displayedText = "\u300C\u4F60\u597D\u300D",
                displayedPivot = 1,
            )

        assertEquals(0, alignment.startOffset)
        assertEquals(5, alignment.endExclusiveOffset)
        assertEquals(2, alignment.pivotOffset)
    }

    @Test
    fun tickerFocusAlignmentFailsClosedForUnexpectedFormatting() {
        val alignment =
            resolveContextTickerFocusAlignment(
                sourceText = "original",
                displayedText = "different",
                displayedPivot = 2,
            )

        assertEquals(0, alignment.startOffset)
        assertEquals("original".length, alignment.endExclusiveOffset)
        assertTrue(alignment.pivotOffset in "original".indices)
    }

    @Test
    fun asymmetricFocusEnvelopeKeepsEachCueCloseToItsOwnTextEdge() {
        val slots =
            resolveContextCueSlots(
                availableWidth = 400.dp,
                focusLeftReserve = 60.dp,
                focusRightReserve = 100.dp,
                horizontalBias = CENTER_BIAS,
                minimumCueWidth = 48.dp,
                cueInnerPadding = 8.dp,
            )

        assertEquals(132.dp, slots.previousWidth)
        assertEquals(176.dp, slots.focusGap)
        assertEquals(92.dp, slots.upcomingWidth)
    }

    @Test
    fun peripheralCueKeepsOnlyTheWordsNearestTheFocusGap() {
        val tokens = listOf(word("one"), word("two"), word("three"), word("four"))

        val previous =
            buildPeripheralContextText(
                tokens = tokens,
                startIndex = 0,
                endExclusive = tokens.size,
                maxWords = 2,
                takeLast = true,
                color = Color.White,
                nearestAlpha = 0.3f,
                farthestAlpha = 0.1f,
            )
        val upcoming =
            buildPeripheralContextText(
                tokens = tokens,
                startIndex = 0,
                endExclusive = tokens.size,
                maxWords = 1,
                takeLast = false,
                color = Color.White,
                nearestAlpha = 0.2f,
                farthestAlpha = 0.1f,
            )

        assertEquals("three four", previous.text)
        assertEquals("one", upcoming.text)
    }

    @Test
    fun fullClauseKeepsClauseStarterAndClosingPunctuation() {
        val tokens =
            listOf(
                word("Earlier"),
                punctuation(","),
                word("because", isClauseBoundary = true),
                word("context"),
                word("matters"),
                punctuation("."),
            )

        val window =
            requireNotNull(
                resolveRsvpContextWindow(
                    tokens = tokens,
                    frameStartIndex = 3,
                    frameEndExclusive = 4,
                    mode = RsvpContextAssistMode.FULL_CLAUSE,
                ),
            )

        assertEquals(2, window.startIndex)
        assertEquals(6, window.endExclusive)
        assertEquals(3, window.focusStartIndex)
    }

    @Test
    fun sentenceTickerCarriesContextAcrossSentenceBoundaries() {
        val tokens =
            listOf(
                word("Earlier"),
                punctuation("."),
                word("This"),
                word("sentence"),
                punctuation(","),
                word("keeps"),
                word("moving"),
                punctuation("!"),
                word("Next"),
                word("one"),
                punctuation("."),
            )

        val window =
            requireNotNull(
                resolveRsvpContextWindow(
                    tokens = tokens,
                    frameStartIndex = 5,
                    frameEndExclusive = 6,
                    mode = RsvpContextAssistMode.SENTENCE_TICKER,
                ),
            )

        assertEquals(0, window.startIndex)
        assertEquals(tokens.size, window.endExclusive)
        assertEquals(5, window.focusStartIndex)
    }

    @Test
    fun sentenceTickerStaysPopulatedAcrossParagraphFrames() {
        val tokens =
            listOf(
                word("Before"),
                punctuation("."),
                Token("\n", TokenType.PARAGRAPH_BREAK),
                word("After"),
                word("continues"),
                punctuation("."),
            )
        val window =
            requireNotNull(
                resolveRsvpContextWindow(
                    tokens = tokens,
                    frameStartIndex = 2,
                    frameEndExclusive = 3,
                    mode = RsvpContextAssistMode.SENTENCE_TICKER,
                ),
            )
        val ticker = buildSentenceTickerContent(tokens, window, color = Color.White)

        assertEquals(3, window.focusStartIndex)
        assertEquals("Before.   After continues.", ticker.text.text)
        assertTrue(ticker.text.text.substring(0, ticker.focusStart).contains("Before."))
        assertTrue(ticker.text.text.substring(ticker.focusEndExclusive).contains("continues"))
    }

    @Test
    fun sentenceTickerMakesTheFixedOrpFrameTransparent() {
        val tokens =
            listOf(
                word("A"),
                word("calm"),
                word("sentence"),
                word("moves"),
                punctuation("."),
            )
        val ticker =
            buildSentenceTickerContent(
                tokens = tokens,
                window =
                RsvpContextWindow(
                    startIndex = 0,
                    endExclusive = tokens.size,
                    focusStartIndex = 2,
                    focusEndExclusive = 3,
                ),
                color = Color.White,
            )

        assertEquals("A calm sentence moves.", ticker.text.text)
        assertEquals(
            "sentence",
            ticker.text.text.substring(ticker.focusStart, ticker.focusEndExclusive),
        )
        assertTrue(ticker.pivotPosition in ticker.focusStart until ticker.focusEndExclusive)
        assertEquals(Color.Transparent, ticker.text.spanStyles.last().item.color)
    }

    @Test
    fun sentenceTickerGivesOpeningPunctuationToTheActiveFrameOnly() {
        val tokens =
            listOf(
                word("Earlier"),
                punctuation("."),
                punctuation("\u201C"),
                word("My"),
                word("name's"),
                word("Koli"),
                punctuation(","),
                punctuation("\u201D"),
                word("I"),
                word("say"),
                punctuation("."),
            )
        val window =
            requireNotNull(
                resolveRsvpContextWindow(
                    tokens = tokens,
                    frameStartIndex = 2,
                    frameEndExclusive = 5,
                    mode = RsvpContextAssistMode.SENTENCE_TICKER,
                ),
            )
        val ticker =
            buildSentenceTickerContent(
                tokens = tokens,
                window = window,
                displayedTokens = tokens.subList(2, 5),
                color = Color.White,
            )

        assertEquals(2, window.focusStartIndex)
        assertEquals("\u201CMy name's", ticker.text.text.substring(ticker.focusStart, ticker.focusEndExclusive))
        assertEquals(
            "\u201CMy name's",
            ticker.text.text.substring(
                ticker.displayedFocusStart,
                ticker.displayedFocusEndExclusive,
            ),
        )
        assertEquals(Color.Transparent, ticker.text.spanStyles.last().item.color)
    }

    @Test
    fun sentenceTickerCutoutMatchesSimplifiedOrpPunctuation() {
        val tokens =
            listOf(
                punctuation("\u201C"),
                word("Hello"),
                punctuation(","),
                word("there"),
            )
        val ticker =
            buildSentenceTickerContent(
                tokens = tokens,
                window =
                RsvpContextWindow(
                    startIndex = 0,
                    endExclusive = tokens.size,
                    focusStartIndex = 0,
                    focusEndExclusive = 3,
                ),
                displayedTokens = tokens.subList(0, 3),
                simplifyPunctuation = true,
                color = Color.White,
            )

        assertEquals(
            "Hello",
            ticker.text.text.substring(
                ticker.displayedFocusStart,
                ticker.displayedFocusEndExclusive,
            ),
        )
        assertEquals(
            "\u201CHello,",
            ticker.text.text.substring(ticker.focusStart, ticker.focusEndExclusive),
        )
    }

    @Test
    fun sentenceTickerClampsVisualRangesAtBookEdges() {
        val tokens = listOf(punctuation("("), word("First"), word("last"), punctuation("."))
        val firstWindow =
            requireNotNull(
                resolveRsvpContextWindow(
                    tokens = tokens,
                    frameStartIndex = -5,
                    frameEndExclusive = 2,
                    mode = RsvpContextAssistMode.SENTENCE_TICKER,
                ),
            )
        val lastWindow =
            requireNotNull(
                resolveRsvpContextWindow(
                    tokens = tokens,
                    frameStartIndex = 2,
                    frameEndExclusive = 99,
                    mode = RsvpContextAssistMode.SENTENCE_TICKER,
                ),
            )

        assertEquals(0, firstWindow.focusStartIndex)
        assertEquals(2, firstWindow.focusEndExclusive)
        assertEquals(2, lastWindow.focusStartIndex)
        assertEquals(tokens.size, lastWindow.focusEndExclusive)
    }

    @Test
    fun sentenceTickerHidesTheWholeSourceRangeForASplitDisplayedWord() {
        val tokens =
            listOf(
                word("just"),
                word("make-believe"),
                punctuation(","),
                word("Sky"),
            )
        val ticker =
            buildSentenceTickerContent(
                tokens = tokens,
                window =
                RsvpContextWindow(
                    startIndex = 0,
                    endExclusive = tokens.size,
                    focusStartIndex = 0,
                    focusEndExclusive = 2,
                ),
                displayedTokens = listOf(word("just"), word("make")),
                color = Color.White,
            )

        assertEquals(
            "just make-believe",
            ticker.text.text.substring(ticker.focusStart, ticker.focusEndExclusive),
        )
        assertEquals(
            "just make",
            ticker.text.text.substring(
                ticker.displayedFocusStart,
                ticker.displayedFocusEndExclusive,
            ),
        )
        assertEquals(Color.Transparent, ticker.text.spanStyles.last().item.color)
    }

    @Test
    fun sentenceTickerAlignsASuffixChunkWithinItsOriginalWord() {
        val tokens = listOf(word("make-believe"), punctuation(","), word("Sky"))
        val ticker =
            buildSentenceTickerContent(
                tokens = tokens,
                window =
                RsvpContextWindow(
                    startIndex = 0,
                    endExclusive = tokens.size,
                    focusStartIndex = 0,
                    focusEndExclusive = 1,
                ),
                displayedTokens = listOf(word("-believe")),
                color = Color.White,
            )

        assertEquals(
            "-believe",
            ticker.text.text.substring(
                ticker.displayedFocusStart,
                ticker.displayedFocusEndExclusive,
            ),
        )
        assertTrue(ticker.pivotPosition >= ticker.displayedFocusStart)
    }

    @Test
    fun sentenceTickerDistinguishesRepeatedHyphenChunks() {
        val tokens = listOf(word("ha-ha-ha"), word("continues"))
        val window =
            RsvpContextWindow(
                startIndex = 0,
                endExclusive = tokens.size,
                focusStartIndex = 0,
                focusEndExclusive = 1,
            )
        val first =
            buildSentenceTickerContent(
                tokens = tokens,
                window = window,
                displayedTokens = listOf(word("ha-")),
                displayedSourceStartCharacterOffset = 0,
                displayedSourceEndCharacterOffset = 3,
                color = Color.White,
            )
        val second =
            buildSentenceTickerContent(
                tokens = tokens,
                window = window,
                displayedTokens = listOf(word("ha-")),
                displayedSourceStartCharacterOffset = 3,
                displayedSourceEndCharacterOffset = 6,
                color = Color.White,
            )
        val third =
            buildSentenceTickerContent(
                tokens = tokens,
                window = window,
                displayedTokens = listOf(word("ha")),
                displayedSourceStartCharacterOffset = 6,
                displayedSourceEndCharacterOffset = 8,
                color = Color.White,
            )

        assertEquals(0, first.displayedFocusStart)
        assertEquals(3, second.displayedFocusStart)
        assertEquals(6, third.displayedFocusStart)
    }

    @Test
    fun replayTargetsCurrentClauseThenThePreviousClauseAtItsStart() {
        val tokens =
            listOf(
                word("Read"),
                word("this"),
                punctuation(","),
                word("because", isClauseBoundary = true),
                word("it"),
                word("helps"),
                punctuation("."),
            )

        assertEquals(3, findReplayPhraseStartTokenIndex(tokens, currentTokenIndex = 5))
        assertEquals(0, findReplayPhraseStartTokenIndex(tokens, currentTokenIndex = 3))
    }

    @Test
    fun regressionPacingEasesThenReturnsTowardTheSelectedTempo() {
        val runtime = RsvpRuntimeState()

        registerRsvpRegression(runtime, enabled = true)
        assertEquals(1f + REGRESSION_PACE_STEP, runtime.comprehensionPaceScale, 0.0001f)

        repeat(REGRESSION_RECOVERY_START_PHRASES + 1) {
            recoverRsvpRegressionPace(runtime, enabled = true, endsPhrase = true)
        }
        assertTrue(runtime.comprehensionPaceScale < 1f + REGRESSION_PACE_STEP)
        assertTrue(runtime.comprehensionPaceScale >= 1f)
    }

    private fun word(
        text: String,
        isClauseBoundary: Boolean = false,
    ): Token =
        Token(
            text = text,
            type = TokenType.WORD,
            isClauseBoundary = isClauseBoundary,
        )

    private fun punctuation(text: String): Token = Token(text, TokenType.PUNCTUATION)
}
