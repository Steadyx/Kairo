@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import kotlin.math.roundToInt

@Composable
internal fun OrpAlignedText(
    tokens: List<Token>,
    typography: OrpTypography,
    colors: OrpColors,
    layout: OrpTextLayout,
) {
    val content = remember(tokens) { buildOrpTextContent(tokens) }
    OrpAlignedTextLayout(
        content = content,
        layout = layout,
        colors = colors,
        typography = typography,
    )
}

internal fun buildOrpAnnotatedText(
    fullText: String,
    pivotPosition: Int,
    pivotColor: Color,
    highlightStart: Int,
    highlightEndExclusive: Int,
    highlightColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        append(fullText)
        if (highlightStart >= 0 && highlightEndExclusive > highlightStart) {
            val safeStart = highlightStart.coerceIn(0, fullText.length)
            val safeEnd = highlightEndExclusive.coerceIn(safeStart, fullText.length)
            if (safeEnd > safeStart) {
                addStyle(
                    style = SpanStyle(color = highlightColor),
                    start = safeStart,
                    end = safeEnd,
                )
            }
        }
        if (fullText.isNotEmpty()) {
            val safeIndex = pivotPosition.coerceIn(0, fullText.lastIndex)
            addStyle(
                style = SpanStyle(color = pivotColor),
                start = safeIndex,
                end = (safeIndex + 1).coerceAtMost(fullText.length),
            )
        }
    }

internal fun buildOrpTextContent(
    tokens: List<Token>,
): OrpTextContent {
    val state = OrpTextBuildState()
    val wordCount = tokens.count { it.type == TokenType.WORD }
    val fullText =
        buildString {
            tokens.forEachIndexed { index, token ->
                val nextToken = tokens.getOrNull(index + 1)
                when (token.type) {
                    TokenType.WORD -> appendWord(token, state, this)
                    TokenType.PUNCTUATION -> appendPunctuation(token, nextToken, state, this)
                    TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> Unit
                }
            }
        }

    val pivotPosition =
        resolvePivotPosition(
            state = state,
            fullText = fullText,
            wordCount = wordCount,
        )
    return OrpTextContent(
        fullText = fullText,
        firstWordStart = state.firstWordStart,
        firstWordEndExclusive = state.firstWordEndExclusive,
        pivotPosition = pivotPosition,
        wordCount = wordCount,
        highlightStart = state.highlightStart,
        highlightEndExclusive = state.highlightEndExclusive,
    )
}

private fun appendWord(
    token: Token,
    state: OrpTextBuildState,
    builder: StringBuilder,
) {
    if (state.needsSpace) builder.append(' ')
    val start = builder.length
    builder.append(token.text)
    val endExclusive = builder.length
    if (state.firstWordStart == INVALID_INDEX) {
        state.firstWordStart = start
        state.firstWordEndExclusive = endExclusive
        val highlightStart = token.highlightStart
        val highlightEndExclusive = token.highlightEndExclusive
        if (highlightStart != null &&
            highlightEndExclusive != null &&
            highlightEndExclusive > highlightStart
        ) {
            val safeStart = (start + highlightStart).coerceIn(start, builder.length)
            val safeEnd = (start + highlightEndExclusive).coerceIn(safeStart, builder.length)
            if (safeEnd > safeStart) {
                state.highlightStart = safeStart
                state.highlightEndExclusive = safeEnd
            }
        }
    }
    state.needsSpace = true
}

private fun appendPunctuation(
    token: Token,
    nextToken: Token?,
    state: OrpTextBuildState,
    builder: StringBuilder,
) {
    val ch = token.text.singleOrNull()
    // For straight quotes, derive opening/closing from the immediately preceding rendered char.
    val isOpening = isCurrencyPrefixPunctuation(ch, nextToken) || isOpeningPunctuation(ch, builder)

    // Opening punctuation gets a space before if needed, no space after
    // Closing punctuation attaches directly to previous content
    if (isOpening && state.needsSpace) builder.append(' ')
    builder.append(token.text)
    // After opening punctuation, no space needed before next word
    // After closing punctuation, space needed before next word
    state.needsSpace = !isOpening
}

private fun isCurrencyPrefixPunctuation(
    ch: Char?,
    nextToken: Token?,
): Boolean {
    if (ch !in ORP_CURRENCY_PREFIX_PUNCTUATION) return false
    val nextWordText = nextToken?.takeIf { it.type == TokenType.WORD }?.text ?: return false
    return ORP_CURRENCY_NUMERIC_WORD_REGEX.matches(nextWordText)
}

private fun isOpeningPunctuation(
    ch: Char?,
    builder: StringBuilder,
): Boolean =
    when (ch) {
        null -> false
        // Curly opening quotes are always opening
        in ORP_OPENING_PUNCTUATION -> true
        // Straight quotes: opening unless they follow a word or strong closer.
        '"' ->
            builder.lastNonWhitespaceChar()?.let { previous ->
                !previous.isLetterOrDigit() && previous !in STRAIGHT_QUOTE_CLOSING_PRECEDERS
            } ?: true
        else -> false
    }

private fun StringBuilder.lastNonWhitespaceChar(): Char? {
    for (index in length - 1 downTo 0) {
        val ch = this[index]
        if (!ch.isWhitespace()) return ch
    }
    return null
}

private val STRAIGHT_QUOTE_CLOSING_PRECEDERS =
    setOf('.', '!', '?', ')', ']', '}', '\u201D', '\u2019', '"')
private val ORP_CURRENCY_PREFIX_PUNCTUATION = setOf('$', '€', '£', '¥')
private val ORP_CURRENCY_NUMERIC_WORD_REGEX = Regex("""\d+(?:[.,]\d+)*""")

private fun resolvePivotPosition(
    state: OrpTextBuildState,
    fullText: String,
    wordCount: Int,
): Int {
    val wordStart = state.firstWordStart
    val wordEndExclusive = state.firstWordEndExclusive
    if (wordStart < 0 || wordEndExclusive <= wordStart) return DEFAULT_PIVOT_INDEX

    // For multi-word phrases, always place the pivot at the true center
    // of the text so it stays aligned with the centered layout and static guide.
    if (wordCount > 1 && fullText.isNotEmpty()) {
        val textStart = fullText.indexOfFirst { it.isLetterOrDigit() }
        val textEnd = fullText.indexOfLast { it.isLetterOrDigit() }
        if (textStart >= 0 && textEnd >= textStart) {
            val centerOffset = ((textEnd - textStart) / BIAS_SCALE_FACTOR).toInt()
            val centerIndex = (textStart + centerOffset).coerceIn(textStart, textEnd)
            return nearestWordCharacterIndex(
                text = fullText,
                targetIndex = centerIndex,
                rangeStart = textStart,
                rangeEnd = textEnd,
            )
        }
        val fallbackOffset = ((fullText.length - 1) / BIAS_SCALE_FACTOR).toInt()
        return fallbackOffset.coerceIn(0, fullText.lastIndex)
    }

    // For single words, use the traditional first-word-based pivot
    val wordEnd = (wordEndExclusive - 1).coerceAtLeast(wordStart)
    val wordLength = (wordEndExclusive - wordStart).coerceAtLeast(1)
    val centerOffset = ((wordLength - 1) / BIAS_SCALE_FACTOR).roundToInt()
    return (wordStart + centerOffset).coerceIn(wordStart, wordEnd)
}

private fun nearestWordCharacterIndex(
    text: String,
    targetIndex: Int,
    rangeStart: Int,
    rangeEnd: Int,
): Int {
    if (text.isEmpty()) return DEFAULT_PIVOT_INDEX
    val safeStart = rangeStart.coerceIn(0, text.lastIndex)
    val safeEnd = rangeEnd.coerceIn(safeStart, text.lastIndex)
    val safeTarget = targetIndex.coerceIn(safeStart, safeEnd)
    if (text[safeTarget].isLetterOrDigit()) return safeTarget

    val maxOffset = safeEnd - safeStart
    for (offset in 1..maxOffset) {
        val left = safeTarget - offset
        if (left >= safeStart && text[left].isLetterOrDigit()) return left

        val right = safeTarget + offset
        if (right <= safeEnd && text[right].isLetterOrDigit()) return right
    }
    return safeTarget
}
