package com.kairo.reader.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.RsvpConfigConstraints
import com.kairo.reader.core.model.TimedReadingMode
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.nearestWordIndex
import kotlinx.coroutines.launch

internal data class ReaderTimedReadingLauncherState(
    val tokens: List<Token>,
    val focusIndex: Int,
    val invertedScroll: Boolean,
    val listState: androidx.compose.foundation.lazy.LazyListState,
    val focusListIndex: Int,
    val progressFraction: Float,
    val selectedMode: TimedReadingMode,
    val modeSelectionEnabled: Boolean,
)

internal data class ReaderTimedReadingLauncherActions(
    val onFocusChange: (Int) -> Unit,
    val onStartTimedReading: (TimedReadingMode, Int) -> Unit,
    val onSelectTimedReadingMode: (TimedReadingMode, Int) -> Unit,
)

// The launcher is one cohesive gesture and accessibility surface; its inputs are grouped above.
@Suppress("LongMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderTimedReadingLauncher(
    state: ReaderTimedReadingLauncherState,
    actions: ReaderTimedReadingLauncherActions,
    modifier: Modifier = Modifier,
) {
    with(state) {
        val coroutineScope = rememberCoroutineScope()
        var dragAccumulator by remember { mutableFloatStateOf(0f) }
        var modeMenuExpanded by remember { mutableStateOf(false) }
        val safeProgress = progressFraction.coerceIn(0f, 1f)
        val progressPercent = (safeProgress * RsvpConfigConstraints.PERCENT_SCALE).toInt()
        val currentTokens by rememberUpdatedState(tokens)
        val currentFocusIndex by rememberUpdatedState(focusIndex)
        val currentInvertedScroll by rememberUpdatedState(invertedScroll)
        val currentFocusListIndex by rememberUpdatedState(focusListIndex)
        val currentOnFocusChange by rememberUpdatedState(actions.onFocusChange)
        val currentOnStartTimedReading by rememberUpdatedState(actions.onStartTimedReading)
        val currentOnSelectTimedReadingMode by rememberUpdatedState(actions.onSelectTimedReadingMode)
        val selectedModeLabel = selectedMode.label()
        val startActionLabel =
            stringResource(R.string.content_desc_start_timed_reading, selectedModeLabel)
        val chooseModeActionLabel =
            stringResource(R.string.reader_choose_timed_reading_mode, selectedModeLabel)
        val recenterActionLabel = stringResource(R.string.reader_recenter_focus_action)
        val launcherShape = RoundedCornerShape(28.dp)

        fun launchAtFocus(
            mode: TimedReadingMode,
            rememberSelection: Boolean,
        ) {
            val latestTokens = currentTokens
            if (latestTokens.isEmpty()) return
            val safeIndex = latestTokens.nearestWordIndex(currentFocusIndex)
            if (rememberSelection) {
                currentOnSelectTimedReadingMode(mode, safeIndex)
            } else {
                currentOnStartTimedReading(mode, safeIndex)
            }
        }

        fun recenterFocus() {
            val targetListIndex = currentFocusListIndex
            if (targetListIndex < 0) return
            coroutineScope.launch { listState.animateScrollToItem(targetListIndex) }
        }

        LaunchedEffect(modeSelectionEnabled) {
            if (!modeSelectionEnabled) modeMenuExpanded = false
        }

        Surface(
            shape = launcherShape,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier =
            modifier
                .clip(launcherShape)
                .pointerInput(Unit) {
                    val thresholdPx = 22.dp.toPx()
                    var gestureFocusIndex = 0
                    detectVerticalDragGestures(
                        onDragStart = {
                            val latestTokens = currentTokens
                            dragAccumulator = 0f
                            gestureFocusIndex =
                                if (latestTokens.isNotEmpty()) {
                                    latestTokens.nearestWordIndex(currentFocusIndex).coerceIn(
                                        0,
                                        latestTokens.lastIndex,
                                    )
                                } else {
                                    0
                                }
                        },
                        onDragEnd = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            val latestTokens = currentTokens
                            if (latestTokens.isEmpty()) return@detectVerticalDragGestures
                            change.consume()
                            dragAccumulator += dragAmount
                            val steps = (dragAccumulator / thresholdPx).toInt()
                            if (steps == 0) return@detectVerticalDragGestures
                            val rawDirection = -steps
                            val effectiveDirection =
                                if (currentInvertedScroll) -rawDirection else rawDirection
                            val next =
                                latestTokens.moveFocusByWords(gestureFocusIndex, effectiveDirection)
                            gestureFocusIndex = next
                            currentOnFocusChange(next)
                            dragAccumulator -= steps * thresholdPx
                        },
                    )
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                    Modifier
                        .size(52.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 28.dp,
                                bottomStart = 28.dp,
                            ),
                        ).combinedClickable(
                            role = Role.Button,
                            onClickLabel = startActionLabel,
                            onLongClickLabel = recenterActionLabel,
                            onClick = {
                                launchAtFocus(selectedMode, rememberSelection = false)
                            },
                            onLongClick = ::recenterFocus,
                        ),
                ) {
                    CircularProgressIndicator(
                        progress = { safeProgress },
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                Box(
                    modifier =
                    Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                )

                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                        Modifier
                            .heightIn(min = 52.dp)
                            .widthIn(min = 112.dp)
                            .clip(
                                RoundedCornerShape(
                                    topEnd = 28.dp,
                                    bottomEnd = 28.dp,
                                ),
                            ).semantics { stateDescription = selectedModeLabel }
                            .combinedClickable(
                                enabled = modeSelectionEnabled,
                                role = Role.Button,
                                onClickLabel = chooseModeActionLabel,
                                onLongClickLabel = recenterActionLabel,
                                onClick = { modeMenuExpanded = true },
                                onLongClick = ::recenterFocus,
                            ).padding(start = 12.dp, end = 8.dp),
                    ) {
                        Text(
                            text =
                            stringResource(
                                R.string.reader_timed_reading_dock_progress,
                                selectedModeLabel,
                                progressPercent,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        if (modeSelectionEnabled) {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = modeMenuExpanded,
                        onDismissRequest = { modeMenuExpanded = false },
                        modifier = Modifier.widthIn(min = 216.dp),
                    ) {
                        TimedReadingMode.entries.forEach { mode ->
                            val modeLabel = mode.label()
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = modeLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        Text(
                                            text = mode.description(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                trailingIcon =
                                if (mode == selectedMode) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                        )
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    modeMenuExpanded = false
                                    launchAtFocus(mode, rememberSelection = true)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun List<Token>.moveFocusByWords(fromIndex: Int, steps: Int): Int {
    if (isEmpty()) return 0
    var current = nearestWordIndex(fromIndex)
    val direction = steps.compareTo(0)
    val count = kotlin.math.abs(steps.toLong()).coerceAtMost(size.toLong()).toInt()
    repeat(count) {
        var next = current + direction
        while (next in indices && this[next].type != TokenType.WORD) next += direction
        if (next !in indices) return current
        current = next
    }
    return current
}

@Composable
private fun TimedReadingMode.label(): String =
    when (this) {
        TimedReadingMode.RSVP -> stringResource(R.string.timed_reading_mode_rsvp)
        TimedReadingMode.BIONIC -> stringResource(R.string.timed_reading_mode_bionic)
    }

@Composable
private fun TimedReadingMode.description(): String =
    when (this) {
        TimedReadingMode.RSVP -> stringResource(R.string.timed_reading_mode_rsvp_description)
        TimedReadingMode.BIONIC -> stringResource(R.string.timed_reading_mode_bionic_description)
    }
