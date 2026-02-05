@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.nearestWordIndex
import com.example.kairo.ui.theme.InterFontFamily
import com.example.kairo.ui.theme.RobotoFontFamily
import kotlin.math.roundToLong

private enum class PreviewSide { ABOVE, BELOW }

@Composable
internal fun RsvpPlaybackSurface(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val currentFrame = frames.getOrNull(runtime.frameIndex)
    val typography =
        OrpTypography(
            fontSizeSp = runtime.currentFontSizeSp,
            fontFamily = resolveFontFamily(runtime.currentFontFamily),
            fontWeight = resolveFontWeight(runtime.currentFontWeight),
        )
    val colors = rememberRsvpTextColors(runtime.currentTextBrightness)
    val interactionSource = remember { MutableInteractionSource() }
    val estimatedWpm = rememberEstimatedWpm(frames, context.timing.tempoScale)

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .rsvpGestureModifier(context, interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        RsvpFocusWord(context, currentFrame, typography, colors)
        RsvpParagraphPreview(context, typography, colors)
        RsvpPositionGuide(context)
        RsvpProgressBar(context)
        RsvpTopBar(context)
        RsvpTempoIndicator(context, estimatedWpm)
        RsvpFontSizeIndicator(context)
        RsvpPositioningIndicator(context)
        RsvpScrubTargetIndicator(context)
        RsvpQuickSettingsPanel(context, estimatedWpm)
        RsvpBottomControls(context)
    }
}

@Composable
private fun rememberEstimatedWpm(
    frames: List<com.example.kairo.core.model.RsvpFrame>,
    tempoScale: Double,
): Int {
    val baseFrameStats =
        remember(frames) {
            val wordUnits =
                frames
                    .sumOf { frame -> frame.tokens.sumOf { it.wordUnitWeight() } }
                    .coerceAtLeast(MIN_WORD_COUNT.toDouble())
            val totalMs = frames.sumOf { it.durationMs }.coerceAtLeast(MIN_TOTAL_MS)
            wordUnits to totalMs
        }
    return remember(baseFrameStats, tempoScale) {
        val wordCount = baseFrameStats.first
        val totalMs =
            (baseFrameStats.second * tempoScale)
                .roundToLong()
                .coerceAtLeast(MIN_TOTAL_MS)
        ((wordCount * MS_PER_MINUTE) / totalMs.toDouble()).toInt().coerceAtLeast(MIN_WORD_COUNT)
    }
}

private fun com.example.kairo.core.model.Token.wordUnitWeight(): Double {
    if (type != TokenType.WORD) return 0.0
    if (!isSubwordChunk) return 1.0
    val start = highlightStart
    val end = highlightEndExclusive
    if (start == null || end == null || end <= start || text.isEmpty()) return 1.0
    val chunkLength = (end - start).toDouble().coerceAtLeast(1.0)
    val fullLength = text.length.toDouble().coerceAtLeast(chunkLength)
    return (chunkLength / fullLength).coerceIn(0.1, 1.0)
}

@Composable
private fun rememberRsvpTextColors(textBrightness: Float): OrpColors {
    val clampedBrightness = textBrightness.coerceIn(TEXT_BRIGHTNESS_MIN, TEXT_BRIGHTNESS_MAX)
    val pivotLineAlpha =
        (PIVOT_LINE_ALPHA_BASE * clampedBrightness)
            .coerceIn(PIVOT_LINE_ALPHA_MIN, PIVOT_LINE_ALPHA_MAX)
    return OrpColors(
        pivotColor = MaterialTheme.colorScheme.primary,
        pivotLineColor = MaterialTheme.colorScheme.onBackground.copy(alpha = pivotLineAlpha),
        textColor = MaterialTheme.colorScheme.onBackground.copy(alpha = clampedBrightness),
        highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
    )
}

@Composable
private fun RsvpFocusWord(
    context: RsvpUiContext,
    frame: com.example.kairo.core.model.RsvpFrame?,
    typography: OrpTypography,
    colors: OrpColors,
) {
    val runtime = context.runtime
    val profile = context.state.profile
    if (frame == null) return

    Box(
        modifier = Modifier.fillMaxSize(),
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
        OrpAlignedText(
            tokens = frame.tokens,
            typography = typography,
            colors = colors,
            layout =
            OrpTextLayout(
                horizontalBias = runtime.currentHorizontalBias,
                lockPivot =
                profile.config.enablePhraseChunking &&
                    profile.config.maxWordsPerUnit.coerceAtLeast(2) > ORP_LOCK_PIVOT_WORDS,
                smoothTranslation = runtime.isScrubbing || runtime.isAdjustingPosition,
            ),
        )
    }
}

@Composable
private fun RsvpParagraphPreview(
    context: RsvpUiContext,
    typography: OrpTypography,
    colors: OrpColors,
) {
    val runtime = context.runtime
    val tokens = context.state.book.tokens
    if (tokens.isEmpty()) return

    val currentIndex =
        resolveCurrentTokenIndex(
            context.frameState.frames,
            runtime.frameIndex,
            context.state.book.startIndex,
        )
    val highlightIndex =
        remember(tokens, currentIndex) {
            tokens.nearestWordIndex(currentIndex)
        }
    val paragraph =
        remember(tokens, highlightIndex) {
            resolveRsvpParagraph(tokens, highlightIndex)
        } ?: return
    val highlightTextColor = MaterialTheme.colorScheme.primary
    val highlightStyle =
        remember(colors, highlightTextColor) {
            SpanStyle(
                color = highlightTextColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    val annotatedText =
        remember(paragraph, highlightIndex, highlightStyle) {
            buildRsvpParagraphAnnotatedText(paragraph, highlightIndex, highlightStyle)
        }
    val fontSizeSp =
        (typography.fontSizeSp * PARAGRAPH_FONT_SCALE)
            .coerceIn(MIN_PARAGRAPH_FONT_SIZE_SP, MAX_PARAGRAPH_FONT_SIZE_SP)
    val lineHeightSp = fontSizeSp * PARAGRAPH_LINE_HEIGHT_MULTIPLIER
    val lineCount =
        with(LocalDensity.current) {
            val lineHeightPx = lineHeightSp.sp.toPx().coerceAtLeast(1f)
            val previewHeightPx =
                (
                    PARAGRAPH_PREVIEW_HEIGHT -
                        (PARAGRAPH_PREVIEW_CONTENT_PADDING_VERTICAL * 2)
                ).toPx().coerceAtLeast(lineHeightPx)
            (previewHeightPx / lineHeightPx).toInt().coerceAtLeast(1)
        }
    val textStyle =
        MaterialTheme.typography.bodyMedium.copy(
            fontSize = fontSizeSp.sp,
            fontFamily = typography.fontFamily,
            fontWeight = FontWeight.Normal,
            color = colors.textColor.copy(alpha = PARAGRAPH_TEXT_ALPHA),
            lineHeight = lineHeightSp.sp,
        )
    val offsetY =
        maxOf(
            with(LocalDensity.current) {
                (typography.fontSizeSp * PARAGRAPH_OFFSET_MULTIPLIER).sp.toDp()
            },
            (PARAGRAPH_PREVIEW_HEIGHT / 2) + PARAGRAPH_PREVIEW_MIN_ORP_CLEARANCE,
        )
    var previewSide by remember { mutableStateOf(PreviewSide.BELOW) }
    val visible = runtime.isScrubbing || (!runtime.isPlaying && !runtime.isExiting)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        val backgroundColor = MaterialTheme.colorScheme.background
        val previewSurfaceColor =
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PARAGRAPH_PREVIEW_SURFACE_ALPHA)
        val previewBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        val clampedVerticalBias = runtime.currentVerticalBias.coerceIn(VERTICAL_BIAS_MIN, VERTICAL_BIAS_MAX)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val previewHeightPx = with(density) { PARAGRAPH_PREVIEW_HEIGHT.toPx() }
            val preferredOffsetPx = with(density) { offsetY.toPx() }
            val edgePaddingPx = with(density) { PARAGRAPH_PREVIEW_EDGE_PADDING.toPx() }
            val collisionGapPx = with(density) { PARAGRAPH_PREVIEW_ORP_COLLISION_GAP.toPx() }
            val switchHysteresisPx = with(density) { PARAGRAPH_PREVIEW_SWITCH_HYSTERESIS.toPx() }
            val switchOverlapThresholdPx =
                with(density) { PARAGRAPH_PREVIEW_SWITCH_OVERLAP_THRESHOLD.toPx() }
            val orpDecorHeightPx =
                with(density) {
                    (
                        (ORP_LINE_HEIGHT * 2) +
                            (ORP_POINTER_HEIGHT * 2) +
                            (ORP_TEXT_SPACER * 2)
                    ).toPx()
                }
            val orpTextHeightPx =
                with(density) {
                    (typography.fontSizeSp * ORP_COLLISION_TEXT_HEIGHT_MULTIPLIER).sp.toPx()
                }
            val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(previewHeightPx)
            val orpCenterY = viewportHeightPx * (ONE_FLOAT + clampedVerticalBias) / BIAS_SCALE_FACTOR
            val orpBandHalfHeightPx = (orpDecorHeightPx + orpTextHeightPx) / BIAS_SCALE_FACTOR
            val protectedTop = orpCenterY - orpBandHalfHeightPx - collisionGapPx
            val protectedBottom = orpCenterY + orpBandHalfHeightPx + collisionGapPx
            val anchorTop =
                ((viewportHeightPx - previewHeightPx) * (ONE_FLOAT + clampedVerticalBias) / BIAS_SCALE_FACTOR)
            val maxTop = (viewportHeightPx - previewHeightPx - edgePaddingPx).coerceAtLeast(edgePaddingPx)
            val rawBelowTop = anchorTop + preferredOffsetPx
            val rawAboveTop = anchorTop - preferredOffsetPx
            val defaultBelowTop = rawBelowTop.coerceIn(edgePaddingPx, maxTop)
            val defaultAboveTop = rawAboveTop.coerceIn(edgePaddingPx, maxTop)
            val safeBelowTop = (protectedBottom).coerceIn(edgePaddingPx, maxTop)
            val safeAboveTop = (protectedTop - previewHeightPx).coerceIn(edgePaddingPx, maxTop)
            val preferAbove = runtime.isPositioningMode && rawBelowTop > maxTop

            fun overlapsOrp(top: Float): Boolean {
                val bottom = top + previewHeightPx
                return bottom > protectedTop && top < protectedBottom
            }

            fun overlapAmount(top: Float): Float {
                val bottom = top + previewHeightPx
                return (minOf(bottom, protectedBottom) - maxOf(top, protectedTop)).coerceAtLeast(0f)
            }

            val belowCandidate = if (overlapsOrp(defaultBelowTop)) safeBelowTop else defaultBelowTop
            val aboveCandidate = if (overlapsOrp(defaultAboveTop)) safeAboveTop else defaultAboveTop

            if (!runtime.isPositioningMode) {
                previewSide = if (preferAbove) PreviewSide.ABOVE else PreviewSide.BELOW
            } else {
                val activeTop = if (previewSide == PreviewSide.ABOVE) aboveCandidate else belowCandidate
                val alternateTop = if (previewSide == PreviewSide.ABOVE) belowCandidate else aboveCandidate
                val activeOverlap = overlapAmount(activeTop)
                val alternateOverlap = overlapAmount(alternateTop)
                val shouldSwitch =
                    activeOverlap > switchOverlapThresholdPx &&
                        (activeOverlap - alternateOverlap) > switchHysteresisPx
                if (shouldSwitch) {
                    previewSide =
                        if (previewSide == PreviewSide.ABOVE) {
                            PreviewSide.BELOW
                        } else {
                            PreviewSide.ABOVE
                        }
                }
            }
            val resolvedTop = if (previewSide == PreviewSide.ABOVE) aboveCandidate else belowCandidate
            val resolvedOffsetY = with(density) { (resolvedTop - anchorTop).toDp() }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment =
                    BiasAlignment(
                        horizontalBias = CENTER_BIAS,
                        verticalBias = clampedVerticalBias,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .offset(y = resolvedOffsetY)
                            .fillMaxWidth()
                            .widthIn(max = PARAGRAPH_PREVIEW_MAX_WIDTH)
                            .padding(horizontal = PARAGRAPH_PREVIEW_HORIZONTAL_PADDING)
                            .height(PARAGRAPH_PREVIEW_HEIGHT)
                            .clip(RoundedCornerShape(PARAGRAPH_PREVIEW_CORNER_RADIUS))
                            .background(previewSurfaceColor)
                            .border(
                                width = 1.dp,
                                color = previewBorderColor,
                                shape = RoundedCornerShape(PARAGRAPH_PREVIEW_CORNER_RADIUS),
                            )
                            .clipToBounds(),
                ) {
                    Text(
                        text = annotatedText,
                        style = textStyle,
                        overflow = TextOverflow.Clip,
                        maxLines = lineCount,
                        minLines = lineCount,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = PARAGRAPH_PREVIEW_CONTENT_PADDING_HORIZONTAL,
                                    vertical = PARAGRAPH_PREVIEW_CONTENT_PADDING_VERTICAL,
                                ),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(PARAGRAPH_FADE_HEIGHT)
                                .align(Alignment.TopCenter)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colorStops =
                                            arrayOf(
                                                0f to backgroundColor,
                                                0.22f to backgroundColor.copy(alpha = PARAGRAPH_FADE_STRONG_ALPHA),
                                                0.5f to backgroundColor.copy(alpha = PARAGRAPH_FADE_MID_ALPHA),
                                                0.78f to backgroundColor.copy(alpha = PARAGRAPH_FADE_SOFT_ALPHA),
                                                1f to backgroundColor.copy(alpha = 0f),
                                            ),
                                    ),
                                ),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(PARAGRAPH_FADE_HEIGHT)
                                .align(Alignment.BottomCenter)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colorStops =
                                            arrayOf(
                                                0f to backgroundColor.copy(alpha = 0f),
                                                0.22f to backgroundColor.copy(alpha = PARAGRAPH_FADE_SOFT_ALPHA),
                                                0.5f to backgroundColor.copy(alpha = PARAGRAPH_FADE_MID_ALPHA),
                                                0.78f to backgroundColor.copy(alpha = PARAGRAPH_FADE_STRONG_ALPHA),
                                                1f to backgroundColor,
                                            ),
                                    ),
                                ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RsvpPositionGuide(context: RsvpUiContext) {
    val runtime = context.runtime
    val visible =
        runtime.showQuickSettings || runtime.isAdjustingPosition || runtime.isPositioningMode

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
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
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(POSITION_GUIDE_HEIGHT)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = POSITIONING_LINE_ALPHA),
                    ),
            )
        }
    }
}

private fun resolveFontFamily(fontFamily: RsvpFontFamily): FontFamily =
    when (fontFamily) {
        RsvpFontFamily.INTER -> InterFontFamily
        RsvpFontFamily.ROBOTO -> RobotoFontFamily
    }

private fun resolveFontWeight(fontWeight: RsvpFontWeight): FontWeight =
    when (fontWeight) {
        RsvpFontWeight.LIGHT -> FontWeight.Light
        RsvpFontWeight.NORMAL -> FontWeight.Normal
        RsvpFontWeight.MEDIUM -> FontWeight.Medium
    }
