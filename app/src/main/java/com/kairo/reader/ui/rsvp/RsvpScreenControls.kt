@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.rsvp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.reader.R
import com.kairo.reader.core.model.nearestWordIndex

private data class PausedPreviewContent(val paragraph: AnnotatedString, val focusWord: String,)

/** [controlsModifier] targets the tutorial-highlighted playback row inside the controls panel. */
@Suppress("ModifierParameter")
@Composable
internal fun BoxScope.RsvpBottomControls(
    context: RsvpUiContext,
    speedIndicatorText: String,
    controlsModifier: Modifier = Modifier,
) {
    val runtime = context.runtime

    AnimatedVisibility(
        visible = runtime.showControls,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        BoxWithConstraints(
            modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(CONTROLS_OUTER_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            val compactLandscape = maxWidth > maxHeight && maxHeight <= 480.dp
            if (compactLandscape) {
                RsvpCompactBottomControls(
                    context = context,
                    speedText = speedIndicatorText,
                    controlsModifier = controlsModifier,
                )
            } else {
                RsvpDefaultBottomControls(
                    context = context,
                    speedText = speedIndicatorText,
                    controlsModifier = controlsModifier,
                )
            }
        }
    }
}

/** [controlsModifier] is forwarded to the playback row rather than this panel's root column. */
@Suppress("ModifierParameter")
@Composable
private fun RsvpDefaultBottomControls(
    context: RsvpUiContext,
    speedText: String,
    controlsModifier: Modifier,
) {
    val runtime = context.runtime
    val previewContent = rememberPausedPreviewContent(context, compact = false)
    Column(
        modifier =
        Modifier
            .widthIn(max = CONTROLS_MAX_WIDTH)
            .fillMaxWidth(CONTROLS_WIDTH_FRACTION)
            .clip(RoundedCornerShape(CONTROLS_CORNER_RADIUS))
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = CONTROLS_BACKGROUND_ALPHA),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = CONTROLS_BORDER_ALPHA),
                shape = RoundedCornerShape(CONTROLS_CORNER_RADIUS),
            )
            .padding(CONTROLS_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (
            previewContent != null &&
            shouldShowPausePreview(
                isPlaying = runtime.isPlaying,
                showControls = runtime.showControls,
                showQuickSettings = runtime.showQuickSettings,
                isExiting = runtime.isExiting,
            )
        ) {
            RsvpPausedPreview(
                content = previewContent,
                context = context,
                compact = false,
            )
            Spacer(modifier = Modifier.height(PAUSE_PREVIEW_BOTTOM_SPACING))
        }
        RsvpControlsProgress(context)
        Spacer(modifier = Modifier.height(CONTROLS_SPACER))
        RsvpPlaybackControlsRow(
            context = context,
            modifier = controlsModifier,
        )
        Spacer(modifier = Modifier.height(CONTROLS_SPACER))
        RsvpPlaybackInfoPills(
            progressText =
            stringResource(
                R.string.rsvp_frame_progress,
                runtime.frameIndex + 1,
                context.frameState.frames.size,
            ),
            speedText = speedText,
        )
        Spacer(modifier = Modifier.height(CONTROLS_HINT_SPACER))
        Text(
            stringResource(R.string.rsvp_tap_to_resume),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = RESUME_TEXT_ALPHA),
        )
    }
}

/** [controlsModifier] is forwarded to the playback row rather than this panel's root column. */
@Suppress("ModifierParameter")
@Composable
private fun RsvpCompactBottomControls(
    context: RsvpUiContext,
    speedText: String,
    controlsModifier: Modifier,
) {
    val runtime = context.runtime
    val previewContent = rememberPausedPreviewContent(context, compact = true)
    Column(
        modifier =
        Modifier
            .widthIn(max = CONTROLS_MAX_WIDTH)
            .fillMaxWidth(COMPACT_CONTROLS_WIDTH_FRACTION)
            .clip(RoundedCornerShape(CONTROLS_CORNER_RADIUS))
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = CONTROLS_BACKGROUND_ALPHA),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = CONTROLS_BORDER_ALPHA),
                shape = RoundedCornerShape(CONTROLS_CORNER_RADIUS),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (
            previewContent != null &&
            shouldShowPausePreview(
                isPlaying = runtime.isPlaying,
                showControls = runtime.showControls,
                showQuickSettings = runtime.showQuickSettings,
                isExiting = runtime.isExiting,
            )
        ) {
            RsvpPausedPreview(
                content = previewContent,
                context = context,
                compact = true,
            )
            Spacer(modifier = Modifier.height(PAUSE_PREVIEW_COMPACT_BOTTOM_SPACING))
        }
        RsvpControlsProgress(context)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RsvpPlaybackControlsRow(
                context = context,
                compact = true,
                modifier = controlsModifier,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RsvpPlaybackInfoPills(
                    progressText =
                    stringResource(
                        R.string.rsvp_frame_progress,
                        runtime.frameIndex + 1,
                        context.frameState.frames.size,
                    ),
                    speedText = speedText,
                    compact = true,
                )
                Text(
                    stringResource(R.string.rsvp_tap_to_resume),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = RESUME_TEXT_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val COMPACT_CONTROLS_WIDTH_FRACTION = 0.88f

@Composable
private fun rememberPausedPreviewContent(
    context: RsvpUiContext,
    compact: Boolean,
): PausedPreviewContent? {
    if (context.presentationMode != ReadingPresentationMode.RSVP) return null
    val runtime = context.runtime
    val tokens = context.state.book.tokens
    if (tokens.isEmpty()) return null

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
    val focusWord = tokens.getOrNull(highlightIndex)?.text.orEmpty()
    val paragraph =
        remember(tokens, highlightIndex) {
            resolveRsvpParagraph(tokens, highlightIndex)
        } ?: return null
    val highlightStyle =
        SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            background = MaterialTheme.colorScheme.primary.copy(alpha = PAUSE_PREVIEW_HIGHLIGHT_BACKGROUND_ALPHA),
        )

    return remember(paragraph, highlightIndex, highlightStyle, focusWord, compact) {
        PausedPreviewContent(
            paragraph =
            buildRsvpParagraphAnnotatedText(
                paragraph = paragraph,
                highlightIndex = highlightIndex,
                highlightStyle = highlightStyle,
                maxWords =
                if (compact) {
                    PAUSE_PREVIEW_COMPACT_WINDOW_WORDS
                } else {
                    PARAGRAPH_PREVIEW_WINDOW_WORDS
                },
                highlightWindowFraction = PAUSE_PREVIEW_HIGHLIGHT_FRACTION,
            ),
            focusWord = focusWord,
        )
    }
}

@Composable
private fun RsvpPausedPreview(
    content: PausedPreviewContent,
    context: RsvpUiContext,
    compact: Boolean,
) {
    val shape = RoundedCornerShape(PAUSE_PREVIEW_CORNER_RADIUS)
    val modifier =
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PAUSE_PREVIEW_SURFACE_ALPHA))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = PAUSE_PREVIEW_BORDER_ALPHA),
                shape = shape,
            ).padding(
                horizontal =
                if (compact) {
                    PAUSE_PREVIEW_COMPACT_PADDING_HORIZONTAL
                } else {
                    PAUSE_PREVIEW_PADDING_HORIZONTAL
                },
                vertical =
                if (compact) {
                    PAUSE_PREVIEW_COMPACT_PADDING_VERTICAL
                } else {
                    PAUSE_PREVIEW_PADDING_VERTICAL
                },
            )

    if (compact) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(PAUSE_PREVIEW_FOCUS_SPACING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RsvpPausePreviewFocusWord(
                focusWord = content.focusWord,
                context = context,
                compact = true,
                modifier =
                Modifier
                    .widthIn(
                        min = PAUSE_PREVIEW_COMPACT_FOCUS_MIN_WIDTH,
                        max = PAUSE_PREVIEW_COMPACT_FOCUS_MAX_WIDTH,
                    ),
            )
            RsvpPausePreviewContextText(
                text = content.paragraph,
                context = context,
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Column(modifier = modifier) {
            RsvpPausePreviewFocusWord(
                focusWord = content.focusWord,
                context = context,
                compact = false,
            )
            Spacer(modifier = Modifier.height(PAUSE_PREVIEW_FOCUS_SPACING))
            RsvpPausePreviewContextText(
                text = content.paragraph,
                context = context,
                compact = false,
            )
        }
    }
}

@Composable
private fun RsvpPausePreviewFocusWord(
    focusWord: String,
    context: RsvpUiContext,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val runtime = context.runtime
    val fontSizeSp =
        if (compact) {
            (runtime.currentFontSizeSp * PAUSE_PREVIEW_COMPACT_FOCUS_FONT_SCALE)
                .coerceIn(MIN_PAUSE_PREVIEW_FOCUS_SIZE_SP, MAX_PAUSE_PREVIEW_COMPACT_FOCUS_SIZE_SP)
        } else {
            (runtime.currentFontSizeSp * PAUSE_PREVIEW_FOCUS_FONT_SCALE)
                .coerceIn(MIN_PAUSE_PREVIEW_FOCUS_SIZE_SP, MAX_PAUSE_PREVIEW_FOCUS_SIZE_SP)
        }

    Text(
        text = focusWord,
        style =
        MaterialTheme.typography.headlineSmall.copy(
            fontSize = fontSizeSp.sp,
            fontFamily = resolveFontFamily(runtime.currentFontFamily),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = PAUSE_PREVIEW_FOCUS_ALPHA),
            lineHeight = (fontSizeSp * 1.08f).sp,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun RsvpPausePreviewContextText(
    text: AnnotatedString,
    context: RsvpUiContext,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val runtime = context.runtime
    val fontSizeSp =
        if (compact) {
            (runtime.currentFontSizeSp * PAUSE_PREVIEW_COMPACT_FONT_SCALE)
                .coerceIn(MIN_PAUSE_PREVIEW_FONT_SIZE_SP, MAX_PAUSE_PREVIEW_COMPACT_FONT_SIZE_SP)
        } else {
            (runtime.currentFontSizeSp * PAUSE_PREVIEW_FONT_SCALE)
                .coerceIn(MIN_PAUSE_PREVIEW_FONT_SIZE_SP, MAX_PAUSE_PREVIEW_FONT_SIZE_SP)
        }
    val lineHeightSp = fontSizeSp * PAUSE_PREVIEW_LINE_HEIGHT_MULTIPLIER
    val lineCount = if (compact) PAUSE_PREVIEW_COMPACT_LINES else PAUSE_PREVIEW_LINES
    val previewHeight = with(LocalDensity.current) { lineHeightSp.sp.toDp() * lineCount.toFloat() }

    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(previewHeight)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style =
            MaterialTheme.typography.bodyMedium.copy(
                fontSize = fontSizeSp.sp,
                fontFamily = resolveFontFamily(runtime.currentFontFamily),
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = PAUSE_PREVIEW_TEXT_ALPHA),
                lineHeight = lineHeightSp.sp,
            ),
            overflow = TextOverflow.Clip,
            maxLines = lineCount,
            minLines = lineCount,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun shouldShowPausePreview(
    isPlaying: Boolean,
    showControls: Boolean,
    showQuickSettings: Boolean,
    isExiting: Boolean,
): Boolean =
    !isExiting &&
        !isPlaying &&
        showControls &&
        !showQuickSettings

@Composable
private fun RsvpPlaybackInfoPills(
    progressText: String,
    speedText: String,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else CONTROLS_INFO_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RsvpPlaybackInfoPill(
            label = stringResource(R.string.rsvp_playback_position_label),
            value = progressText,
            modifier = Modifier.weight(1f),
            compact = compact,
        )
        RsvpPlaybackInfoPill(
            label = stringResource(R.string.rsvp_playback_speed_label),
            value = speedText,
            modifier = Modifier.weight(1f),
            compact = compact,
        )
    }
}

@Composable
private fun RsvpPlaybackInfoPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier =
        modifier
            .clip(RoundedCornerShape(CONTROLS_INFO_PILL_CORNER_RADIUS))
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CONTROLS_PILL_BACKGROUND_ALPHA),
            )
            .padding(
                horizontal =
                if (compact) {
                    10.dp
                } else {
                    CONTROLS_INFO_PILL_PADDING_HORIZONTAL
                },
                vertical =
                if (compact) {
                    6.dp
                } else {
                    CONTROLS_INFO_PILL_PADDING_VERTICAL
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CONTROLS_PILL_LABEL_ALPHA),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CONTROLS_PILL_VALUE_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RsvpControlsProgress(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val progress = (runtime.frameIndex + 1).toFloat() / frames.size.coerceAtLeast(1).toFloat()

    LinearProgressIndicator(
        progress = { progress },
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CONTROLS_PROGRESS_CORNER_RADIUS))
            .height(CONTROLS_PROGRESS_HEIGHT),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PROGRESS_TRACK_ALPHA),
    )
}

@Composable
private fun RsvpPlaybackControlsRow(
    context: RsvpUiContext,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val runtime = context.runtime
    val rowSpacing = if (compact) 18.dp else CONTROLS_ROW_SPACING
    val skipIconSize = if (compact) 28.dp else SKIP_ICON_SIZE
    val playButtonSize = if (compact) 54.dp else PLAY_BUTTON_SIZE
    val playIconSize = if (compact) 30.dp else PLAY_ICON_SIZE

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            replayPreviousPhrase(context)
        }) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.content_desc_replay_phrase),
                modifier = Modifier.size(skipIconSize),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        IconButton(
            onClick = {
                if (runtime.isPlaying) {
                    runtime.isPlaying = false
                } else if (!runtime.completed) {
                    runtime.showControls = false
                    resumePlayback(runtime)
                }
                context.haptics.onFrameStep()
            },
            modifier =
            Modifier
                .size(playButtonSize)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Icon(
                if (runtime.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription =
                stringResource(
                    if (runtime.isPlaying) {
                        R.string.content_desc_pause
                    } else {
                        R.string.content_desc_play
                    },
                ),
                modifier = Modifier.size(playIconSize),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }

        IconButton(onClick = {
            advanceFrame(context)
            context.haptics.onFrameStep()
        }) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.content_desc_next),
                modifier = Modifier.size(skipIconSize),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
