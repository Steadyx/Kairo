package com.kairo.reader.ui.rsvp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.RsvpFrame

internal data class RsvpContextWindow(val startIndex: Int, val endExclusive: Int, val focusStartIndex: Int, val focusEndExclusive: Int,)

internal sealed interface RsvpContextContent {
    data class Ticker(val content: RsvpSentenceTickerContent) : RsvpContextContent

    data class Peripheral(val previous: AnnotatedString, val upcoming: AnnotatedString,) : RsvpContextContent
}

internal data class RsvpSentenceTickerContent(
    val text: AnnotatedString,
    val pivotPosition: Int,
    val focusStart: Int,
    val focusEndExclusive: Int,
    val displayedFocusStart: Int,
    val displayedFocusEndExclusive: Int,
)

internal data class ContextCueSlots(
    val previousWidth: Dp,
    val focusGap: Dp,
    val upcomingWidth: Dp,
    val hasPreviousRoom: Boolean,
    val hasUpcomingRoom: Boolean,
)

internal data class ContextFocusEnvelope(val leftReserve: Dp, val rightReserve: Dp,)

internal data class ContextTickerFocusAlignment(val startOffset: Int, val endExclusiveOffset: Int, val pivotOffset: Int,)

@Composable
internal fun BoxScope.RsvpContextAssist(
    context: RsvpUiContext,
    frame: RsvpFrame?,
    bottomChromeInset: Dp,
) {
    val runtime = context.runtime
    val config = context.state.profile.config
    val mode = config.contextAssistMode
    val content = rememberRsvpContextContent(context, frame)
    val controlsAllowContext =
        !runtime.showControls || mode == RsvpContextAssistMode.SENTENCE_TICKER
    val visible =
        content != null &&
            mode != RsvpContextAssistMode.OFF &&
            controlsAllowContext &&
            !runtime.showQuickSettings &&
            !runtime.isPositioningMode &&
            !runtime.isExiting

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(bottom = bottomChromeInset),
            contentAlignment =
            BiasAlignment(
                horizontalBias = CENTER_BIAS,
                verticalBias =
                runtime.currentVerticalBias.coerceIn(
                    VERTICAL_BIAS_MIN,
                    VERTICAL_BIAS_MAX,
                ),
            ),
        ) {
            val guideBandHeight =
                rememberContextGuideBandHeight(
                    fontSizeSp = runtime.currentFontSizeSp,
                    fontFamily = runtime.currentFontFamily,
                    fontWeight = runtime.currentFontWeight,
                    frame = frame,
                    guideVisible = config.orpGuideEnabled,
                    guideThickness =
                    config.orpGuideThickness
                        .toFloat()
                        .coerceIn(ORP_GUIDE_THICKNESS_MIN, ORP_GUIDE_THICKNESS_MAX),
                )
            when (val resolvedContent = content) {
                is RsvpContextContent.Ticker -> RsvpSentenceTicker(
                    content = resolvedContent.content,
                    fontSizeSp = runtime.currentFontSizeSp,
                    fontFamily = runtime.currentFontFamily,
                    fontWeight = runtime.currentFontWeight,
                    horizontalBias = runtime.currentHorizontalBias,
                    guideBandHeight = guideBandHeight,
                    frameDurationMs = frame?.durationMs ?: CONTEXT_TICKER_FALLBACK_FRAME_MS,
                )
                is RsvpContextContent.Peripheral -> {
                    val targetFocusEnvelope =
                        rememberContextFocusEnvelope(
                            frames = context.frameState.frames,
                            frameIndex = runtime.frameIndex,
                            fontSizeSp = runtime.currentFontSizeSp,
                            fontFamily = runtime.currentFontFamily,
                            fontWeight = runtime.currentFontWeight,
                        )
                    val leftReserve =
                        animateDpAsState(
                            targetValue = targetFocusEnvelope.leftReserve,
                            animationSpec = tween(durationMillis = CONTEXT_ENVELOPE_ANIMATION_MS),
                            label = "contextLeftReserve",
                        ).value
                    val rightReserve =
                        animateDpAsState(
                            targetValue = targetFocusEnvelope.rightReserve,
                            animationSpec = tween(durationMillis = CONTEXT_ENVELOPE_ANIMATION_MS),
                            label = "contextRightReserve",
                        ).value
                    RsvpPeripheralContext(
                        previous = resolvedContent.previous,
                        upcoming = resolvedContent.upcoming,
                        focusLeftReserve = leftReserve,
                        focusRightReserve = rightReserve,
                        fontSizeSp = runtime.currentFontSizeSp,
                        fontFamily = runtime.currentFontFamily,
                        fontWeight = runtime.currentFontWeight,
                        horizontalBias = runtime.currentHorizontalBias,
                        guideBandHeight = guideBandHeight,
                    )
                }
                null -> Unit
            }
        }
    }
}
