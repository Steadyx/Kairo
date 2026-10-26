package com.kairo.reader.ui.navigation

import androidx.lifecycle.SavedStateHandle
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.ui.rsvp.RsvpResumePoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RsvpPositionSaverTest {
    @Test
    fun pendingCompletionSurvivesSavedStateRestoration() {
        val state = SavedStateHandle()
        assertFalse(state.hasPendingRsvpCompletion())
        state.setPendingRsvpCompletion(RsvpResumePoint(tokenIndex = 44, chapterIndex = 2, tempoMsPerWord = 150))
        val restored = SavedStateHandle(state.keys().associateWith { key -> state.get<Any>(key) })
        assertTrue(restored.hasPendingRsvpCompletion())
        assertEquals(RsvpResumePoint(tokenIndex = 44, chapterIndex = 2, tempoMsPerWord = 150), restored.pendingRsvpCompletion(2))
    }

    @Test
    fun auxiliaryTimedReadingDoesNotReplaceLinearProgress() {
        assertFalse(shouldPersistTimedChapter(0, null))
        assertTrue(shouldPersistTimedChapter(2, listOf(0, 2)))
        assertFalse(shouldPersistTimedChapter(3, listOf(0, 2)))
    }

    @Test
    fun finalSaveWaitsForOlderWritesBeforeDestinationIsDestroyed() = runTest {
        val destinationScope = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
        val slowWrite = CompletableDeferred<Unit>()
        val writes = mutableListOf<ReadingPosition>()
        val saver = RsvpPositionSaver(destinationScope) { position ->
            if (position.tokenIndex == 1) slowWrite.await()
            writes += position
        }
        val previous = ReadingPosition(BookId("book"), 0, 1)
        val final = previous.copy(tokenIndex = 2, rsvpResumeCursor = 99)
        saver.save(previous)
        var navigated = false
        destinationScope.launch {
            saver.save(final).join()
            navigated = true
            destinationScope.cancel()
        }
        runCurrent()
        assertFalse(navigated)
        assertTrue(writes.isEmpty())
        slowWrite.complete(Unit)
        runCurrent()
        assertTrue(navigated)
        assertEquals(listOf(previous, final), writes)
    }

    @Test
    fun openingBookmarksFlushesTheLastQueuedPosition() = runTest {
        val slowWrite = CompletableDeferred<Unit>()
        var saved = false
        val saver = RsvpPositionSaver(this) {
            slowWrite.await()
            saved = true
        }
        saver.save(ReadingPosition(BookId("book"), 0, 2))
        val flush = launch { saver.flush() }
        runCurrent()
        assertFalse(flush.isCompleted)
        assertFalse(saved)
        slowWrite.complete(Unit)
        flush.join()
        assertTrue(saved)
    }
}
