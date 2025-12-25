package com.example.kairo.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.nearestWordIndex
import kotlinx.coroutines.launch

@Composable
internal fun ReaderRsvpLauncher(
    tokens: List<Token>,
    focusIndex: Int,
    invertedScroll: Boolean,
    listState: LazyListState,
    focusListIndex: Int,
    progressFraction: Float,
    onFocusChange: (Int) -> Unit,
    onStartRsvp: (Int) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
        Modifier
            .pointerInput(focusListIndex) {
                detectTapGestures(
                    onLongPress = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(focusListIndex)
                        }
                    },
                )
            }.pointerInput(tokens, focusIndex, invertedScroll) {
                val thresholdPx = 22f
                var gestureFocusIndex = 0
                detectVerticalDragGestures(
                    onDragStart = {
                        dragAccumulator = 0f
                        gestureFocusIndex =
                            if (tokens.isNotEmpty()) {
                                tokens.nearestWordIndex(
                                    focusIndex
                                ).coerceIn(0, tokens.lastIndex)
                            } else {
                                0
                            }
                    },
                    onDragEnd = { dragAccumulator = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        if (tokens.isEmpty()) return@detectVerticalDragGestures
                        dragAccumulator += dragAmount
                        val steps = (dragAccumulator / thresholdPx).toInt()
                        if (steps == 0) return@detectVerticalDragGestures

                        // Swipe up (negative drag) moves forward by default.
                        val rawDirection = -steps
                        val effectiveDirection = if (invertedScroll) -rawDirection else rawDirection
                        val next =
                            tokens.nearestWordIndex(
                                (gestureFocusIndex + effectiveDirection).coerceIn(
                                    0,
                                    tokens.lastIndex
                                ),
                            )
                        gestureFocusIndex = next
                        onFocusChange(next)
                        dragAccumulator -= steps * thresholdPx
                    },
                )
            },
    ) {
        CircularProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier.size(76.dp),
            strokeWidth = 4.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.60f),
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
        )
        FloatingActionButton(
            onClick = {
                val safeIndex = tokens.nearestWordIndex(focusIndex)
                onStartRsvp(safeIndex)
            },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Start RSVP")
        }
    }
}
