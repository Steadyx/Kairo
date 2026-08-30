@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.rsvp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.reader.R
import com.kairo.reader.ui.rememberWindowContainerMetrics
import kotlin.math.roundToInt

@Composable
internal fun BoxScope.RsvpProgressBar(context: RsvpUiContext) {
    val runtime = context.runtime
    if (!shouldShowAmbientProgressBar(
            isPlaying = runtime.isPlaying,
            showControls = runtime.showControls,
            showQuickSettings = runtime.showQuickSettings,
        )
    ) {
        return
    }

    val frames = context.frameState.frames
    val progress = (runtime.frameIndex + 1).toFloat() / frames.size.toFloat()

    LinearProgressIndicator(
        progress = { progress },
        modifier =
        Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(
                horizontal = PROGRESS_HORIZONTAL_PADDING,
                vertical = PROGRESS_BOTTOM_PADDING,
            )
            .clip(RoundedCornerShape(PROGRESS_CORNER_RADIUS))
            .height(PROGRESS_HEIGHT),
        color = MaterialTheme.colorScheme.primary.copy(alpha = PROGRESS_PRIMARY_ALPHA),
        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PROGRESS_TRACK_ALPHA),
    )
}

internal fun shouldShowAmbientProgressBar(
    isPlaying: Boolean,
    showControls: Boolean,
    showQuickSettings: Boolean,
): Boolean =
    !isPlaying && !showControls && !showQuickSettings

/**
 * The modifier parameters target individual tutorial-highlighted buttons, not this composable's
 * root layout.
 */
@Suppress("ModifierParameter")
@Composable
internal fun BoxScope.RsvpTopBar(
    context: RsvpUiContext,
    settingsModifier: Modifier = Modifier,
    closeModifier: Modifier = Modifier,
    homeModifier: Modifier = Modifier,
) {
    val runtime = context.runtime
    val showTopIcons = runtime.isPositioningMode || !runtime.isPlaying
    if (!showTopIcons) return

    Row(
        modifier =
        Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(TOP_BAR_PADDING),
        horizontalArrangement = Arrangement.spacedBy(TOP_BAR_SPACING),
    ) {
        RsvpTopIconButton(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.content_desc_close),
            onClick = { exitAndSavePosition(context) },
            modifier = closeModifier,
        )
        RsvpTopIconButton(
            imageVector = Icons.Default.Home,
            contentDescription = stringResource(R.string.content_desc_go_to_library),
            onClick = { openLibraryAndSavePosition(context) },
            modifier = homeModifier,
        )
    }

    Row(
        modifier =
        Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(TOP_BAR_PADDING),
        horizontalArrangement = Arrangement.spacedBy(TOP_BAR_SPACING),
    ) {
        RsvpTopIconButton(
            imageVector = Icons.Default.Settings,
            contentDescription = stringResource(R.string.content_desc_settings),
            onClick = {
                if (runtime.isPositioningMode) {
                    finishPositioning(context, resumeIfWasPlaying = true)
                } else {
                    runtime.showQuickSettings = !runtime.showQuickSettings
                    if (runtime.showQuickSettings) runtime.showControls = false
                }
            },
            modifier = settingsModifier,
        )
    }
}

@Composable
private fun RsvpTopIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier =
        modifier
            .size(TOP_BAR_BUTTON_SIZE)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = TOP_BAR_BUTTON_BACKGROUND_ALPHA),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = TOP_BAR_BUTTON_BORDER_ALPHA),
                shape = CircleShape,
            ),
    ) {
        Icon(
            imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = TOP_BAR_ICON_ALPHA),
            modifier = Modifier.size(TOP_BAR_ICON_SIZE),
        )
    }
}

/**
 * Top padding for a top-centered indicator pill: the default position, unless the pill would
 * overlap the focus-word band (short landscape viewports, word positioned high), in which case
 * the pill moves just below the word instead — visible feedback without covering the fixation
 * point.
 */
@Composable
private fun wordAwareIndicatorTopPadding(
    context: RsvpUiContext,
    defaultTopPadding: Dp,
    flippedStackOffset: Dp = 0.dp,
): Dp {
    val runtime = context.runtime
    val windowMetrics = rememberWindowContainerMetrics()
    val fontSizeSp = runtime.currentFontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
    val verticalBias = runtime.currentVerticalBias.coerceIn(VERTICAL_BIAS_MIN, VERTICAL_BIAS_MAX)
    val textHeight =
        with(LocalDensity.current) {
            (fontSizeSp * ORP_COLLISION_TEXT_HEIGHT_MULTIPLIER).sp.toDp()
        }
    val wordBandHalfHeight =
        ((ORP_LINE_HEIGHT * 2) + (ORP_POINTER_HEIGHT * 2) + (ORP_TEXT_SPACER * 2) + textHeight) / 2
    val wordCenterY =
        windowMetrics.heightDp * ((ONE_FLOAT + verticalBias) / BIAS_SCALE_FACTOR)
    return resolveIndicatorTopPadding(
        defaultTopPadding = defaultTopPadding,
        flippedStackOffset = flippedStackOffset,
        wordBandTop = wordCenterY - wordBandHalfHeight,
        wordBandBottom = wordCenterY + wordBandHalfHeight,
    )
}

internal fun resolveIndicatorTopPadding(
    defaultTopPadding: Dp,
    flippedStackOffset: Dp,
    wordBandTop: Dp,
    wordBandBottom: Dp,
): Dp {
    val defaultBottom = defaultTopPadding + INDICATOR_APPROX_HEIGHT + INDICATOR_WORD_CLEARANCE
    return if (wordBandTop < defaultBottom) {
        wordBandBottom + INDICATOR_WORD_CLEARANCE + flippedStackOffset
    } else {
        defaultTopPadding
    }
}

@Composable
internal fun BoxScope.RsvpTempoIndicator(
    context: RsvpUiContext,
    indicatorText: String,
) {
    val runtime = context.runtime
    val topPadding = wordAwareIndicatorTopPadding(context, TEMPO_INDICATOR_TOP_PADDING)

    AnimatedVisibility(
        visible = runtime.showTempoIndicator,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Box(
            modifier =
            Modifier
                .padding(top = topPadding)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(
                        alpha = INDICATOR_BACKGROUND_ALPHA,
                    ),
                    RoundedCornerShape(INDICATOR_CORNER_RADIUS),
                ).padding(
                    horizontal = INDICATOR_PADDING_HORIZONTAL,
                    vertical = INDICATOR_PADDING_VERTICAL,
                ),
        ) {
            Text(
                text = indicatorText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
internal fun BoxScope.RsvpFontSizeIndicator(context: RsvpUiContext) {
    val runtime = context.runtime
    val topPadding =
        wordAwareIndicatorTopPadding(
            context = context,
            defaultTopPadding = FONT_SIZE_INDICATOR_TOP_PADDING,
            // Stays below a flipped tempo pill, preserving the same stagger as the defaults.
            flippedStackOffset = FONT_SIZE_INDICATOR_TOP_PADDING - TEMPO_INDICATOR_TOP_PADDING,
        )

    AnimatedVisibility(
        visible = runtime.showFontSizeIndicator,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Box(
            modifier =
            Modifier
                .padding(top = topPadding)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = INDICATOR_BACKGROUND_ALPHA,
                    ),
                    RoundedCornerShape(INDICATOR_CORNER_RADIUS),
                ).padding(
                    horizontal = INDICATOR_PADDING_HORIZONTAL,
                    vertical = INDICATOR_PADDING_VERTICAL,
                ),
        ) {
            Text(
                text =
                stringResource(
                    R.string.format_sp,
                    runtime.currentFontSizeSp.toInt(),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun BoxScope.RsvpPositioningIndicator(context: RsvpUiContext) {
    val runtime = context.runtime

    AnimatedVisibility(
        visible = runtime.isPositioningMode,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Box(
            modifier =
            Modifier
                .padding(top = TEMPO_INDICATOR_TOP_PADDING)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = INDICATOR_BACKGROUND_ALPHA,
                    ),
                    RoundedCornerShape(INDICATOR_CORNER_RADIUS),
                ).clickable { finishPositioning(context, resumeIfWasPlaying = true) }
                .padding(
                    horizontal = POSITIONING_INDICATOR_PADDING_HORIZONTAL,
                    vertical = POSITIONING_INDICATOR_PADDING_VERTICAL,
                ),
        ) {
            Text(
                text = stringResource(R.string.rsvp_positioning_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun BoxScope.RsvpScrubTargetIndicator(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val frameCount = frames.size.coerceAtLeast(1)
    val progressPercent =
        (((runtime.frameIndex + 1).toFloat() / frameCount.toFloat()) * PERCENT_SCALE)
            .roundToInt()
            .coerceIn(0, PERCENT_SCALE.toInt())
    val topPadding = wordAwareIndicatorTopPadding(context, SCRUB_INDICATOR_TOP_PADDING)

    AnimatedVisibility(
        visible = runtime.isScrubbing,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Box(
            modifier =
            Modifier
                .statusBarsPadding()
                .padding(top = topPadding)
                .background(
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = INDICATOR_BACKGROUND_ALPHA),
                    RoundedCornerShape(INDICATOR_CORNER_RADIUS),
                ).padding(
                    horizontal = INDICATOR_PADDING_HORIZONTAL,
                    vertical = INDICATOR_PADDING_VERTICAL,
                ),
        ) {
            Text(
                text = "${runtime.frameIndex + 1}/$frameCount • $progressPercent%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
