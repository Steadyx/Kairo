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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.nearestWordIndex
import com.example.kairo.ui.theme.InterFontFamily
import com.example.kairo.ui.theme.RobotoFontFamily
import kotlin.math.abs
import kotlin.math.roundToLong

internal enum class PreviewSide { ABOVE, BELOW }

internal data class ParagraphPreviewPlacement(
    val side: PreviewSide,
    val topPx: Float,
)

private data class PreviewCandidate(
    val side: PreviewSide,
    val topPx: Float,
    val overlapPx: Float,
    val score: Float,
)

@Composable
internal fun RsvpPlaybackSurface(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val currentFrame = frames.getOrNull(runtime.frameIndex)
    val bottomChromeInset = rememberBottomChromeInset(runtime)
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
        RsvpFocusWord(context, currentFrame, typography, colors, bottomChromeInset)
        RsvpParagraphPreview(context, typography, colors, bottomChromeInset)
        RsvpPositionGuide(context, bottomChromeInset)
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
    bottomChromeInset: Dp,
) {
    val runtime = context.runtime
    val profile = context.state.profile
    if (frame == null) return

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
    bottomChromeInset: Dp,
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
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomChromeInset),
        ) {
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
            val placement =
                resolveParagraphPreviewPlacement(
                    currentSide = previewSide,
                    isPositioningMode = runtime.isPositioningMode,
                    anchorTop = anchorTop,
                    previewHeightPx = previewHeightPx,
                    preferredOffsetPx = preferredOffsetPx,
                    edgePaddingPx = edgePaddingPx,
                    protectedTop = protectedTop,
                    protectedBottom = protectedBottom,
                    maxTop = (viewportHeightPx - previewHeightPx - edgePaddingPx).coerceAtLeast(edgePaddingPx),
                    switchHysteresisPx = switchHysteresisPx,
                    switchOverlapThresholdPx = switchOverlapThresholdPx,
                )
            previewSide = placement.side
            val resolvedTop = placement.topPx
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

internal fun resolveParagraphPreviewPlacement(
    currentSide: PreviewSide,
    isPositioningMode: Boolean,
    anchorTop: Float,
    previewHeightPx: Float,
    preferredOffsetPx: Float,
    edgePaddingPx: Float,
    protectedTop: Float,
    protectedBottom: Float,
    maxTop: Float,
    switchHysteresisPx: Float,
    switchOverlapThresholdPx: Float,
): ParagraphPreviewPlacement {
    val rawBelowTop = anchorTop + preferredOffsetPx
    val rawAboveTop = anchorTop - preferredOffsetPx
    val defaultBelowTop = rawBelowTop.coerceIn(edgePaddingPx, maxTop)
    val defaultAboveTop = rawAboveTop.coerceIn(edgePaddingPx, maxTop)
    val safeBelowTop = protectedBottom.coerceIn(edgePaddingPx, maxTop)
    val safeAboveTop = (protectedTop - previewHeightPx).coerceIn(edgePaddingPx, maxTop)

    fun overlapAmount(top: Float): Float {
        val bottom = top + previewHeightPx
        return (minOf(bottom, protectedBottom) - maxOf(top, protectedTop)).coerceAtLeast(0f)
    }

    fun buildCandidate(
        side: PreviewSide,
        rawTop: Float,
        defaultTop: Float,
        safeTop: Float,
    ): PreviewCandidate {
        val overlapAtDefault = overlapAmount(defaultTop)
        val top = if (overlapAtDefault > 0f) safeTop else defaultTop
        val overlap = overlapAmount(top)
        val score = (overlap * PREVIEW_OVERLAP_SCORE_MULTIPLIER) + abs(top - rawTop)
        return PreviewCandidate(side = side, topPx = top, overlapPx = overlap, score = score)
    }

    val belowCandidate =
        buildCandidate(
            side = PreviewSide.BELOW,
            rawTop = rawBelowTop,
            defaultTop = defaultBelowTop,
            safeTop = safeBelowTop,
        )
    val aboveCandidate =
        buildCandidate(
            side = PreviewSide.ABOVE,
            rawTop = rawAboveTop,
            defaultTop = defaultAboveTop,
            safeTop = safeAboveTop,
        )

    val resolvedSide =
        if (!isPositioningMode) {
            when {
                aboveCandidate.score + switchHysteresisPx < belowCandidate.score -> PreviewSide.ABOVE
                belowCandidate.score + switchHysteresisPx < aboveCandidate.score -> PreviewSide.BELOW
                rawBelowTop > maxTop && rawAboveTop >= edgePaddingPx -> PreviewSide.ABOVE
                rawAboveTop < edgePaddingPx && rawBelowTop <= maxTop -> PreviewSide.BELOW
                else -> PreviewSide.BELOW
            }
        } else {
            val activeCandidate =
                if (currentSide == PreviewSide.ABOVE) {
                    aboveCandidate
                } else {
                    belowCandidate
                }
            val alternateCandidate =
                if (currentSide == PreviewSide.ABOVE) {
                    belowCandidate
                } else {
                    aboveCandidate
                }
            val shouldSwitch =
                activeCandidate.overlapPx > switchOverlapThresholdPx &&
                    (activeCandidate.overlapPx - alternateCandidate.overlapPx) > switchHysteresisPx
            if (shouldSwitch) {
                alternateCandidate.side
            } else {
                currentSide
            }
        }

    val resolvedTop =
        if (resolvedSide == PreviewSide.ABOVE) {
            aboveCandidate.topPx
        } else {
            belowCandidate.topPx
        }
    return ParagraphPreviewPlacement(side = resolvedSide, topPx = resolvedTop)
}

@Composable
private fun RsvpPositionGuide(
    context: RsvpUiContext,
    bottomChromeInset: Dp,
) {
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

@Composable
private fun rememberBottomChromeInset(runtime: RsvpRuntimeState): Dp {
    if (!runtime.showControls) return 0.dp

    val density = LocalDensity.current
    val navigationBarsInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    return CONTROLS_RESERVED_HEIGHT + navigationBarsInset
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

private const val PREVIEW_OVERLAP_SCORE_MULTIPLIER = 10f
