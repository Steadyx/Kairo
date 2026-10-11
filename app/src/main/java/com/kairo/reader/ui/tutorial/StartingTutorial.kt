package com.kairo.reader.ui.tutorial

import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R

enum class StartingTutorialRoute { LIBRARY, SETTINGS, READER, RSVP }

object StartingTutorialTargetIds {
    const val LIBRARY_IMPORT = "library_import"
    const val LIBRARY_TABS = "library_tabs"
    const val LIBRARY_SETTINGS = "library_settings"
    const val SETTINGS_THEME = "settings_theme"
    const val SETTINGS_CHANGELOG = "settings_changelog"
    const val SETTINGS_LANGUAGE = "settings_language"
    const val SETTINGS_RSVP = "settings_rsvp"
    const val SETTINGS_READER = "settings_reader"
    const val SETTINGS_FOCUS = "settings_focus"
    const val SETTINGS_TUTORIAL = "settings_tutorial"
    const val READER_NAVIGATION = "reader_navigation"
    const val READER_MENU = "reader_menu"
    const val READER_RSVP_LAUNCHER = "reader_rsvp_launcher"
    const val READER_MENU_SETTINGS = "reader_menu_settings"
    const val RSVP_TOP_SETTINGS = "rsvp_top_settings"
    const val RSVP_PLAYBACK_CONTROLS = "rsvp_playback_controls"
    const val RSVP_SURFACE = "rsvp_surface"
    const val RSVP_QUICK_SETTINGS = "rsvp_quick_settings"
    const val RSVP_SETTINGS_ROW = "rsvp_settings_row"
    const val RSVP_EXIT = "rsvp_exit"
}

data class StartingTutorialStep(
    val route: StartingTutorialRoute,
    val targetId: String? = null,
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
)

data class StartingTutorialOverlayState(
    val step: StartingTutorialStep,
    val index: Int,
    val totalSteps: Int,
    val onViewChangelog: (() -> Unit)? = null,
) {
    val isFirstStep: Boolean = index == 0
    val isLastStep: Boolean = index == totalSteps - 1
}

fun startingTutorialSteps(
    includeReaderAndRsvp: Boolean,
): List<StartingTutorialStep> =
    buildList {
        add(
            StartingTutorialStep(
                route = StartingTutorialRoute.LIBRARY,
                titleRes = R.string.starting_tutorial_intro_title,
                bodyRes = R.string.starting_tutorial_intro_body,
            ),
        )
        add(
            StartingTutorialStep(
                route = StartingTutorialRoute.LIBRARY,
                targetId = StartingTutorialTargetIds.LIBRARY_IMPORT,
                titleRes = R.string.starting_tutorial_import_title,
                bodyRes = R.string.starting_tutorial_import_body,
            ),
        )
        add(
            StartingTutorialStep(
                route = StartingTutorialRoute.LIBRARY,
                targetId = StartingTutorialTargetIds.LIBRARY_TABS,
                titleRes = R.string.starting_tutorial_tabs_title,
                bodyRes = R.string.starting_tutorial_tabs_body,
            ),
        )
        add(
            StartingTutorialStep(
                route = StartingTutorialRoute.LIBRARY,
                targetId = StartingTutorialTargetIds.LIBRARY_SETTINGS,
                titleRes = R.string.starting_tutorial_library_settings_title,
                bodyRes = R.string.starting_tutorial_library_settings_body,
            ),
        )
        add(
            StartingTutorialStep(
                route = StartingTutorialRoute.SETTINGS,
                targetId = StartingTutorialTargetIds.SETTINGS_LANGUAGE,
                titleRes = R.string.starting_tutorial_language_title,
                bodyRes = R.string.starting_tutorial_language_body,
            ),
        )
        add(
            StartingTutorialStep(
                route = StartingTutorialRoute.SETTINGS,
                targetId = StartingTutorialTargetIds.SETTINGS_RSVP,
                titleRes = R.string.starting_tutorial_rsvp_title,
                bodyRes = R.string.starting_tutorial_rsvp_body,
            ),
        )
        add(
            StartingTutorialStep(
                route = StartingTutorialRoute.SETTINGS,
                targetId = StartingTutorialTargetIds.SETTINGS_READER,
                titleRes = R.string.starting_tutorial_reader_title,
                bodyRes = R.string.starting_tutorial_reader_body,
            ),
        )
        add(
            StartingTutorialStep(
                route = StartingTutorialRoute.SETTINGS,
                targetId = StartingTutorialTargetIds.SETTINGS_FOCUS,
                titleRes = R.string.starting_tutorial_focus_title,
                bodyRes = R.string.starting_tutorial_focus_body,
            ),
        )
        add(
            StartingTutorialStep(
                route = StartingTutorialRoute.SETTINGS,
                targetId = StartingTutorialTargetIds.SETTINGS_TUTORIAL,
                titleRes = R.string.starting_tutorial_replay_title,
                bodyRes = R.string.starting_tutorial_replay_body,
            ),
        )

        if (includeReaderAndRsvp) {
            add(
                StartingTutorialStep(
                    route = StartingTutorialRoute.READER,
                    targetId = StartingTutorialTargetIds.READER_NAVIGATION,
                    titleRes = R.string.starting_tutorial_reader_navigation_title,
                    bodyRes = R.string.starting_tutorial_reader_navigation_body,
                ),
            )
            add(
                StartingTutorialStep(
                    route = StartingTutorialRoute.READER,
                    targetId = StartingTutorialTargetIds.READER_RSVP_LAUNCHER,
                    titleRes = R.string.starting_tutorial_reader_launch_title,
                    bodyRes = R.string.starting_tutorial_reader_launch_body,
                ),
            )
            add(
                StartingTutorialStep(
                    route = StartingTutorialRoute.READER,
                    targetId = StartingTutorialTargetIds.READER_MENU,
                    titleRes = R.string.starting_tutorial_reader_menu_title,
                    bodyRes = R.string.starting_tutorial_reader_menu_body,
                ),
            )
            add(
                StartingTutorialStep(
                    route = StartingTutorialRoute.READER,
                    targetId = StartingTutorialTargetIds.READER_MENU_SETTINGS,
                    titleRes = R.string.starting_tutorial_reader_menu_settings_title,
                    bodyRes = R.string.starting_tutorial_reader_menu_settings_body,
                ),
            )
            add(
                StartingTutorialStep(
                    route = StartingTutorialRoute.RSVP,
                    targetId = StartingTutorialTargetIds.RSVP_PLAYBACK_CONTROLS,
                    titleRes = R.string.starting_tutorial_rsvp_playback_title,
                    bodyRes = R.string.starting_tutorial_rsvp_playback_body,
                ),
            )
            add(
                StartingTutorialStep(
                    route = StartingTutorialRoute.RSVP,
                    targetId = StartingTutorialTargetIds.RSVP_TOP_SETTINGS,
                    titleRes = R.string.starting_tutorial_rsvp_topbar_title,
                    bodyRes = R.string.starting_tutorial_rsvp_topbar_body,
                ),
            )
            add(
                StartingTutorialStep(
                    route = StartingTutorialRoute.RSVP,
                    targetId = StartingTutorialTargetIds.RSVP_SURFACE,
                    titleRes = R.string.starting_tutorial_rsvp_gestures_title,
                    bodyRes = R.string.starting_tutorial_rsvp_gestures_body,
                ),
            )
            add(
                StartingTutorialStep(
                    route = StartingTutorialRoute.RSVP,
                    targetId = StartingTutorialTargetIds.RSVP_EXIT,
                    titleRes = R.string.starting_tutorial_rsvp_exit_title,
                    bodyRes = R.string.starting_tutorial_rsvp_exit_body,
                ),
            )
            add(
                StartingTutorialStep(
                    route = StartingTutorialRoute.RSVP,
                    targetId = StartingTutorialTargetIds.RSVP_SETTINGS_ROW,
                    titleRes = R.string.starting_tutorial_rsvp_settings_title,
                    bodyRes = R.string.starting_tutorial_rsvp_settings_body,
                ),
            )
        }
    }

fun Modifier.startingTutorialTarget(
    targetId: String,
    onBoundsChanged: (String, Rect) -> Unit,
): Modifier =
    onGloballyPositioned { coordinates ->
        onBoundsChanged(targetId, coordinates.boundsInRoot())
    }

@Composable
fun StartingTutorialOverlay(
    state: StartingTutorialOverlayState,
    targetBounds: Rect?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardScrollState = rememberScrollState()
    LaunchedEffect(state.index) { cardScrollState.scrollTo(0) }
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val highlightPadding = 12.dp
    val cornerRadius = 20.dp
    val strokeWidth = 2.dp
    val highlightPaddingPx = with(density) { highlightPadding.toPx() }
    val screenWidthPx = windowInfo.containerSize.width.toFloat()
    val screenHeightPx = windowInfo.containerSize.height.toFloat()
    val highlightColor = MaterialTheme.colorScheme.primary
    val cardPlacementPrefersTop =
        targetBounds != null && targetBounds.center.y > (screenHeightPx * TOP_CARD_PLACEMENT_THRESHOLD)
    val highlightRect =
        targetBounds?.expand(highlightPaddingPx)
            ?.coerceInside(screenWidthPx, screenHeightPx)
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.74f)

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (highlightRect == null) {
                drawRect(color = scrimColor)
            } else {
                if (highlightRect.top > 0f) {
                    drawRect(
                        color = scrimColor,
                        topLeft = Offset.Zero,
                        size = Size(size.width, highlightRect.top),
                    )
                }
                if (highlightRect.bottom < size.height) {
                    drawRect(
                        color = scrimColor,
                        topLeft = Offset(0f, highlightRect.bottom),
                        size = Size(size.width, size.height - highlightRect.bottom),
                    )
                }
                if (highlightRect.left > 0f) {
                    drawRect(
                        color = scrimColor,
                        topLeft = Offset(0f, highlightRect.top),
                        size = Size(highlightRect.left, highlightRect.height),
                    )
                }
                if (highlightRect.right < size.width) {
                    drawRect(
                        color = scrimColor,
                        topLeft = Offset(highlightRect.right, highlightRect.top),
                        size = Size(size.width - highlightRect.right, highlightRect.height),
                    )
                }
                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(highlightRect.left, highlightRect.top),
                    size = Size(highlightRect.width, highlightRect.height),
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                    style = Stroke(width = strokeWidth.toPx()),
                )
            }
        }

        Surface(
            modifier =
            Modifier
                .align(
                    if (cardPlacementPrefersTop) {
                        Alignment.TopCenter
                    } else {
                        Alignment.BottomCenter
                    },
                ).padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier.verticalScroll(cardScrollState).padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text =
                    stringResource(
                        R.string.starting_tutorial_progress,
                        state.index + 1,
                        state.totalSteps,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(state.step.titleRes),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(state.step.bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TutorialHint(state.step)
                state.onViewChangelog?.let { openChangelog ->
                    TextButton(onClick = openChangelog) {
                        Text(stringResource(R.string.whats_new_view_changelog))
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.action_skip))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!state.isFirstStep) {
                            OutlinedButton(onClick = onPrevious) {
                                Text(stringResource(R.string.action_previous))
                            }
                        }
                        Button(onClick = onNext) {
                            Text(
                                stringResource(
                                    if (state.isLastStep) {
                                        R.string.action_done
                                    } else {
                                        R.string.action_next
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val TOP_CARD_PLACEMENT_THRESHOLD = 0.58f

private fun Rect.expand(padding: Float): Rect =
    Rect(
        left = left - padding,
        top = top - padding,
        right = right + padding,
        bottom = bottom + padding,
    )

private fun Rect.coerceInside(maxWidth: Float, maxHeight: Float): Rect =
    Rect(
        left = left.coerceIn(0f, maxWidth),
        top = top.coerceIn(0f, maxHeight),
        right = right.coerceIn(0f, maxWidth),
        bottom = bottom.coerceIn(0f, maxHeight),
    )

@Composable
private fun TutorialHint(step: StartingTutorialStep) {
    when (step.targetId) {
        StartingTutorialTargetIds.RSVP_SURFACE -> {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedSwipeHint(
                    axis = TutorialHintAxis.Horizontal,
                    label = stringResource(R.string.starting_tutorial_hint_scrub),
                )
                AnimatedSwipeHint(
                    axis = TutorialHintAxis.Vertical,
                    label = stringResource(R.string.starting_tutorial_hint_tempo),
                )
            }
        }

        StartingTutorialTargetIds.RSVP_EXIT -> {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulsingHoldHint(label = stringResource(R.string.starting_tutorial_hint_hold_exit))
                AnimatedCloseHint(label = stringResource(R.string.starting_tutorial_hint_close_exit))
            }
        }

        else -> Unit
    }
}

private enum class TutorialHintAxis { Horizontal, Vertical }

@Composable
private fun AnimatedSwipeHint(
    axis: TutorialHintAxis,
    label: String,
) {
    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "swipe_hint")
    val offsetProgress =
        transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "swipe_offset",
        )
    val offsetDp = 10.dp * offsetProgress.value
    val offsetPx = with(density) { offsetDp.toPx() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
            Modifier
                .size(width = 96.dp, height = 52.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (axis == TutorialHintAxis.Horizontal) {
                Row(
                    modifier = Modifier.graphicsLayer { translationX = offsetPx },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = primaryColor,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = primaryColor,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.graphicsLayer { translationY = offsetPx },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = primaryColor,
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = primaryColor,
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PulsingHoldHint(label: String) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "hold_hint")
    val ringScale =
        transition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1.25f,
            animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "hold_ring_scale",
        )
    val ringAlpha =
        transition.animateFloat(
            initialValue = 0.65f,
            targetValue = 0.05f,
            animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "hold_ring_alpha",
        )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier =
                Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = ringScale.value
                        scaleY = ringScale.value
                        alpha = ringAlpha.value
                    },
            ) {
                drawCircle(
                    color = primaryColor,
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
            Canvas(modifier = Modifier.size(22.dp)) {
                drawCircle(color = primaryColor)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AnimatedCloseHint(label: String) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "close_hint")
    val alpha =
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "close_alpha",
        )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier =
                Modifier
                    .padding(12.dp)
                    .graphicsLayer { this.alpha = alpha.value },
                tint = primaryColor,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
