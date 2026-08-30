package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingSessionMode
import com.kairo.reader.data.sessions.ReadingSessionLocation

@Composable
internal fun TrackTimedReadingSessionLifecycle(
    container: KairoApplication,
    bookId: BookId,
    mode: ReadingSessionMode,
    start: ReadingSessionLocation,
    lifecycleOwner: LifecycleOwner,
    isPlaying: Boolean,
    enabled: Boolean,
) {
    var hasBegun by remember(bookId, mode) { mutableStateOf(false) }
    val latestHasBegun by rememberUpdatedState(hasBegun)
    val latestIsPlaying by rememberUpdatedState(isPlaying)

    LaunchedEffect(bookId, mode, start, enabled) {
        if (!enabled || hasBegun) return@LaunchedEffect
        container.readingSessionCoordinator.beginTimed(
            bookId = bookId,
            mode = mode,
            location = start,
            active =
            isPlaying && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        hasBegun = true
    }
    LaunchedEffect(isPlaying, hasBegun) {
        if (hasBegun) {
            container.readingSessionCoordinator.setTimedActive(
                bookId = bookId,
                mode = mode,
                active =
                isPlaying &&
                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
            )
        }
    }

    DisposableEffect(lifecycleOwner, bookId, mode) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START ->
                        if (latestHasBegun) {
                            container.readingSessionCoordinator.setTimedActive(
                                bookId,
                                mode,
                                latestIsPlaying,
                            )
                        }
                    Lifecycle.Event.ON_STOP ->
                        if (latestHasBegun) {
                            container.readingSessionCoordinator.checkpointTimed(bookId, mode)
                        }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (latestHasBegun) {
                container.readingSessionCoordinator.checkpointTimed(bookId, mode)
            }
        }
    }
}
