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

private fun buildOrpTextContent(tokens: List<Token>): OrpTextContent {
    val state = OrpTextBuildState()
    val wordCount = tokens.count { it.type == TokenType.WORD }
    val fullText =
        buildString {
            tokens.forEach { token ->
                when (token.type) {
                    TokenType.WORD -> appendWord(token, state, this)
                    TokenType.PUNCTUATION -> appendPunctuation(token, state, this)
                    TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> Unit
                }
            }
        }

    return OrpTextContent(
        fullText = fullText,
        firstWordStart = state.firstWordStart,
        firstWordEndExclusive = state.firstWordEndExclusive,
        pivotPosition = resolvePivotPosition(state),
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
    if (state.firstWordStart == INVALID_INDEX) {
        state.firstWordStart = start
        state.firstWordEndExclusive = builder.length
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
    state: OrpTextBuildState,
    builder: StringBuilder,
) {
    val ch = token.text.singleOrNull()
    val isOpening = isOpeningPunctuation(ch, state.needsSpace)
    if (isOpening && state.needsSpace) builder.append(' ')
    builder.append(token.text)
    state.needsSpace = !isOpening
}

private fun isOpeningPunctuation(
    ch: Char?,
    needsSpace: Boolean,
): Boolean =
    when (ch) {
        null -> false
        '"' -> !needsSpace
        in ORP_OPENING_PUNCTUATION -> true
        else -> false
    }

private fun resolvePivotPosition(state: OrpTextBuildState): Int {
    val wordStart = state.firstWordStart
    val wordEndExclusive = state.firstWordEndExclusive
    if (wordStart < 0 || wordEndExclusive <= wordStart) return DEFAULT_PIVOT_INDEX

    val wordEnd = (wordEndExclusive - 1).coerceAtLeast(wordStart)
    val wordLength = (wordEndExclusive - wordStart).coerceAtLeast(1)
    val centerOffset = ((wordLength - 1) / BIAS_SCALE_FACTOR).roundToInt()
    return (wordStart + centerOffset).coerceIn(wordStart, wordEnd)
}
