@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun OrpAlignedTextLayout(
    content: OrpTextContent,
    layout: OrpTextLayout,
    colors: OrpColors,
    typography: OrpTypography,
) {
    val density = LocalDensity.current
    val effectiveBias = layout.horizontalBias.safeCoerceIn(HORIZONTAL_BIAS_MIN, HORIZONTAL_BIAS_MAX)
    val textMeasurer = rememberTextMeasurer()
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

    BoxWithConstraints(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ORP_HORIZONTAL_PADDING),
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(MIN_ORP_WIDTH_PX)
        val display =
            remember(content, textStyle, colors, maxWidthPx) {
                resolveOrpDisplay(content, textStyle, colors, textMeasurer, maxWidthPx)
            }
        val measuredWidthPx = display.measured.size.width.toFloat()
        val baseEdgePx = with(density) { ORP_BASE_EDGE.toPx() }
        val extraEdgePx = with(density) { ORP_EXTRA_EDGE.toPx() }
        val bounds =
            calculateOrpBounds(
                maxWidthPx = maxWidthPx,
                effectiveBias = effectiveBias,
                baseEdgePx = baseEdgePx,
                extraEdgePx = extraEdgePx,
                measuredWidthPx = measuredWidthPx,
            )
        val safePivotIndex =
            if (display.content.fullText.isEmpty()) {
                DEFAULT_PIVOT_INDEX
            } else {
                display.content.pivotPosition.safeCoerceIn(0, display.content.fullText.lastIndex)
            }
        val pivotRange =
            calculatePivotRange(
                display.content,
                display.content.fullText.lastIndex,
                safePivotIndex,
            )
        val layoutResult =
            calculateOrpLayout(
                display.content,
                layout,
                display.measured,
                bounds,
                pivotRange,
            )

        Column(modifier = Modifier.fillMaxWidth()) {
            OrpStaticLine(colors.pivotLineColor)
            OrpPointer(layoutResult.guideBias, colors.pivotLineColor)
            Spacer(modifier = Modifier.height(ORP_TEXT_SPACER))
            OrpTextLine(
                display.annotatedText,
                display.textStyle,
                colors.textColor,
                layoutResult.translationX,
            )
            Spacer(modifier = Modifier.height(ORP_TEXT_SPACER))
            OrpPointer(layoutResult.guideBias, colors.pivotLineColor)
            OrpStaticLine(colors.pivotLineColor)
        }
    }
}

private data class OrpDisplay(
    val content: OrpTextContent,
    val annotatedText: AnnotatedString,
    val textStyle: TextStyle,
    val measured: TextLayoutResult,
)

private fun resolveOrpDisplay(
    content: OrpTextContent,
    textStyle: TextStyle,
    colors: OrpColors,
    textMeasurer: TextMeasurer,
    maxWidthPx: Float,
): OrpDisplay {
    val baseAnnotated =
        buildOrpAnnotatedText(
            fullText = content.fullText,
            pivotPosition = content.pivotPosition,
            pivotColor = colors.pivotColor,
            highlightStart = content.highlightStart,
            highlightEndExclusive = content.highlightEndExclusive,
            highlightColor = colors.highlightColor,
        )
    val baseMeasured =
        measureOrpText(
            textMeasurer = textMeasurer,
            text = baseAnnotated,
            textStyle = textStyle,
        )
    val baseWidthPx = baseMeasured.size.width.toFloat()

    if (baseWidthPx <= maxWidthPx) {
        return OrpDisplay(content, baseAnnotated, textStyle, baseMeasured)
    }

    val scale = (maxWidthPx / baseWidthPx).coerceAtMost(1f)
    if (scale >= ORP_MIN_AUTO_FIT_SCALE) {
        val scaledStyle =
            textStyle.copy(
                fontSize = textStyle.fontSize.scaledBy(scale),
                letterSpacing = textStyle.letterSpacing.scaledBy(scale),
            )
        val scaledMeasured =
            measureOrpText(
                textMeasurer = textMeasurer,
                text = baseAnnotated,
                textStyle = scaledStyle,
            )
        return OrpDisplay(content, baseAnnotated, scaledStyle, scaledMeasured)
    }

    val windowed =
        buildWindowedContent(
            content = content,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            maxWidthPx = maxWidthPx,
        )
    val windowedAnnotated =
        buildOrpAnnotatedText(
            fullText = windowed.fullText,
            pivotPosition = windowed.pivotPosition,
            pivotColor = colors.pivotColor,
            highlightStart = windowed.highlightStart,
            highlightEndExclusive = windowed.highlightEndExclusive,
            highlightColor = colors.highlightColor,
        )
    val windowedMeasured =
        measureOrpText(
            textMeasurer = textMeasurer,
            text = windowedAnnotated,
            textStyle = textStyle,
        )
    return OrpDisplay(windowed, windowedAnnotated, textStyle, windowedMeasured)
}

private fun buildWindowedContent(
    content: OrpTextContent,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Float,
): OrpTextContent {
    val fullText = content.fullText
    if (fullText.isEmpty()) return content

    val hasHighlight =
        content.highlightStart >= 0 && content.highlightEndExclusive > content.highlightStart
    val focusStart =
        if (hasHighlight) {
            content.highlightStart.safeCoerceIn(0, fullText.lastIndex)
        } else {
            content.pivotPosition.safeCoerceIn(0, fullText.lastIndex)
        }
    val focusEndExclusive =
        if (hasHighlight) {
            content.highlightEndExclusive.safeCoerceIn(focusStart + 1, fullText.length)
        } else {
            (focusStart + 1).coerceAtMost(fullText.length)
        }
    val window =
        resolveWindowRange(
            fullText = fullText,
            focusStart = focusStart,
            focusEndExclusive = focusEndExclusive,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            maxWidthPx = maxWidthPx,
        )

    val prefixOffset = if (window.start > 0) 1 else 0
    val visibleLength = (window.endExclusive - window.start).coerceAtLeast(1)
    val displayText =
        buildWindowText(
            fullText = fullText,
            start = window.start,
            endExclusive = window.endExclusive,
        )
    val highlightStart =
        if (hasHighlight) {
            (prefixOffset + content.highlightStart - window.start).safeCoerceIn(
                prefixOffset,
                prefixOffset + visibleLength,
            )
        } else {
            INVALID_INDEX
        }
    val highlightEndExclusive =
        if (hasHighlight) {
            (prefixOffset + content.highlightEndExclusive - window.start).safeCoerceIn(
                highlightStart,
                prefixOffset + visibleLength,
            )
        } else {
            INVALID_INDEX
        }
    val pivotPosition =
        if (hasHighlight) {
            val length = (highlightEndExclusive - highlightStart).coerceAtLeast(1)
            val centerOffset = ((length - 1) / BIAS_SCALE_FACTOR).roundToInt()
            (highlightStart + centerOffset).safeCoerceIn(
                prefixOffset,
                prefixOffset + visibleLength - 1,
            )
        } else if (content.pivotPosition in window.start until window.endExclusive) {
            (prefixOffset + content.pivotPosition - window.start).safeCoerceIn(
                prefixOffset,
                prefixOffset + visibleLength - 1,
            )
        } else {
            val centerOffset = ((visibleLength - 1) / BIAS_SCALE_FACTOR).roundToInt()
            (prefixOffset + centerOffset).safeCoerceIn(
                prefixOffset,
                prefixOffset + visibleLength - 1,
            )
        }

    return OrpTextContent(
        fullText = displayText,
        firstWordStart = prefixOffset,
        firstWordEndExclusive = prefixOffset + visibleLength,
        pivotPosition = pivotPosition,
        wordCount = content.wordCount,
        highlightStart = highlightStart,
        highlightEndExclusive = highlightEndExclusive,
    )
}

private data class WindowRange(val start: Int, val endExclusive: Int,)

private fun resolveWindowRange(
    fullText: String,
    focusStart: Int,
    focusEndExclusive: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Float,
): WindowRange {
    val length = fullText.length
    var start = focusStart.safeCoerceIn(0, length - 1)
    var end = focusEndExclusive.safeCoerceIn(start + 1, length)

    fun fits(candidateStart: Int, candidateEnd: Int): Boolean {
        val candidate =
            buildWindowText(
                fullText = fullText,
                start = candidateStart,
                endExclusive = candidateEnd,
            )
        val measured =
            measureOrpText(
                textMeasurer = textMeasurer,
                text = AnnotatedString(candidate),
                textStyle = textStyle,
            )
        return measured.size.width.toFloat() <= maxWidthPx
    }

    while (start < end && !fits(start, end)) {
        if (end - start <= 1) break
        val leftSpace = focusStart - start
        val rightSpace = end - focusEndExclusive
        if (rightSpace >= leftSpace && end - 1 > start) {
            end -= 1
        } else if (start + 1 < end) {
            start += 1
        } else {
            break
        }
    }

    var canLeft = start > 0
    var canRight = end < length
    while (canLeft || canRight) {
        var expanded = false
        if (canLeft && start > 0 && fits(start - 1, end)) {
            start -= 1
            expanded = true
        } else {
            canLeft = false
        }
        if (canRight && end < length && fits(start, end + 1)) {
            end += 1
            expanded = true
        } else {
            canRight = false
        }
        if (!expanded) break
    }

    return WindowRange(start = start, endExclusive = end)
}

private fun buildWindowText(
    fullText: String,
    start: Int,
    endExclusive: Int,
): String {
    val prefix = if (start > 0) ORP_ELLIPSIS.toString() else ""
    val suffix = if (endExclusive < fullText.length) ORP_ELLIPSIS.toString() else ""
    return prefix + fullText.substring(start, endExclusive) + suffix
}

private fun measureOrpText(
    textMeasurer: TextMeasurer,
    text: AnnotatedString,
    textStyle: TextStyle,
): TextLayoutResult =
    textMeasurer.measure(
        text = text,
        style = textStyle,
        overflow = TextOverflow.Clip,
        softWrap = false,
        maxLines = 1,
        constraints = Constraints(maxWidth = Int.MAX_VALUE),
    )

private fun TextUnit.scaledBy(scale: Float): TextUnit =
    if (this == TextUnit.Unspecified) {
        this
    } else {
        (value * scale).sp
    }

private fun Float.safeCoerceIn(minimum: Float, maximum: Float): Float {
    val minBound = minOf(minimum, maximum)
    val maxBound = maxOf(minimum, maximum)
    return when {
        this < minBound -> minBound
        this > maxBound -> maxBound
        else -> this
    }
}

private fun Int.safeCoerceIn(minimum: Int, maximum: Int): Int {
    val minBound = minOf(minimum, maximum)
    val maxBound = maxOf(minimum, maximum)
    return when {
        this < minBound -> minBound
        this > maxBound -> maxBound
        else -> this
    }
}

private fun calculateOrpBounds(
    maxWidthPx: Float,
    effectiveBias: Float,
    baseEdgePx: Float,
    extraEdgePx: Float,
    measuredWidthPx: Float,
): OrpBounds {
    if (!maxWidthPx.isFinite() || maxWidthPx <= ZERO_FLOAT || !measuredWidthPx.isFinite()) {
        val safeWidth = if (maxWidthPx.isFinite() && maxWidthPx > ZERO_FLOAT) {
            maxWidthPx
        } else {
            MIN_ORP_WIDTH_PX
        }
        val midpoint = safeWidth / BIAS_SCALE_FACTOR
        return OrpBounds(
            safeLeftPx = midpoint,
            safeRightPx = safeWidth - midpoint,
            desiredPivotX = midpoint,
            maxTranslationX = ZERO_FLOAT,
            maxWidthPx = safeWidth,
        )
    }
    val rawFraction =
        ((effectiveBias + ONE_FLOAT) / BIAS_SCALE_FACTOR)
            .safeCoerceIn(ORP_BIAS_FRACTION_MIN, ORP_BIAS_FRACTION_MAX)
    val biasFromCenter = rawFraction - ORP_CENTER_FRACTION
    val leftExtraFactor =
        (biasFromCenter / ORP_CENTER_FRACTION)
            .coerceAtLeast(ZERO_FLOAT)
            .safeCoerceIn(ZERO_FLOAT, ONE_FLOAT)
    val rightExtraFactor =
        (-biasFromCenter / ORP_CENTER_FRACTION)
            .coerceAtLeast(ZERO_FLOAT)
            .safeCoerceIn(ZERO_FLOAT, ONE_FLOAT)
    var safeLeftPx = baseEdgePx + (leftExtraFactor * extraEdgePx)
    var safeRightPx = baseEdgePx + (rightExtraFactor * extraEdgePx)
    val edgeTotal = safeLeftPx + safeRightPx
    if (edgeTotal > maxWidthPx && edgeTotal > ZERO_FLOAT) {
        val scale = maxWidthPx / edgeTotal
        safeLeftPx *= scale
        safeRightPx *= scale
    }
    val maxPivotXBefore = maxWidthPx - safeRightPx
    if (maxPivotXBefore < safeLeftPx) {
        val midpoint = maxWidthPx / BIAS_SCALE_FACTOR
        safeLeftPx = midpoint
        safeRightPx = maxWidthPx - midpoint
    }

    var minFraction =
        (safeLeftPx / maxWidthPx)
            .safeCoerceIn(ORP_EDGE_FRACTION_MIN, ORP_EDGE_FRACTION_MAX)
    var maxFraction =
        ONE_FLOAT -
            (safeRightPx / maxWidthPx)
                .safeCoerceIn(ORP_EDGE_FRACTION_MIN, ORP_EDGE_FRACTION_MAX)
    if (minFraction > maxFraction) {
        val midpoint = (minFraction + maxFraction) / BIAS_SCALE_FACTOR
        minFraction = midpoint
        maxFraction = midpoint
    }
    val desiredFraction = rawFraction.safeCoerceIn(minFraction, maxFraction)
    val maxPivotXAfter = maxWidthPx - safeRightPx
    val minPivotX = minOf(safeLeftPx, maxPivotXAfter)
    val maxPivotX = maxOf(safeLeftPx, maxPivotXAfter)
    val desiredPivotX =
        (maxWidthPx * desiredFraction)
            .safeCoerceIn(minPivotX, maxPivotX)
    val maxTranslationX = maxWidthPx - safeRightPx - measuredWidthPx

    return OrpBounds(
        safeLeftPx = safeLeftPx,
        safeRightPx = safeRightPx,
        desiredPivotX = desiredPivotX,
        maxTranslationX = maxTranslationX,
        maxWidthPx = maxWidthPx,
    )
}

private fun calculatePivotRange(
    content: OrpTextContent,
    lastIndex: Int,
    safePivotIndex: Int,
): OrpPivotRange {
    val start = if (content.firstWordStart >= 0) content.firstWordStart else DEFAULT_PIVOT_INDEX
    val rawEnd =
        if (content.firstWordEndExclusive > 0) {
            content.firstWordEndExclusive - 1
        } else {
            lastIndex
        }
    val end = rawEnd.coerceAtLeast(start)
    return OrpPivotRange(start = start, end = end, safePivotIndex = safePivotIndex)
}

private fun calculateOrpLayout(
    content: OrpTextContent,
    layout: OrpTextLayout,
    measured: TextLayoutResult,
    bounds: OrpBounds,
    pivotRange: OrpPivotRange,
): OrpLayoutResult =
    when {
        layout.lockPivot && content.wordCount > ORP_LOCK_PIVOT_WORDS ->
            layoutLockedPivot(pivotRange, measured, bounds, content.fullText)
        bounds.maxTranslationX < bounds.safeLeftPx ->
            layoutWideWord(pivotRange, measured, bounds, content.fullText.lastIndex)
        else ->
            layoutFlexiblePivot(pivotRange, measured, bounds, content.fullText)
    }

private fun layoutLockedPivot(
    pivotRange: OrpPivotRange,
    measured: TextLayoutResult,
    bounds: OrpBounds,
    fullText: String,
): OrpLayoutResult {
    val pivotIndex = pivotRange.safePivotIndex.safeCoerceIn(pivotRange.start, pivotRange.end)
    val lastIndex = fullText.lastIndex.coerceAtLeast(DEFAULT_PIVOT_INDEX)
    val pivotCenter = pivotCenterX(measured, pivotIndex, lastIndex)
    val textLeft = measured.getBoundingBox(DEFAULT_PIVOT_INDEX).left
    val textRight = measured.getBoundingBox(lastIndex).right
    val textCenter = (textLeft + textRight) / BIAS_SCALE_FACTOR
    val chunkingShiftPx = pivotCenter - textCenter
    val minPivotXRaw = bounds.safeLeftPx + pivotCenter
    val maxPivotXRaw = bounds.maxTranslationX + pivotCenter
    val minPivotX = minOf(minPivotXRaw, maxPivotXRaw)
    val maxPivotX = maxOf(minPivotXRaw, maxPivotXRaw)
    val guidePivotX =
        (bounds.desiredPivotX + chunkingShiftPx)
            .safeCoerceIn(minPivotX, maxPivotX)
    val translationX = guidePivotX - pivotCenter
    val alignment = alignPivotToPixel(pivotCenter, translationX)
    return OrpLayoutResult(
        pivotIndex = pivotIndex,
        translationX = alignment.translationX,
        guideBias = guideBias(bounds.maxWidthPx, alignment.pivotX),
    )
}

private fun layoutWideWord(
    pivotRange: OrpPivotRange,
    measured: TextLayoutResult,
    bounds: OrpBounds,
    lastIndex: Int,
): OrpLayoutResult {
    val pivotIndex = pivotRange.safePivotIndex.safeCoerceIn(pivotRange.start, pivotRange.end)
    val measuredWidthPx = measured.size.width.toFloat()
    val translationX = (bounds.maxWidthPx - measuredWidthPx) / BIAS_SCALE_FACTOR
    val pivotCenter =
        pivotCenterX(
            measured,
            pivotIndex,
            lastIndex.coerceAtLeast(DEFAULT_PIVOT_INDEX),
        )
    val alignment = alignPivotToPixel(pivotCenter, translationX)
    return OrpLayoutResult(
        pivotIndex = pivotIndex,
        translationX = alignment.translationX,
        guideBias = guideBias(bounds.maxWidthPx, alignment.pivotX),
    )
}

private fun layoutFlexiblePivot(
    pivotRange: OrpPivotRange,
    measured: TextLayoutResult,
    bounds: OrpBounds,
    fullText: String,
): OrpLayoutResult {
    val lastIndex = fullText.lastIndex.coerceAtLeast(DEFAULT_PIVOT_INDEX)
    val basePivotIndex = pivotRange.safePivotIndex
    val pivotCenter = pivotCenterX(measured, basePivotIndex, lastIndex)
    val minTranslationX = minOf(bounds.safeLeftPx, bounds.maxTranslationX)
    val maxTranslationX = maxOf(bounds.safeLeftPx, bounds.maxTranslationX)
    val translationX =
        (bounds.desiredPivotX - pivotCenter)
            .safeCoerceIn(minTranslationX, maxTranslationX)
    val alignment = alignPivotToPixel(pivotCenter, translationX)
    return OrpLayoutResult(
        pivotIndex = basePivotIndex,
        translationX = alignment.translationX,
        guideBias = guideBias(bounds.maxWidthPx, alignment.pivotX),
    )
}

private fun pivotCenterX(
    measured: TextLayoutResult,
    index: Int,
    lastIndex: Int,
): Float {
    val safeIndex = index.safeCoerceIn(DEFAULT_PIVOT_INDEX, lastIndex)
    val box = measured.getBoundingBox(safeIndex)
    return box.left + (box.width / BIAS_SCALE_FACTOR)
}

private fun guideBias(
    maxWidthPx: Float,
    guidePivotX: Float,
): Float = ((guidePivotX / maxWidthPx) * BIAS_SCALE_FACTOR) - ONE_FLOAT

private fun alignPivotToPixel(
    pivotCenter: Float,
    translationX: Float,
): PivotAlignment {
    val actualPivotX = pivotCenter + translationX
    val roundedPivotX = actualPivotX.roundToInt().toFloat()
    val adjustedTranslation = translationX + (roundedPivotX - actualPivotX)
    return PivotAlignment(pivotX = roundedPivotX, translationX = adjustedTranslation)
}
