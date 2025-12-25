package com.example.kairo.ui.reader

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

internal data class ReaderListState(
    val listState: LazyListState,
    val invertedScrollCommands: MutableSharedFlow<InvertedScrollCommand>,
)

@Composable
internal fun rememberReaderListState(
    listStateKey: String,
    focusListIndex: Int,
    displayBlocks: List<ReaderBlock>,
    invertedScroll: Boolean,
): ReaderListState {
    val listState =
        key(listStateKey) {
            rememberLazyListState(
                initialFirstVisibleItemIndex = focusListIndex,
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

    LaunchedEffect(focusListIndex, listStateKey) {
        if (displayBlocks.isNotEmpty() && listState.firstVisibleItemIndex != focusListIndex) {
            listState.scrollToItem(focusListIndex)
        }
    }

    return ReaderListState(
        listState = listState,
        invertedScrollCommands = invertedScrollCommands,
    )
}
