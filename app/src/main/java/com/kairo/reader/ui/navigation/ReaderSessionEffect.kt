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
import com.kairo.reader.data.sessions.ReaderProgress
import com.kairo.reader.data.sessions.ReaderWordBasis
import com.kairo.reader.data.sessions.ReadingSessionLocation
import com.kairo.reader.ui.reader.ReaderUiState

@Composable
internal fun RecordReaderSessionEffect(
    container: KairoApplication,
    bookId: BookId,
    hasInitialized: Boolean,
    readerState: ReaderUiState,
    lifecycleOwner: LifecycleOwner,
) {
    var hasBegun by remember(bookId) { mutableStateOf(false) }
    var sessionSnapshot by remember(bookId) { mutableStateOf<ReaderSessionSnapshot?>(null) }
    val latestSessionSnapshot by rememberUpdatedState(sessionSnapshot)
    val chapterData = readerState.chapterData
    val wordBasis = remember(readerState.bookWordCounts) {
        ReaderWordBasis.from(readerState.bookWordCounts)
    }
    LaunchedEffect(
        bookId,
        hasInitialized,
        readerState.chapterIndex,
        readerState.focusIndex,
        chapterData,
        wordBasis,
    ) {
        if (!hasInitialized || chapterData == null || chapterData.tokens.isEmpty()) {
            return@LaunchedEffect
        }
        val safeTokenIndex = readerState.focusIndex.coerceIn(0, chapterData.tokens.lastIndex)
        val location =
            ReadingSessionLocation(
                chapterIndex = readerState.chapterIndex,
                tokenIndex = safeTokenIndex,
                wordIndex = resolveWordIndex(chapterData.wordCountByToken, safeTokenIndex),
            )
        val snapshot = ReaderSessionSnapshot(wordBasis.progress(location))
        sessionSnapshot = snapshot
        if (!hasBegun) {
            container.readingSessionCoordinator.beginReader(
                bookId = bookId,
                progress = snapshot.progress,
                active = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
            )
            hasBegun = true
        } else {
            container.readingSessionCoordinator.moveReader(
                bookId = bookId,
                progress = snapshot.progress,
            )
        }
    }

    DisposableEffect(lifecycleOwner, bookId) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        latestSessionSnapshot?.let { snapshot ->
                            container.readingSessionCoordinator.beginReader(
                                bookId = bookId,
                                progress = snapshot.progress,
                                active = true,
                            )
                        }
                    }
                    Lifecycle.Event.ON_STOP ->
                        if (latestSessionSnapshot != null) {
                            container.readingSessionCoordinator.checkpointReader(bookId)
                        }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (latestSessionSnapshot != null) {
                container.readingSessionCoordinator.checkpointReader(bookId)
            }
        }
    }
}

private data class ReaderSessionSnapshot(val progress: ReaderProgress,)
