package com.kairo.reader.ui.rsvp

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.RsvpFontWeight
import com.kairo.reader.core.model.RsvpFrame

@Composable
internal fun RsvpPeripheralContext(
    previous: AnnotatedString,
    upcoming: AnnotatedString,
    focusLeftReserve: Dp,
    focusRightReserve: Dp,
    fontSizeSp: Float,
    fontFamily: RsvpFontFamily,
    fontWeight: RsvpFontWeight,
    horizontalBias: Float,
    guideBandHeight: Dp?,
) {
    val contextModifier =
        if (guideBandHeight != null) {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ORP_HORIZONTAL_PADDING)
                .height(guideBandHeight)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ORP_HORIZONTAL_PADDING)
        }
    BoxWithConstraints(
        modifier = contextModifier,
        contentAlignment = Alignment.Center,
    ) {
        val slots =
            resolveContextCueSlots(
                availableWidth = maxWidth,
                focusLeftReserve = focusLeftReserve,
                focusRightReserve = focusRightReserve,
                horizontalBias = horizontalBias,
                minimumCueWidth = CONTEXT_MIN_CUE_WIDTH,
                cueInnerPadding = CONTEXT_CUE_INNER_PADDING,
            )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RsvpPeripheralCueText(
                text = if (slots.hasPreviousRoom) previous else AnnotatedString(""),
                textAlign = TextAlign.End,
                fontSizeSp = fontSizeSp,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                modifier = Modifier.width(slots.previousWidth),
            )
            Spacer(modifier = Modifier.width(slots.focusGap))
            RsvpPeripheralCueText(
                text = if (slots.hasUpcomingRoom) upcoming else AnnotatedString(""),
                textAlign = TextAlign.Start,
                fontSizeSp = fontSizeSp,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                modifier = Modifier.width(slots.upcomingWidth),
            )
        }
    }
}

@Composable
internal fun rememberContextGuideBandHeight(
    fontSizeSp: Float,
    fontFamily: RsvpFontFamily,
    fontWeight: RsvpFontWeight,
    frame: RsvpFrame?,
    guideVisible: Boolean,
    guideThickness: Float,
): Dp? {
    if (!guideVisible) return null

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val focusContent = remember(frame?.tokens) { buildOrpTextContent(frame?.tokens.orEmpty()) }
    val focusStyle = rememberRsvpContextTextStyle(fontSizeSp, fontFamily, fontWeight)
    val measuredTextHeightPx =
        remember(focusContent, focusStyle, textMeasurer) {
            textMeasurer.measure(
                text = AnnotatedString(focusContent.fullText.ifEmpty { CONTEXT_BASELINE_SAMPLE }),
                style = focusStyle,
                overflow = TextOverflow.Clip,
                maxLines = 1,
                softWrap = false,
                constraints = Constraints(maxWidth = Int.MAX_VALUE),
            ).size.height
        }
    val measuredTextHeight = with(density) { measuredTextHeightPx.toDp() }
    return orpGuideBandHeight(measuredTextHeight, guideThickness)
}

@Composable
internal fun rememberContextFocusEnvelope(
    frames: List<RsvpFrame>,
    frameIndex: Int,
    fontSizeSp: Float,
    fontFamily: RsvpFontFamily,
    fontWeight: RsvpFontWeight,
): ContextFocusEnvelope {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val focusStyle = rememberRsvpContextTextStyle(fontSizeSp, fontFamily, fontWeight)
    val frameRange =
        remember(frameIndex, frames) {
            resolveThoughtEnvelopeFrameRange(frames, frameIndex) ?: resolveContextEnvelopeFrameRange(
                frameIndex = frameIndex,
                frameCount = frames.size,
                blockSize = CONTEXT_ENVELOPE_BLOCK_FRAMES,
            )
        }
    val extentsPx =
        remember(frames, frameRange, focusStyle, textMeasurer) {
            var maxLeftPx = 0f
            var maxRightPx = 0f
            frameRange.forEach { index ->
                val content = buildOrpTextContent(frames[index].tokens)
                val text = content.fullText
                if (text.isNotEmpty()) {
                    val measured =
                        textMeasurer.measure(
                            text = AnnotatedString(text),
                            style = focusStyle,
                            overflow = TextOverflow.Clip,
                            maxLines = 1,
                            softWrap = false,
                            constraints = Constraints(maxWidth = Int.MAX_VALUE),
                        )
                    val pivot = content.pivotPosition.coerceIn(0, text.lastIndex)
                    val pivotBox = measured.getBoundingBox(pivot)
                    val pivotCenter = pivotBox.left + (pivotBox.width / BIAS_SCALE_FACTOR)
                    maxLeftPx = maxOf(maxLeftPx, pivotCenter)
                    maxRightPx = maxOf(maxRightPx, measured.size.width.toFloat() - pivotCenter)
                }
            }
            maxLeftPx to maxRightPx
        }
    val leftReserve =
        (with(density) { extentsPx.first.toDp() } + CONTEXT_FOCUS_SIDE_PADDING)
            .coerceAtLeast(CONTEXT_MIN_FOCUS_SIDE_RESERVE)
    val rightReserve =
        (with(density) { extentsPx.second.toDp() } + CONTEXT_FOCUS_SIDE_PADDING)
            .coerceAtLeast(CONTEXT_MIN_FOCUS_SIDE_RESERVE)
    return ContextFocusEnvelope(leftReserve = leftReserve, rightReserve = rightReserve)
}

internal fun resolveThoughtEnvelopeFrameRange(frames: List<RsvpFrame>, frameIndex: Int): IntRange? {
    val phraseStart = frames.getOrNull(frameIndex)?.phraseStartTokenIndex ?: return null
    var start = frameIndex
    var end = frameIndex + 1
    while (start > 0 && frames[start - 1].phraseStartTokenIndex == phraseStart) start--
    while (end < frames.size && frames[end].phraseStartTokenIndex == phraseStart) end++
    return start until end
}

@Composable
internal fun RsvpPeripheralCueText(
    text: AnnotatedString,
    textAlign: TextAlign,
    fontSizeSp: Float,
    fontFamily: RsvpFontFamily,
    fontWeight: RsvpFontWeight,
    modifier: Modifier = Modifier,
) {
    val effectiveFontSizeSp = stableContextCueFontSizeSp(fontSizeSp)
    val style = rememberRsvpContextTextStyle(effectiveFontSizeSp, fontFamily, fontWeight)
    Text(
        text = text,
        style = style,
        textAlign = textAlign,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}
