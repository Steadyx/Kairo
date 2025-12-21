@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

@Composable
internal fun OrpAlignedTextLayout(
    content: OrpTextContent,
    layout: OrpTextLayout,
    colors: OrpColors,
    rendering: OrpTextRendering
) {
    val density = LocalDensity.current
    val effectiveBias = layout.horizontalBias.coerceIn(HORIZONTAL_BIAS_MIN, HORIZONTAL_BIAS_MAX)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ORP_HORIZONTAL_PADDING)
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(MIN_ORP_WIDTH_PX)
        val measured = remember(rendering.annotatedText, rendering.textStyle) {
            rendering.textMeasurer.measure(
                text = rendering.annotatedText,
                style = rendering.textStyle,
                overflow = TextOverflow.Clip,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = Int.MAX_VALUE)
            )
        }
        val measuredWidthPx = measured.size.width.toFloat()
        val baseEdgePx = with(density) { ORP_BASE_EDGE.toPx() }
        val extraEdgePx = with(density) { ORP_EXTRA_EDGE.toPx() }
        val bounds = calculateOrpBounds(
            maxWidthPx = maxWidthPx,
            effectiveBias = effectiveBias,
            baseEdgePx = baseEdgePx,
            extraEdgePx = extraEdgePx,
            measuredWidthPx = measuredWidthPx
        )
        val safePivotIndex = if (content.fullText.isEmpty()) {
            DEFAULT_PIVOT_INDEX
        } else {
            content.pivotPosition.coerceIn(0, content.fullText.lastIndex)
        }
        val pivotRange = calculatePivotRange(content, content.fullText.lastIndex, safePivotIndex)
        val layoutResult = calculateOrpLayout(content, layout, measured, bounds, pivotRange)

        Column(modifier = Modifier.fillMaxWidth()) {
            OrpStaticLine(colors.pivotLineColor)
            OrpPointer(layoutResult.guideBias, colors.pivotLineColor)
            Spacer(modifier = Modifier.height(ORP_TEXT_SPACER))
            OrpTextLine(rendering, colors.textColor, layoutResult.translationX)
            Spacer(modifier = Modifier.height(ORP_TEXT_SPACER))
            OrpPointer(layoutResult.guideBias, colors.pivotLineColor)
            OrpStaticLine(colors.pivotLineColor)
        }
    }
}

private fun calculateOrpBounds(
    maxWidthPx: Float,
    effectiveBias: Float,
    baseEdgePx: Float,
    extraEdgePx: Float,
    measuredWidthPx: Float
): OrpBounds {
    val rawFraction = ((effectiveBias + ONE_FLOAT) / BIAS_SCALE_FACTOR)
        .coerceIn(ORP_BIAS_FRACTION_MIN, ORP_BIAS_FRACTION_MAX)
    val biasFromCenter = rawFraction - ORP_CENTER_FRACTION
    val leftExtraFactor = (biasFromCenter / ORP_CENTER_FRACTION)
        .coerceAtLeast(ZERO_FLOAT)
        .coerceIn(ZERO_FLOAT, ONE_FLOAT)
    val rightExtraFactor = (-biasFromCenter / ORP_CENTER_FRACTION)
        .coerceAtLeast(ZERO_FLOAT)
        .coerceIn(ZERO_FLOAT, ONE_FLOAT)
    var safeLeftPx = baseEdgePx + (leftExtraFactor * extraEdgePx)
    var safeRightPx = baseEdgePx + (rightExtraFactor * extraEdgePx)
    val edgeTotal = safeLeftPx + safeRightPx
    if (edgeTotal > maxWidthPx && edgeTotal > ZERO_FLOAT) {
        val scale = maxWidthPx / edgeTotal
        safeLeftPx *= scale
        safeRightPx *= scale
    }
    val maxPivotXRaw = maxWidthPx - safeRightPx
    if (maxPivotXRaw < safeLeftPx) {
        val midpoint = maxWidthPx / BIAS_SCALE_FACTOR
        safeLeftPx = midpoint
        safeRightPx = maxWidthPx - midpoint
    }

    var minFraction = (safeLeftPx / maxWidthPx)
        .coerceIn(ORP_EDGE_FRACTION_MIN, ORP_EDGE_FRACTION_MAX)
    var maxFraction = ONE_FLOAT - (safeRightPx / maxWidthPx)
        .coerceIn(ORP_EDGE_FRACTION_MIN, ORP_EDGE_FRACTION_MAX)
    if (minFraction > maxFraction) {
        val midpoint = (minFraction + maxFraction) / BIAS_SCALE_FACTOR
        minFraction = midpoint
        maxFraction = midpoint
    }
    val desiredFraction = rawFraction.coerceIn(minFraction, maxFraction)
    val maxPivotX = maxWidthPx - safeRightPx
    val desiredPivotX = (maxWidthPx * desiredFraction)
        .coerceIn(safeLeftPx, maxPivotX)
    val maxTranslationX = maxWidthPx - safeRightPx - measuredWidthPx

    return OrpBounds(
        safeLeftPx = safeLeftPx,
        safeRightPx = safeRightPx,
        desiredPivotX = desiredPivotX,
        maxTranslationX = maxTranslationX,
        maxWidthPx = maxWidthPx
    )
}

private fun calculatePivotRange(
    content: OrpTextContent,
    lastIndex: Int,
    safePivotIndex: Int
): OrpPivotRange {
    val start = if (content.firstWordStart >= 0) content.firstWordStart else DEFAULT_PIVOT_INDEX
    val rawEnd = if (content.firstWordEndExclusive > 0) {
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
    pivotRange: OrpPivotRange
): OrpLayoutResult {
    return when {
        layout.lockPivot && content.wordCount > ORP_LOCK_PIVOT_WORDS ->
            layoutLockedPivot(pivotRange, measured, bounds, content.fullText)
        bounds.maxTranslationX < bounds.safeLeftPx ->
            layoutWideWord(pivotRange, measured, bounds, content.fullText.lastIndex)
        else ->
            layoutFlexiblePivot(pivotRange, measured, bounds, content.fullText)
    }
}

private fun layoutLockedPivot(
    pivotRange: OrpPivotRange,
    measured: TextLayoutResult,
    bounds: OrpBounds,
    fullText: String
): OrpLayoutResult {
    val pivotIndex = pivotRange.safePivotIndex.coerceIn(pivotRange.start, pivotRange.end)
    val lastIndex = fullText.lastIndex.coerceAtLeast(DEFAULT_PIVOT_INDEX)
    val pivotCenter = pivotCenterX(measured, pivotIndex, lastIndex)
    val textLeft = measured.getBoundingBox(DEFAULT_PIVOT_INDEX).left
    val textRight = measured.getBoundingBox(lastIndex).right
    val textCenter = (textLeft + textRight) / BIAS_SCALE_FACTOR
    val chunkingShiftPx = pivotCenter - textCenter
    val minPivotX = bounds.safeLeftPx + pivotCenter
    val maxPivotX = bounds.maxTranslationX + pivotCenter
    val guidePivotX = (bounds.desiredPivotX + chunkingShiftPx).coerceIn(minPivotX, maxPivotX)
    val translationX = guidePivotX - pivotCenter
    val alignment = alignPivotToPixel(pivotCenter, translationX)
    return OrpLayoutResult(
        pivotIndex = pivotIndex,
        translationX = alignment.translationX,
        guideBias = guideBias(bounds.maxWidthPx, alignment.pivotX)
    )
}

private fun layoutWideWord(
    pivotRange: OrpPivotRange,
    measured: TextLayoutResult,
    bounds: OrpBounds,
    lastIndex: Int
): OrpLayoutResult {
    val pivotIndex = pivotRange.safePivotIndex.coerceIn(pivotRange.start, pivotRange.end)
    val measuredWidthPx = measured.size.width.toFloat()
    val translationX = (bounds.maxWidthPx - measuredWidthPx) / BIAS_SCALE_FACTOR
    val pivotCenter = pivotCenterX(
        measured,
        pivotIndex,
        lastIndex.coerceAtLeast(DEFAULT_PIVOT_INDEX)
    )
    val alignment = alignPivotToPixel(pivotCenter, translationX)
    return OrpLayoutResult(
        pivotIndex = pivotIndex,
        translationX = alignment.translationX,
        guideBias = guideBias(bounds.maxWidthPx, alignment.pivotX)
    )
}

private fun layoutFlexiblePivot(
    pivotRange: OrpPivotRange,
    measured: TextLayoutResult,
    bounds: OrpBounds,
    fullText: String
): OrpLayoutResult {
    val lastIndex = fullText.lastIndex.coerceAtLeast(DEFAULT_PIVOT_INDEX)
    val basePivotIndex = pivotRange.safePivotIndex
    val pivotCenter = pivotCenterX(measured, basePivotIndex, lastIndex)
    val translationX = (bounds.desiredPivotX - pivotCenter)
        .coerceIn(bounds.safeLeftPx, bounds.maxTranslationX)
    val alignment = alignPivotToPixel(pivotCenter, translationX)
    return OrpLayoutResult(
        pivotIndex = basePivotIndex,
        translationX = alignment.translationX,
        guideBias = guideBias(bounds.maxWidthPx, alignment.pivotX)
    )
}

private fun pivotCenterX(measured: TextLayoutResult, index: Int, lastIndex: Int): Float {
    val safeIndex = index.coerceIn(DEFAULT_PIVOT_INDEX, lastIndex)
    val box = measured.getBoundingBox(safeIndex)
    return box.left + (box.width / BIAS_SCALE_FACTOR)
}

private fun guideBias(maxWidthPx: Float, guidePivotX: Float): Float {
    return ((guidePivotX / maxWidthPx) * BIAS_SCALE_FACTOR) - ONE_FLOAT
}

private fun alignPivotToPixel(pivotCenter: Float, translationX: Float): PivotAlignment {
    val actualPivotX = pivotCenter + translationX
    val roundedPivotX = actualPivotX.roundToInt().toFloat()
    val adjustedTranslation = translationX + (roundedPivotX - actualPivotX)
    return PivotAlignment(pivotX = roundedPivotX, translationX = adjustedTranslation)
}
