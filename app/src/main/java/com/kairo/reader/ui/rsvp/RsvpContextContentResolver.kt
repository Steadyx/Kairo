package com.kairo.reader.ui.rsvp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.shouldInsertSpaceBeforeToken

@Composable
internal fun rememberRsvpContextContent(
    context: RsvpUiContext,
    frame: RsvpFrame?,
): RsvpContextContent? {
    if (frame == null) return null
    val tokens = context.state.book.tokens
    val mode = context.state.profile.config.contextAssistMode
    val simplifyPunctuation = false
    if (tokens.isEmpty() || mode == RsvpContextAssistMode.OFF) return null

    val currentTokenIndex = frame.originalTokenIndex.coerceIn(0, tokens.lastIndex)
    val currentToken = tokens[currentTokenIndex]
    val isBoundaryFrame =
        currentToken.type == TokenType.PARAGRAPH_BREAK ||
            currentToken.type == TokenType.PAGE_BREAK
    if (isBoundaryFrame && mode != RsvpContextAssistMode.SENTENCE_TICKER) return null

    val window =
        remember(
            tokens,
            currentTokenIndex,
            frame.displayOriginalStartIndex,
            frame.displayOriginalEndExclusive,
            mode,
        ) {
            resolveRsvpContextWindow(
                tokens = tokens,
                frameStartIndex = frame.displayOriginalStartIndex,
                frameEndExclusive = frame.displayOriginalEndExclusive,
                mode = mode,
            )
        } ?: return null
    val contextColor = MaterialTheme.colorScheme.onBackground
    if (mode == RsvpContextAssistMode.SENTENCE_TICKER) {
        val ticker =
            remember(
                tokens,
                window,
                frame.tokens,
                frame.displayOriginalStartCharacterOffset,
                frame.displayOriginalEndCharacterOffset,
                simplifyPunctuation,
                contextColor,
            ) {
                buildSentenceTickerContent(
                    tokens = tokens,
                    window = window,
                    displayedTokens = frame.tokens,
                    displayedSourceStartCharacterOffset =
                    frame.displayOriginalStartCharacterOffset,
                    displayedSourceEndCharacterOffset =
                    frame.displayOriginalEndCharacterOffset,
                    simplifyPunctuation = simplifyPunctuation,
                    color = contextColor,
                )
            }
        return RsvpContextContent.Ticker(ticker)
    }
    val previousWords =
        if (mode == RsvpContextAssistMode.FULL_CLAUSE) {
            CONTEXT_CLAUSE_PREVIOUS_WORDS
        } else {
            CONTEXT_MINIMAL_PREVIOUS_WORDS
        }
    val upcomingWords =
        if (mode == RsvpContextAssistMode.FULL_CLAUSE) {
            CONTEXT_CLAUSE_UPCOMING_WORDS
        } else {
            0
        }
    val stableWindow = remember(tokens, frame.phraseStartTokenIndex, frame.phraseEndTokenIndexExclusive) {
        resolveStablePeripheralWindow(tokens, frame)
    }
    val peripheralWindow = stableWindow ?: window
    val previous =
        remember(tokens, peripheralWindow, contextColor, previousWords) {
            buildPeripheralContextText(
                tokens = tokens,
                startIndex = peripheralWindow.startIndex,
                endExclusive = peripheralWindow.focusStartIndex,
                maxWords = previousWords,
                takeLast = true,
                color = contextColor,
                nearestAlpha = CONTEXT_PREVIOUS_NEAREST_ALPHA,
                farthestAlpha = CONTEXT_PREVIOUS_FARTHEST_ALPHA,
            )
        }
    val upcoming =
        remember(tokens, peripheralWindow, contextColor, upcomingWords) {
            if (upcomingWords == 0) {
                AnnotatedString("")
            } else {
                buildPeripheralContextText(
                    tokens = tokens,
                    startIndex = peripheralWindow.focusEndExclusive,
                    endExclusive = peripheralWindow.endExclusive,
                    maxWords = upcomingWords,
                    takeLast = false,
                    color = contextColor,
                    nearestAlpha = CONTEXT_UPCOMING_NEAREST_ALPHA,
                    farthestAlpha = CONTEXT_UPCOMING_FARTHEST_ALPHA,
                )
            }
        }
    return RsvpContextContent.Peripheral(
        previous = previous,
        upcoming = upcoming,
    )
}

/** Keep neighbouring thoughts fixed while the active phrase plays at the central focus. */
internal fun resolveStablePeripheralWindow(tokens: List<Token>, frame: RsvpFrame): RsvpContextWindow? {
    val phraseStart = frame.phraseStartTokenIndex ?: return null
    val phraseEnd = frame.phraseEndTokenIndexExclusive ?: return null
    if (phraseStart !in tokens.indices || phraseEnd !in (phraseStart + 1)..tokens.size) return null
    var start = phraseStart
    var previousWords = 0
    while (start > 0 && previousWords < CONTEXT_CLAUSE_PREVIOUS_WORDS) {
        if (tokens[start - 1].isParagraphBoundary()) break
        start--
        if (tokens[start].type == TokenType.WORD) previousWords++
    }
    var end = phraseEnd
    var upcomingWords = 0
    while (end < tokens.size && upcomingWords < CONTEXT_CLAUSE_UPCOMING_WORDS) {
        if (tokens[end].isParagraphBoundary()) break
        if (tokens[end].type == TokenType.WORD) upcomingWords++
        end++
    }
    return RsvpContextWindow(start, end, phraseStart, phraseEnd)
}

internal fun resolveRsvpContextWindow(
    tokens: List<Token>,
    frameStartIndex: Int,
    frameEndExclusive: Int,
    mode: RsvpContextAssistMode,
): RsvpContextWindow? {
    if (tokens.isEmpty() || mode == RsvpContextAssistMode.OFF) return null
    val safeStart = frameStartIndex.coerceIn(0, tokens.lastIndex)
    val focusWordIndex =
        findWordInRange(tokens, safeStart, frameEndExclusive)
            ?: findWordAtOrAfter(tokens, safeStart)
    if (focusWordIndex < 0) return null

    val baseRange =
        when (mode) {
            RsvpContextAssistMode.OFF -> return null
            RsvpContextAssistMode.PREVIOUS_WORDS ->
                resolveNearbyWordRange(
                    tokens = tokens,
                    focusIndex = focusWordIndex,
                    wordsBefore = CONTEXT_PREVIOUS_WORDS,
                    wordsAfter = CONTEXT_UPCOMING_WORDS,
                )
            RsvpContextAssistMode.FULL_CLAUSE ->
                resolveClauseRange(
                    tokens = tokens,
                    focusIndex = focusWordIndex,
                )
            RsvpContextAssistMode.SENTENCE_TICKER ->
                resolveTickerRange(
                    tokens = tokens,
                    focusIndex = focusWordIndex,
                )
        }
    val frameContainsWord =
        focusWordIndex in safeStart until frameEndExclusive.coerceAtMost(tokens.size)
    val focusStart =
        if (mode == RsvpContextAssistMode.SENTENCE_TICKER && frameContainsWord) {
            safeStart
        } else {
            focusWordIndex
        }
    val focusEnd = frameEndExclusive.coerceIn(focusStart + 1, baseRange.last + 1)
    return RsvpContextWindow(
        startIndex = baseRange.first,
        endExclusive = baseRange.last + 1,
        focusStartIndex = focusStart,
        focusEndExclusive = focusEnd,
    )
}

private fun resolveNearbyWordRange(
    tokens: List<Token>,
    focusIndex: Int,
    wordsBefore: Int,
    wordsAfter: Int,
): IntRange {
    var start = focusIndex
    var seenBefore = 0
    while (start > 0 && seenBefore < wordsBefore) {
        val candidate = tokens[start - 1]
        if (candidate.isParagraphBoundary() || candidate.isClausePunctuation()) break
        start -= 1
        if (candidate.type == TokenType.WORD) {
            seenBefore += 1
            if (candidate.isClauseBoundary) break
        }
    }

    var endExclusive = (focusIndex + 1).coerceAtMost(tokens.size)
    var seenAfter = 0
    while (endExclusive < tokens.size && seenAfter < wordsAfter) {
        val candidate = tokens[endExclusive]
        if (candidate.isParagraphBoundary()) break
        if (candidate.type == TokenType.WORD && candidate.isClauseBoundary) break
        endExclusive += 1
        if (candidate.isClausePunctuation()) break
        if (candidate.type == TokenType.WORD) seenAfter += 1
    }
    return start until endExclusive
}

private fun resolveClauseRange(
    tokens: List<Token>,
    focusIndex: Int,
): IntRange {
    var start = focusIndex
    while (start > 0) {
        val candidate = tokens[start - 1]
        if (candidate.isParagraphBoundary() || candidate.isClausePunctuation()) break
        start -= 1
        if (candidate.type == TokenType.WORD && candidate.isClauseBoundary) break
    }

    var endExclusive = (focusIndex + 1).coerceAtMost(tokens.size)
    while (endExclusive < tokens.size) {
        val candidate = tokens[endExclusive]
        if (candidate.isParagraphBoundary()) break
        if (candidate.type == TokenType.WORD && candidate.isClauseBoundary) break
        endExclusive += 1
        if (candidate.isClausePunctuation()) break
    }

    val wordCount = tokens.subList(start, endExclusive).count { it.type == TokenType.WORD }
    return if (wordCount <= CONTEXT_MAX_CLAUSE_WORDS) {
        start until endExclusive
    } else {
        resolveNearbyWordRange(
            tokens = tokens,
            focusIndex = focusIndex,
            wordsBefore = CONTEXT_LONG_CLAUSE_PREVIOUS_WORDS,
            wordsAfter = CONTEXT_LONG_CLAUSE_UPCOMING_WORDS,
        )
    }
}

private fun resolveTickerRange(
    tokens: List<Token>,
    focusIndex: Int,
): IntRange {
    var start = focusIndex
    var previousWords = 0
    while (start > 0) {
        val candidate = tokens[start - 1]
        if (candidate.type == TokenType.WORD && previousWords >= CONTEXT_TICKER_PREVIOUS_WORDS) {
            break
        }
        start -= 1
        if (candidate.type == TokenType.WORD) previousWords += 1
    }

    var endExclusive = (focusIndex + 1).coerceAtMost(tokens.size)
    var upcomingWords = 0
    while (endExclusive < tokens.size) {
        val candidate = tokens[endExclusive]
        if (candidate.type == TokenType.WORD && upcomingWords >= CONTEXT_TICKER_UPCOMING_WORDS) {
            break
        }
        endExclusive += 1
        if (candidate.type == TokenType.WORD) upcomingWords += 1
    }
    return start until endExclusive
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun buildSentenceTickerContent(
    tokens: List<Token>,
    window: RsvpContextWindow,
    color: Color,
    displayedTokens: List<Token>? = null,
    displayedSourceStartCharacterOffset: Int = 0,
    displayedSourceEndCharacterOffset: Int? = null,
    simplifyPunctuation: Boolean = false,
): RsvpSentenceTickerContent {
    val safeStart = window.startIndex.coerceIn(0, tokens.size)
    val safeEnd = window.endExclusive.coerceIn(safeStart, tokens.size)
    val safeFocusStart = window.focusStartIndex.coerceIn(safeStart, safeEnd)
    val safeFocusEnd = window.focusEndExclusive.coerceIn(safeFocusStart, safeEnd)
    var focusStartChar = 0
    var focusEndChar = 0
    var previous: Token? = null
    var renderedIndex = 0
    val tokenStartCharacters = mutableMapOf<Int, Int>()
    val tokenEndCharacters = mutableMapOf<Int, Int>()
    val plainText =
        buildString {
            for (index in safeStart until safeEnd) {
                val token = tokens[index]
                if (token.isParagraphBoundary()) {
                    if (isNotEmpty() && !endsWith(CONTEXT_TICKER_BOUNDARY_GAP)) {
                        append(CONTEXT_TICKER_BOUNDARY_GAP)
                    }
                    previous = null
                    renderedIndex = 0
                    continue
                }
                if (shouldInsertSpaceBeforeToken(token, previous, renderedIndex)) append(' ')
                val tokenStart = length
                tokenStartCharacters[index] = tokenStart
                if (index == safeFocusStart) focusStartChar = tokenStart
                append(token.text)
                tokenEndCharacters[index] = length
                if (index in safeFocusStart until safeFocusEnd) focusEndChar = length
                previous = token
                renderedIndex += 1
            }
        }
    if (plainText.isEmpty()) {
        return RsvpSentenceTickerContent(AnnotatedString(""), 0, 0, 0, 0, 0)
    }
    val sourceFocusTokens = tokens.subList(safeFocusStart, safeFocusEnd)
    val focusContent =
        buildOrpTextContent(
            tokens = displayedTokens ?: sourceFocusTokens,
            simplifyPunctuation = simplifyPunctuation,
        )
    val lastFocusTokenIndex = (safeFocusEnd - 1).coerceAtLeast(safeFocusStart)
    val firstTokenStart = tokenStartCharacters[safeFocusStart] ?: focusStartChar
    val firstTokenEnd = tokenEndCharacters[safeFocusStart] ?: firstTokenStart
    val lastTokenStart = tokenStartCharacters[lastFocusTokenIndex] ?: firstTokenStart
    val lastTokenEnd = tokenEndCharacters[lastFocusTokenIndex] ?: focusEndChar
    val sourceSliceStart =
        (firstTokenStart + displayedSourceStartCharacterOffset)
            .coerceIn(firstTokenStart, firstTokenEnd)
    val sourceSliceEnd =
        (
            lastTokenStart +
                (displayedSourceEndCharacterOffset ?: (lastTokenEnd - lastTokenStart))
            )
            .coerceIn(sourceSliceStart, lastTokenEnd)
    val sourceFocusText = plainText.substring(sourceSliceStart, sourceSliceEnd)
    val alignment =
        resolveContextTickerFocusAlignment(
            sourceText = sourceFocusText,
            displayedText = focusContent.fullText,
            displayedPivot = focusContent.pivotPosition,
        )
    val displayedFocusStart = sourceSliceStart + alignment.startOffset
    val displayedFocusEndExclusive = sourceSliceStart + alignment.endExclusiveOffset
    val pivotPosition =
        (sourceSliceStart + alignment.pivotOffset)
            .coerceIn(focusStartChar, (focusEndChar - 1).coerceAtLeast(focusStartChar))
    val annotated =
        buildAnnotatedString {
            append(plainText)
            addStyle(
                SpanStyle(color = color.copy(alpha = CONTEXT_TICKER_ALPHA)),
                0,
                length,
            )
            if (focusEndChar > focusStartChar) {
                addStyle(
                    SpanStyle(color = Color.Transparent),
                    focusStartChar,
                    focusEndChar,
                )
            }
        }
    return RsvpSentenceTickerContent(
        text = annotated,
        pivotPosition = pivotPosition,
        focusStart = focusStartChar,
        focusEndExclusive = focusEndChar,
        displayedFocusStart = displayedFocusStart,
        displayedFocusEndExclusive = displayedFocusEndExclusive,
    )
}

internal fun buildPeripheralContextText(
    tokens: List<Token>,
    startIndex: Int,
    endExclusive: Int,
    maxWords: Int,
    takeLast: Boolean,
    color: Color,
    nearestAlpha: Float,
    farthestAlpha: Float,
): AnnotatedString {
    if (tokens.isEmpty() || maxWords <= 0) return AnnotatedString("")
    val safeStart = startIndex.coerceIn(0, tokens.size)
    val safeEnd = endExclusive.coerceIn(safeStart, tokens.size)
    val wordIndices =
        (safeStart until safeEnd).filter { index -> tokens[index].type == TokenType.WORD }
    if (wordIndices.isEmpty()) return AnnotatedString("")
    val selectedWords =
        if (takeLast) {
            wordIndices.takeLast(maxWords)
        } else {
            wordIndices.take(maxWords)
        }
    val fragmentStart = if (takeLast) selectedWords.first() else safeStart
    val fragmentEnd =
        if (takeLast || selectedWords.size == wordIndices.size) {
            safeEnd
        } else {
            wordIndices[selectedWords.size]
        }

    return buildAnnotatedString {
        var previous: Token? = null
        var renderedIndex = 0
        var wordOrdinal = -1
        for (index in fragmentStart until fragmentEnd) {
            val token = tokens[index]
            if (token.isParagraphBoundary()) continue
            if (shouldInsertSpaceBeforeToken(token, previous, renderedIndex)) append(' ')
            if (token.type == TokenType.WORD) wordOrdinal += 1
            val start = length
            append(token.text)
            val end = length
            val ordinal = wordOrdinal.coerceAtLeast(0)
            val progress =
                if (selectedWords.size <= 1) {
                    1f
                } else {
                    ordinal.toFloat() / (selectedWords.size - 1).toFloat()
                }
            val proximity = if (takeLast) progress else 1f - progress
            val alpha =
                (farthestAlpha + ((nearestAlpha - farthestAlpha) * proximity))
                    .coerceIn(0f, 1f)
            if (end > start) {
                addStyle(
                    SpanStyle(color = color.copy(alpha = alpha)),
                    start,
                    end,
                )
            }
            previous = token
            renderedIndex += 1
        }
    }
}

private fun findWordInRange(
    tokens: List<Token>,
    startIndex: Int,
    endExclusive: Int,
): Int? {
    val safeEnd = endExclusive.coerceIn(startIndex + 1, tokens.size)
    for (index in startIndex until safeEnd) {
        if (tokens[index].type == TokenType.WORD) return index
    }
    return null
}

private fun findWordAtOrAfter(tokens: List<Token>, startIndex: Int): Int {
    for (index in startIndex.coerceAtLeast(0) until tokens.size) {
        if (tokens[index].type == TokenType.WORD) return index
    }
    return -1
}

private fun Token.isParagraphBoundary(): Boolean =
    type == TokenType.PARAGRAPH_BREAK || type == TokenType.PAGE_BREAK

private fun Token.isClausePunctuation(): Boolean =
    type == TokenType.PUNCTUATION && text.any { it in CONTEXT_BOUNDARY_PUNCTUATION }

internal fun stableContextCueFontSizeSp(fontSizeSp: Float): Float =
    fontSizeSp.coerceAtLeast(0f) * CONTEXT_CUE_FONT_SCALE

internal fun resolveContextTickerFocusAlignment(
    sourceText: String,
    displayedText: String,
    displayedPivot: Int,
): ContextTickerFocusAlignment {
    if (sourceText.isEmpty()) return ContextTickerFocusAlignment(0, 0, 0)

    val safeDisplayedPivot =
        displayedPivot.coerceIn(0, (displayedText.length - 1).coerceAtLeast(0))
    val exactStart = sourceText.indexOf(displayedText).takeIf { displayedText.isNotEmpty() }
    if (exactStart != null && exactStart >= 0) {
        return ContextTickerFocusAlignment(
            startOffset = exactStart,
            endExclusiveOffset = exactStart + displayedText.length,
            pivotOffset = exactStart + safeDisplayedPivot,
        )
    }

    val sourceCharacterIndices = sourceText.indices.filterNot { sourceText[it].isWhitespace() }
    val compactSource = sourceCharacterIndices.joinToString(separator = "") { sourceText[it].toString() }
    val displayedCharacterIndices = displayedText.indices.filterNot { displayedText[it].isWhitespace() }
    val compactDisplayed =
        displayedCharacterIndices.joinToString(separator = "") { displayedText[it].toString() }
    val compactStart =
        compactSource.indexOf(compactDisplayed).takeIf { compactDisplayed.isNotEmpty() }
    if (compactStart != null && compactStart >= 0) {
        val compactEnd = compactStart + compactDisplayed.length - 1
        val pivotCharacterOrdinal =
            displayedCharacterIndices
                .indexOfLast { it <= safeDisplayedPivot }
                .coerceAtLeast(0)
                .coerceAtMost(compactDisplayed.lastIndex)
        return ContextTickerFocusAlignment(
            startOffset = sourceCharacterIndices[compactStart],
            endExclusiveOffset = sourceCharacterIndices[compactEnd] + 1,
            pivotOffset = sourceCharacterIndices[compactStart + pivotCharacterOrdinal],
        )
    }

    // Fail closed: the complete source range is already transparent, so an unexpected formatting
    // mismatch must reserve that same range rather than expose a duplicate ticker fragment.
    val fallbackPivot =
        sourceText.indices
            .filter { sourceText[it].isLetterOrDigit() }
            .let { wordIndices -> wordIndices.getOrNull(wordIndices.size / 2) }
            ?: (sourceText.length / 2).coerceAtMost(sourceText.lastIndex)
    return ContextTickerFocusAlignment(
        startOffset = 0,
        endExclusiveOffset = sourceText.length,
        pivotOffset = fallbackPivot,
    )
}

internal fun resolveContextEnvelopeFrameRange(
    frameIndex: Int,
    frameCount: Int,
    blockSize: Int,
): IntRange {
    if (frameCount <= 0) return IntRange.EMPTY
    val safeBlockSize = blockSize.coerceAtLeast(1)
    val safeFrameIndex = frameIndex.coerceIn(0, frameCount - 1)
    val blockStart = (safeFrameIndex / safeBlockSize) * safeBlockSize
    val endExclusive = (blockStart + (safeBlockSize * 2)).coerceAtMost(frameCount)
    return blockStart until endExclusive
}

internal fun resolveContextCueSlots(
    availableWidth: Dp,
    focusLeftReserve: Dp,
    focusRightReserve: Dp,
    horizontalBias: Float,
    minimumCueWidth: Dp,
    cueInnerPadding: Dp,
): ContextCueSlots {
    val safeWidth = availableWidth.coerceAtLeast(0.dp)
    val safeBias = horizontalBias.coerceIn(HORIZONTAL_BIAS_MIN, HORIZONTAL_BIAS_MAX)
    val safeCuePadding = cueInnerPadding.coerceAtLeast(0.dp)
    val guideFraction =
        ((safeBias + ONE_FLOAT) / BIAS_SCALE_FACTOR)
            .coerceIn(ORP_BIAS_FRACTION_MIN, ORP_BIAS_FRACTION_MAX)
    val guidePosition = safeWidth * guideFraction
    val safeLeftReserve = focusLeftReserve.coerceAtLeast(0.dp)
    val safeRightReserve = focusRightReserve.coerceAtLeast(0.dp)
    val previousWidth =
        (guidePosition - safeLeftReserve - safeCuePadding).coerceIn(0.dp, safeWidth)
    val upcomingStart =
        (guidePosition + safeRightReserve + safeCuePadding).coerceIn(0.dp, safeWidth)
    val upcomingWidth = (safeWidth - upcomingStart).coerceAtLeast(0.dp)
    val visibleFocusGap = (safeWidth - previousWidth - upcomingWidth).coerceAtLeast(0.dp)

    return ContextCueSlots(
        previousWidth = previousWidth,
        focusGap = visibleFocusGap,
        upcomingWidth = upcomingWidth,
        hasPreviousRoom = previousWidth >= minimumCueWidth,
        hasUpcomingRoom = upcomingWidth >= minimumCueWidth,
    )
}

internal const val CONTEXT_PREVIOUS_WORDS = 6
internal const val CONTEXT_UPCOMING_WORDS = 3
internal const val CONTEXT_MAX_CLAUSE_WORDS = 18
internal const val CONTEXT_LONG_CLAUSE_PREVIOUS_WORDS = 10
internal const val CONTEXT_LONG_CLAUSE_UPCOMING_WORDS = 7
internal const val CONTEXT_MINIMAL_PREVIOUS_WORDS = 1
internal const val CONTEXT_CLAUSE_PREVIOUS_WORDS = 1
internal const val CONTEXT_CLAUSE_UPCOMING_WORDS = 1
internal const val CONTEXT_ENVELOPE_BLOCK_FRAMES = 6
internal const val CONTEXT_ENVELOPE_ANIMATION_MS = 180
internal const val CONTEXT_PREVIOUS_NEAREST_ALPHA = 0.34f
internal const val CONTEXT_PREVIOUS_FARTHEST_ALPHA = 0.34f
internal const val CONTEXT_UPCOMING_NEAREST_ALPHA = 0.34f
internal const val CONTEXT_UPCOMING_FARTHEST_ALPHA = 0.34f
internal const val CONTEXT_CUE_FONT_SCALE = 1f
internal const val CONTEXT_TICKER_ALPHA = 0.30f
internal const val CONTEXT_TICKER_EDGE_FADE_FRACTION = 0.08f
internal const val CONTEXT_TICKER_MOTION_DURATION_FRACTION = 0.38
internal const val CONTEXT_TICKER_MOTION_MIN_MS = 24
internal const val CONTEXT_TICKER_MOTION_MAX_MS = 72
internal const val CONTEXT_TICKER_FALLBACK_FRAME_MS = 150L
internal const val CONTEXT_TICKER_PREVIOUS_WORDS = 24
internal const val CONTEXT_TICKER_UPCOMING_WORDS = 24
internal const val CONTEXT_TICKER_BOUNDARY_GAP = "   "
internal const val CONTEXT_BASELINE_SAMPLE = "Ag"
internal val CONTEXT_FOCUS_SIDE_PADDING = 18.dp
internal val CONTEXT_MIN_FOCUS_SIDE_RESERVE = 48.dp
internal val CONTEXT_MIN_CUE_WIDTH = 48.dp
internal val CONTEXT_CUE_INNER_PADDING = 8.dp
internal val CONTEXT_BOUNDARY_PUNCTUATION =
    setOf('.', ',', ';', ':', '!', '?', '\u2026', '\u2014', '\u2013')
