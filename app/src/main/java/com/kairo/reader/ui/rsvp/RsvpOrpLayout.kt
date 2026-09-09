@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.rsvp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.util.LinkedHashMap
import kotlin.math.roundToInt

@Composable
internal fun OrpAlignedTextLayout(
    content: OrpTextContent,
    layout: OrpTextLayout,
    colors: OrpColors,
    typography: OrpTypography,
    contextCues: List<AnnotatedString> = emptyList(),
    followingContextCues: List<AnnotatedString> = emptyList(),
) {
    val density = LocalDensity.current
    val effectiveBias = layout.horizontalBias.safeCoerceIn(HORIZONTAL_BIAS_MIN, HORIZONTAL_BIAS_MAX)
    val textMeasurer = rememberTextMeasurer()
    val displayCache = remember(textMeasurer) { OrpDisplayCache() }
    val baseStyle = MaterialTheme.typography.displayMedium
    val textStyle =
        remember(typography, baseStyle) {
            baseStyle.copy(
                fontSize = typography.fontSizeSp.sp,
                lineHeight = (typography.fontSizeSp * ORP_TEXT_LINE_HEIGHT_MULTIPLIER).sp,
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
            remember(
                content,
                textStyle,
                colors,
                maxWidthPx,
                layout.preferWindowing,
                layout.pivotHighlightVisible,
            ) {
                displayCache.getOrPut(
                    key =
                    OrpDisplayCacheKey(
                        content = content,
                        textStyle = textStyle,
                        colors = colors,
                        maxWidthPx = maxWidthPx.roundToInt(),
                        preferWindowing = layout.preferWindowing,
                        pivotHighlightVisible = layout.pivotHighlightVisible,
                    ),
                ) {
                    resolveOrpDisplay(
                        content = content,
                        textStyle = textStyle,
                        colors = colors,
                        textMeasurer = textMeasurer,
                        maxWidthPx = maxWidthPx,
                        preferWindowing = layout.preferWindowing,
                        pivotHighlightVisible = layout.pivotHighlightVisible,
                    )
                }
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
            if (display.content.fullText.isEmpty()) {
                OrpLayoutResult(
                    pivotIndex = DEFAULT_PIVOT_INDEX,
                    translationX = ZERO_FLOAT,
                    guideBias = stableGuideBias(bounds),
                )
            } else {
                calculateOrpLayout(
                    display.content,
                    layout,
                    display.measured,
                    bounds,
                    pivotRange,
                )
            }
        val translationX =
            if (layout.smoothTranslation) {
                animateFloatAsState(
                    targetValue = layoutResult.translationX,
                    animationSpec =
                    spring(
                        dampingRatio = 1f,
                        stiffness = 1800f,
                    ),
                    label = "orpTranslationX",
                ).value
            } else {
                layoutResult.translationX
            }
        val annotatedText =
            remember(
                display.content,
                layoutResult.pivotIndex,
                colors,
                layout.pivotHighlightVisible,
            ) {
                buildOrpAnnotatedText(
                    fullText = display.content.fullText,
                    pivotPosition = layoutResult.pivotIndex,
                    pivotColor = colors.pivotColor,
                    highlightStart = display.content.highlightStart,
                    highlightEndExclusive = display.content.highlightEndExclusive,
                    highlightColor = colors.highlightColor,
                    pivotHighlightVisible = layout.pivotHighlightVisible,
                )
            }

        val guideThickness = ORP_LINE_HEIGHT * layout.guideThickness
        val pointerThickness = ORP_POINTER_WIDTH * layout.guideThickness
        val measuredTextHeight = with(density) { display.measured.size.height.toDp() }
        val containerModifier =
            if (layout.guideVisible) {
                Modifier
                    .fillMaxWidth()
                    .height(orpGuideBandHeight(measuredTextHeight, layout.guideThickness))
            } else {
                Modifier.fillMaxWidth().height(measuredTextHeight)
            }

        // The focus and inline cue share the exact rendered baseline and guide geometry.
        Box(
            modifier = containerModifier,
            contentAlignment = Alignment.Center,
        ) {
            if (layout.guideVisible) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OrpStaticLine(colors.pivotLineColor, guideThickness)
                    OrpPointer(layoutResult.guideBias, colors.pivotLineColor, pointerThickness)
                    Spacer(modifier = Modifier.height(ORP_TEXT_SPACER))
                    Spacer(modifier = Modifier.height(measuredTextHeight))
                    Spacer(modifier = Modifier.height(ORP_TEXT_SPACER))
                    OrpPointer(layoutResult.guideBias, colors.pivotLineColor, pointerThickness)
                    OrpStaticLine(colors.pivotLineColor, guideThickness)
                }
            }
            if (layout.wordAlpha > 0f) {
                OrpTextLine(
                    annotatedText,
                    display.textStyle,
                    colors.textColor,
                    translationX,
                    alpha = layout.wordAlpha,
                )
            }
            RsvpInlineContext(
                preceding = contextCues,
                following = followingContextCues,
                focusOffsetPx = snapTranslationToRenderPixel(translationX),
                focusMeasurement = display.measured,
                focusStyle = display.textStyle,
                focusHeight = measuredTextHeight,
                lineWidthPx = maxWidthPx,
            )
        }
    }
}

internal fun orpGuideBandHeight(
    measuredTextHeight: Dp,
    guideThickness: Float,
): Dp =
    measuredTextHeight +
        (ORP_TEXT_SPACER * 2) +
        (ORP_POINTER_HEIGHT * 2) +
        (ORP_LINE_HEIGHT * guideThickness * 2f)

private data class OrpDisplay(
    val content: OrpTextContent,
    val annotatedText: AnnotatedString,
    val textStyle: TextStyle,
    val measured: TextLayoutResult,
)

private data class OrpDisplayCacheKey(
    val content: OrpTextContent,
    val textStyle: TextStyle,
    val colors: OrpColors,
    val maxWidthPx: Int,
    val preferWindowing: Boolean,
    val pivotHighlightVisible: Boolean,
)

private class OrpDisplayCache {
    private val entries =
        object : LinkedHashMap<OrpDisplayCacheKey, OrpDisplay>(
            ORP_DISPLAY_CACHE_SIZE,
            ORP_DISPLAY_CACHE_LOAD_FACTOR,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<OrpDisplayCacheKey, OrpDisplay>?,
            ): Boolean = size > ORP_DISPLAY_CACHE_SIZE
        }

    fun getOrPut(
        key: OrpDisplayCacheKey,
        createDisplay: () -> OrpDisplay,
    ): OrpDisplay =
        entries[key]
            ?: createDisplay().also { display ->
                entries[key] = display
            }
}

private fun resolveOrpDisplay(
    content: OrpTextContent,
    textStyle: TextStyle,
    colors: OrpColors,
    textMeasurer: TextMeasurer,
    maxWidthPx: Float,
    preferWindowing: Boolean,
    pivotHighlightVisible: Boolean,
): OrpDisplay {
    val baseAnnotated =
        buildOrpAnnotatedText(
            fullText = content.fullText,
            pivotPosition = content.pivotPosition,
            pivotColor = colors.pivotColor,
            highlightStart = content.highlightStart,
            highlightEndExclusive = content.highlightEndExclusive,
            highlightColor = colors.highlightColor,
            pivotHighlightVisible = pivotHighlightVisible,
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
    if (!preferWindowing && scale >= ORP_MIN_AUTO_FIT_SCALE) {
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
            pivotHighlightVisible = pivotHighlightVisible,
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
            focusPivot = content.pivotPosition,
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
    focusPivot: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Float,
): WindowRange {
    val length = fullText.length
    val safeFocusStart = focusStart.safeCoerceIn(0, length - 1)
    val safeFocusEnd = focusEndExclusive.safeCoerceIn(safeFocusStart + 1, length)
    val safeFocusPivot = focusPivot.safeCoerceIn(safeFocusStart, safeFocusEnd - 1)

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

    if (!fits(safeFocusStart, safeFocusEnd)) {
        return resolveOversizedFocusRange(
            focusStart = safeFocusStart,
            focusEndExclusive = safeFocusEnd,
            focusPivot = safeFocusPivot,
            fits = { candidateStart, candidateEnd -> fits(candidateStart, candidateEnd) },
        )
    }

    val maxSymmetricRadius = maxOf(safeFocusStart, length - safeFocusEnd)
    val symmetricRadius =
        maxFittingValue(0, maxSymmetricRadius) { radius ->
            fits(
                (safeFocusStart - radius).coerceAtLeast(0),
                (safeFocusEnd + radius).coerceAtMost(length),
            )
        }
    var start = (safeFocusStart - symmetricRadius).coerceAtLeast(0)
    var end = (safeFocusEnd + symmetricRadius).coerceAtMost(length)

    val leftExpansion =
        maxFittingValue(0, start) { extraChars ->
            fits(start - extraChars, end)
        }
    start -= leftExpansion

    val rightExpansion =
        maxFittingValue(0, length - end) { extraChars ->
            fits(start, end + extraChars)
        }
    end += rightExpansion

    return WindowRange(start = start, endExclusive = end)
}

private fun resolveOversizedFocusRange(
    focusStart: Int,
    focusEndExclusive: Int,
    focusPivot: Int,
    fits: (Int, Int) -> Boolean,
): WindowRange {
    val focusLength = (focusEndExclusive - focusStart).coerceAtLeast(1)
    val bestSpan =
        maxFittingValue(1, focusLength) { span ->
            val range =
                windowRangeAroundPivot(
                    focusStart = focusStart,
                    focusEndExclusive = focusEndExclusive,
                    focusPivot = focusPivot,
                    span = span,
                )
            fits(range.start, range.endExclusive)
        }
    return windowRangeAroundPivot(
        focusStart = focusStart,
        focusEndExclusive = focusEndExclusive,
        focusPivot = focusPivot,
        span = bestSpan,
    )
}

private fun windowRangeAroundPivot(
    focusStart: Int,
    focusEndExclusive: Int,
    focusPivot: Int,
    span: Int,
): WindowRange {
    val safeSpan = span.coerceIn(1, focusEndExclusive - focusStart)
    val maxStart = focusEndExclusive - safeSpan
    val start = (focusPivot - (safeSpan / 2)).coerceIn(focusStart, maxStart)
    return WindowRange(start = start, endExclusive = start + safeSpan)
}

private fun maxFittingValue(
    minimum: Int,
    maximum: Int,
    fits: (Int) -> Boolean,
): Int {
    var low = minimum
    var high = maximum
    var best = minimum
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (fits(mid)) {
            best = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return best
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

internal fun calculateOrpBounds(
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

internal fun calculatePivotRange(
    content: OrpTextContent,
    lastIndex: Int,
    safePivotIndex: Int,
): OrpPivotRange {
    // A phrase pivot can legitimately live in any word. Keeping the first-word range here used
    // to clamp the layout pivot away from the independently highlighted phrase pivot.
    if (content.wordCount > ORP_LOCK_PIVOT_WORDS) {
        return OrpPivotRange(
            start = DEFAULT_PIVOT_INDEX,
            end = lastIndex.coerceAtLeast(DEFAULT_PIVOT_INDEX),
            safePivotIndex = safePivotIndex,
        )
    }

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
            layoutLockedPivot(pivotRange, measured, bounds)
        bounds.maxTranslationX < bounds.safeLeftPx ->
            layoutWideWord(pivotRange, measured, bounds, content.fullText.lastIndex)
        else ->
            layoutFlexiblePivot(pivotRange, measured, bounds, content.fullText)
    }

private fun layoutLockedPivot(
    pivotRange: OrpPivotRange,
    measured: TextLayoutResult,
    bounds: OrpBounds,
): OrpLayoutResult {
    val pivotIndex = pivotRange.safePivotIndex.safeCoerceIn(pivotRange.start, pivotRange.end)
    val pivotCenter =
        pivotCenterX(
            measured = measured,
            index = pivotIndex,
            lastIndex = pivotRange.end,
        )
    val minTranslationX = minOf(bounds.safeLeftPx, bounds.maxTranslationX)
    val maxTranslationX = maxOf(bounds.safeLeftPx, bounds.maxTranslationX)
    val translationX =
        stablePivotAlignedTranslationX(
            desiredPivotX = bounds.desiredPivotX,
            pivotCenterX = pivotCenter,
            minTranslationX = minTranslationX,
            maxTranslationX = maxTranslationX,
        )

    return OrpLayoutResult(
        pivotIndex = pivotIndex,
        translationX = translationX,
        guideBias = stableGuideBias(bounds),
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
        guideBias = stableGuideBias(bounds),
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
    val pivotAlignedTranslationX =
        (bounds.desiredPivotX - pivotCenter)
            .safeCoerceIn(minTranslationX, maxTranslationX)
    val measuredWidthPx = measured.size.width.toFloat()
    val centeredTranslationX =
        ((bounds.maxWidthPx - measuredWidthPx) / BIAS_SCALE_FACTOR)
            .safeCoerceIn(minTranslationX, maxTranslationX)
    val stabilizedTranslationX =
        lerpFloat(
            start = centeredTranslationX,
            end = pivotAlignedTranslationX,
            fraction = ONE_FLOAT,
        )
    val alignment = alignPivotToPixel(pivotCenter, stabilizedTranslationX)
    return OrpLayoutResult(
        pivotIndex = basePivotIndex,
        translationX = alignment.translationX,
        guideBias = stableGuideBias(bounds),
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

private fun stableGuideBias(bounds: OrpBounds): Float {
    val guidePivotX =
        bounds.desiredPivotX
            .roundToInt()
            .toFloat()
            .safeCoerceIn(ZERO_FLOAT, bounds.maxWidthPx)
    return guideBias(bounds.maxWidthPx, guidePivotX)
}

private fun alignPivotToPixel(
    pivotCenter: Float,
    translationX: Float,
): PivotAlignment {
    val actualPivotX = pivotCenter + translationX
    val roundedPivotX = actualPivotX.roundToInt().toFloat()
    val adjustedTranslation = translationX + (roundedPivotX - actualPivotX)
    return PivotAlignment(pivotX = roundedPivotX, translationX = adjustedTranslation)
}

private fun lerpFloat(
    start: Float,
    end: Float,
    fraction: Float,
): Float {
    val safeFraction = fraction.safeCoerceIn(ZERO_FLOAT, ONE_FLOAT)
    return start + ((end - start) * safeFraction)
}

internal fun stableCenteredTranslationX(
    maxWidthPx: Float,
    measuredWidthPx: Float,
    minTranslationX: Float,
    maxTranslationX: Float,
): Float =
    stableBiasedCenterTranslationX(
        desiredCenterX = maxWidthPx / BIAS_SCALE_FACTOR,
        measuredWidthPx = measuredWidthPx,
        minTranslationX = minTranslationX,
        maxTranslationX = maxTranslationX,
    )

internal fun stableBiasedCenterTranslationX(
    desiredCenterX: Float,
    measuredWidthPx: Float,
    minTranslationX: Float,
    maxTranslationX: Float,
): Float {
    val centeredTranslationX =
        if (desiredCenterX.isFinite() && measuredWidthPx.isFinite()) {
            desiredCenterX - (measuredWidthPx / BIAS_SCALE_FACTOR)
        } else {
            ZERO_FLOAT
        }
    val clampedTranslationX =
        centeredTranslationX.safeCoerceIn(minTranslationX, maxTranslationX)
    return snapTranslationToRenderPixel(clampedTranslationX)
        .safeCoerceIn(minTranslationX, maxTranslationX)
}

internal fun stablePivotAlignedTranslationX(
    desiredPivotX: Float,
    pivotCenterX: Float,
    minTranslationX: Float,
    maxTranslationX: Float,
): Float {
    val pivotAlignedTranslationX =
        if (desiredPivotX.isFinite() && pivotCenterX.isFinite()) {
            desiredPivotX - pivotCenterX
        } else {
            ZERO_FLOAT
        }
    val clampedTranslationX =
        pivotAlignedTranslationX.safeCoerceIn(minTranslationX, maxTranslationX)
    return snapTranslationToRenderPixel(clampedTranslationX)
        .safeCoerceIn(minTranslationX, maxTranslationX)
}

internal fun snapTranslationToRenderPixel(translationX: Float): Float =
    if (translationX.isFinite()) {
        translationX.roundToInt().toFloat()
    } else {
        ZERO_FLOAT
    }
