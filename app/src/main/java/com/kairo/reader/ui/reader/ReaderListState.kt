package com.kairo.reader.ui.reader

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

internal data class ReaderListState(val listState: LazyListState, val invertedScrollCommands: MutableSharedFlow<InvertedScrollCommand>,)

@Composable
internal fun rememberReaderListState(
    listStateKey: String,
    focusListIndex: Int,
    listItemCount: Int,
    invertedScroll: Boolean,
): ReaderListState {
    val safeIndex =
        focusListIndex.coerceIn(
            0,
            (listItemCount - 1).coerceAtLeast(0),
        )
    val listState =
        key(listStateKey) {
            rememberLazyListState(
                initialFirstVisibleItemIndex = safeIndex,
            )
        }

    val invertedScrollCommands =
        remember(listStateKey) {
            MutableSharedFlow<InvertedScrollCommand>(
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }

    LaunchedEffect(listStateKey, invertedScroll) {
        if (!invertedScroll) return@LaunchedEffect
        var flingJob: Job? = null
        invertedScrollCommands.collect { command ->
            when (command) {
                is InvertedScrollCommand.Drag -> {
                    flingJob?.cancel()
                    listState.scrollBy(command.dy)
                }

                is InvertedScrollCommand.Fling -> {
                    flingJob?.cancel()
                    val newJob =
                        launch {
                            performInvertedFling(listState, command.velocityY)
                        }
                    flingJob = newJob
                    newJob.invokeOnCompletion {
                        if (flingJob === newJob) {
                            flingJob = null
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(listStateKey, listItemCount, safeIndex) {
        // Search/TOC navigation can change focus without changing the current page.
        // A word tapped in an already visible paragraph should not move that paragraph.
        if (listItemCount > 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == safeIndex }) {
            listState.scrollToItem(safeIndex)
        }
    }

    return ReaderListState(
        listState = listState,
        invertedScrollCommands = invertedScrollCommands,
    )
}
