@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
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
    val rendering =
        rememberOrpRendering(
            fullText = content.fullText,
            typography = typography,
            pivotPosition = content.pivotPosition,
            pivotColor = colors.pivotColor,
        )
    OrpAlignedTextLayout(
        content = content,
        layout = layout,
        colors = colors,
        rendering = rendering,
    )
}

@Composable
private fun rememberOrpRendering(
    fullText: String,
    typography: OrpTypography,
    pivotPosition: Int,
    pivotColor: Color,
): OrpTextRendering {
    val baseStyle = MaterialTheme.typography.displayMedium
    val textStyle =
        remember(typography, baseStyle) {
            baseStyle.copy(
                fontSize = typography.fontSizeSp.sp,
                fontFamily = typography.fontFamily,
                fontWeight = typography.fontWeight,
                letterSpacing = ORP_LETTER_SPACING_SP.sp,
            )
        }
    val annotatedText =
        remember(fullText, pivotPosition, pivotColor) {
            buildOrpAnnotatedText(fullText, pivotPosition, pivotColor)
        }
    val textMeasurer = rememberTextMeasurer()
    return OrpTextRendering(
        annotatedText = annotatedText,
        textStyle = textStyle,
        textMeasurer = textMeasurer,
    )
}

private fun buildOrpAnnotatedText(
    fullText: String,
    pivotPosition: Int,
    pivotColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        append(fullText)
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
