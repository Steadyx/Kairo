package com.kairo.reader.ui.rsvp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.shouldInsertSpaceBeforeToken

@Composable
internal fun rememberRsvpReadingContext(context: RsvpUiContext, frame: RsvpFrame): RsvpReadingContext? {
    val tokens = context.state.book.tokens
    val mode = context.state.profile.config.contextAssistMode
    val color = MaterialTheme.colorScheme.onBackground
    return remember(tokens, frame, mode, color) {
        if (mode == RsvpContextAssistMode.OFF) null else buildRsvpReadingContext(tokens, frame, color, mode)
    }
}

/** Inline context uses source positions so split words cannot expose their unread remainder. */
internal fun buildRsvpReadingContext(
    tokens: List<Token>,
    frame: RsvpFrame,
    color: Color,
    mode: RsvpContextAssistMode = RsvpContextAssistMode.PREVIOUS_WORDS,
): RsvpReadingContext? {
    if (mode == RsvpContextAssistMode.OFF || tokens.isEmpty() || frame.tokens.none { it.type == TokenType.WORD }) return null
    val fallback = resolveRsvpContextWindow(
        tokens,
        frame.displayOriginalStartIndex,
        frame.displayOriginalEndExclusive,
        if (mode == RsvpContextAssistMode.SENTENCE_TICKER) mode else RsvpContextAssistMode.FULL_CLAUSE,
    ) ?: return null
    val phraseStart = if (mode != RsvpContextAssistMode.PREVIOUS_WORDS) {
        fallback.startIndex
    } else {
        frame.phraseStartTokenIndex?.takeIf { it in 0..fallback.focusStartIndex } ?: fallback.startIndex
    }
    val phraseEnd = if (mode != RsvpContextAssistMode.PREVIOUS_WORDS) {
        fallback.endExclusive
    } else {
        frame.phraseEndTokenIndexExclusive?.takeIf { it in fallback.focusEndExclusive..tokens.size } ?: fallback.endExclusive
    }
    val previousEnd = frame.displayOriginalStartIndex.coerceIn(phraseStart, phraseEnd)
    val maxWords = if (mode == RsvpContextAssistMode.PREVIOUS_WORDS) INLINE_CONTEXT_WORDS else INLINE_SURROUNDING_WORDS
    val cues = inlineCueCandidates(tokens, phraseStart, previousEnd, maxWords, true, color)
    val following = if (mode != RsvpContextAssistMode.PREVIOUS_WORDS) {
        inlineCueCandidates(tokens, frame.displayOriginalEndExclusive.coerceIn(phraseStart, phraseEnd), phraseEnd, maxWords, false, color)
    } else {
        emptyList()
    }
    val phrase = buildSentenceTickerContent(
        tokens = tokens,
        window = RsvpContextWindow(phraseStart, phraseEnd, fallback.focusStartIndex, fallback.focusEndExclusive),
        color = color,
        displayedTokens = frame.tokens,
        displayedSourceStartCharacterOffset = frame.displayOriginalStartCharacterOffset,
        displayedSourceEndCharacterOffset = frame.displayOriginalEndCharacterOffset,
    ).text
    return RsvpReadingContext(cues, phrase, following)
}

private fun inlineCueCandidates(
    tokens: List<Token>,
    start: Int,
    end: Int,
    maxWords: Int,
    preceding: Boolean,
    color: Color,
): List<AnnotatedString> = (maxWords downTo 1).map { count ->
    buildPeripheralContextText(tokens, start, end, count, preceding, color, INLINE_CONTEXT_NEAREST_ALPHA, INLINE_CONTEXT_FARTHEST_ALPHA)
}.filter { it.isNotBlank() }.distinct()

private const val INLINE_CONTEXT_WORDS = 2
private const val INLINE_SURROUNDING_WORDS = 8
private const val INLINE_CONTEXT_NEAREST_ALPHA = 0.28f
private const val INLINE_CONTEXT_FARTHEST_ALPHA = 0.18f

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
    while (start > 0 && !tokens[start].isClauseBoundary) {
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
        if (candidate.isParagraphBoundary()) break
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
        if (candidate.isParagraphBoundary()) break
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
                    SpanStyle(color = color.copy(alpha = CONTEXT_TICKER_FOCUS_ALPHA)),
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
    (fontSizeSp * CONTEXT_CUE_FONT_SCALE).coerceIn(CONTEXT_CUE_MIN_SP, CONTEXT_CUE_MAX_SP)

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

internal const val CONTEXT_PREVIOUS_WORDS = 6
internal const val CONTEXT_UPCOMING_WORDS = 3
internal const val CONTEXT_MAX_CLAUSE_WORDS = 18
internal const val CONTEXT_LONG_CLAUSE_PREVIOUS_WORDS = 10
internal const val CONTEXT_LONG_CLAUSE_UPCOMING_WORDS = 7
internal const val CONTEXT_CUE_FONT_SCALE = 0.45f
internal const val CONTEXT_CUE_MIN_SP = 14f
internal const val CONTEXT_CUE_MAX_SP = 22f
internal const val CONTEXT_TICKER_FOCUS_ALPHA = 1f
internal const val CONTEXT_TICKER_ALPHA = 0.65f
internal const val CONTEXT_TICKER_PREVIOUS_WORDS = 24
internal const val CONTEXT_TICKER_UPCOMING_WORDS = 24
internal const val CONTEXT_TICKER_BOUNDARY_GAP = "   "
internal val CONTEXT_BOUNDARY_PUNCTUATION =
    setOf('.', ',', ';', ':', '!', '?', '\u2026', '\u2014', '\u2013')
