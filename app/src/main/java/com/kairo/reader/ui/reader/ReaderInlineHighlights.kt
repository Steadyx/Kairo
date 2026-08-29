package com.kairo.reader.ui.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

internal data class ReaderParagraphVisualContent(val text: AnnotatedString, val highlights: List<ReaderInlineHighlightRange>,)

internal data class ReaderInlineHighlightRange(val key: String, val start: Int, val endExclusive: Int, val color: Color,)

internal fun MutableList<ReaderInlineHighlightRange>.addOrExtendInlineHighlight(
    key: String,
    start: Int,
    endExclusive: Int,
    color: Color,
) {
    if (start >= endExclusive) return
    val existingIndex =
        indexOfLast { range ->
            range.key == key && range.color == color && range.endExclusive == start
        }
    if (existingIndex >= 0) {
        val existing = get(existingIndex)
        set(existingIndex, existing.copy(endExclusive = endExclusive))
    } else {
        add(ReaderInlineHighlightRange(key, start, endExclusive, color))
    }
}

internal fun Modifier.drawReaderInlineHighlights(
    layoutResult: () -> TextLayoutResult?,
    highlights: () -> List<ReaderInlineHighlightRange>,
): Modifier =
    drawBehind {
        val measuredText = layoutResult() ?: return@drawBehind
        val horizontalPadding = HIGHLIGHT_HORIZONTAL_PADDING.toPx()
        val verticalInset = HIGHLIGHT_VERTICAL_INSET.toPx()
        val cornerRadius = CornerRadius(HIGHLIGHT_CORNER_RADIUS.toPx())
        highlights().forEach { highlight ->
            measuredText.resolveHighlightRects(highlight).forEach { rect ->
                val left = (rect.left - horizontalPadding).coerceIn(0f, size.width)
                val right = (rect.right + horizontalPadding).coerceIn(0f, size.width)
                val top = rect.top + verticalInset
                val bottom = rect.bottom - verticalInset
                if (right > left && bottom > top) {
                    drawRoundRect(
                        color = highlight.color,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        cornerRadius = cornerRadius,
                    )
                }
            }
        }
    }

private fun TextLayoutResult.resolveHighlightRects(
    highlight: ReaderInlineHighlightRange,
): List<Rect> {
    val textLength = layoutInput.text.length
    if (textLength == 0) return emptyList()
    val safeStart = highlight.start.coerceIn(0, textLength - 1)
    val safeEnd = highlight.endExclusive.coerceIn(safeStart + 1, textLength)
    val firstLine = getLineForOffset(safeStart)
    val lastLine = getLineForOffset(safeEnd - 1)
    return (firstLine..lastLine).mapNotNull { line ->
        val rangeStart = max(safeStart, getLineStart(line))
        val rangeEnd = min(safeEnd, getLineEnd(line, visibleEnd = false))
        if (rangeStart >= rangeEnd) return@mapNotNull null
        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        for (offset in rangeStart until rangeEnd) {
            val box = getBoundingBox(offset)
            left = min(left, box.left)
            right = max(right, box.right)
        }
        if (!left.isFinite() || !right.isFinite() || right <= left) {
            null
        } else {
            Rect(left, getLineTop(line), right, getLineBottom(line))
        }
    }
}

private val HIGHLIGHT_HORIZONTAL_PADDING = 0.75.dp
private val HIGHLIGHT_VERTICAL_INSET = 1.dp
private val HIGHLIGHT_CORNER_RADIUS = 4.dp
