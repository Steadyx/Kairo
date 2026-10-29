package com.kairo.reader.ui.navigation

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.data.reading.ReadingPositionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPositionSaverTest {
    @Test
    fun saveDebounced_coalescesDuplicatePositions() =
        runTest {
            val repository = FakeReadingPositionRepository()
            val saver =
                ReaderPositionSaver(
                    scope = this,
                    repository = repository,
                    saveDispatcher = StandardTestDispatcher(testScheduler),
                )
            val position = readingPosition(tokenIndex = 10)

            saver.saveDebounced(position)
            saver.saveDebounced(position)
            advanceTimeBy(249)

            assertEquals(emptyList<ReadingPosition>(), repository.savedPositions)

            advanceUntilIdle()

            assertEquals(listOf(position), repository.savedPositions)
        }

    @Test
    fun saveImmediateAndJoin_flushesPendingDebouncedPosition() =
        runTest {
            val repository = FakeReadingPositionRepository()
            val saver =
                ReaderPositionSaver(
                    scope = this,
                    repository = repository,
                    saveDispatcher = StandardTestDispatcher(testScheduler),
                )
            val stalePosition = readingPosition(tokenIndex = 10)
            val currentPosition = readingPosition(tokenIndex = 20)

            saver.saveDebounced(stalePosition)
            saver.saveImmediateAndJoin(currentPosition)
            advanceUntilIdle()

            assertEquals(listOf(currentPosition), repository.savedPositions)
        }

    @Test
    fun flushWaitsForQueuedLinearPositionBeforeOpeningAuxiliaryTimedReading() =
        runTest {
            val repository = FakeReadingPositionRepository()
            val saver = ReaderPositionSaver(this, repository, StandardTestDispatcher(testScheduler))
            val position = readingPosition(tokenIndex = 10)
            saver.saveDebounced(position)
            val flush = async { saver.flush() }
            advanceUntilIdle()
            flush.await()
            assertEquals(listOf(position), repository.savedPositions)
        }

    private fun readingPosition(tokenIndex: Int): ReadingPosition =
        ReadingPosition(
            bookId = BookId("book"),
            chapterIndex = 0,
            tokenIndex = tokenIndex,
            wordIndex = tokenIndex,
        )

    private class FakeReadingPositionRepository : ReadingPositionRepository {
        val savedPositions = mutableListOf<ReadingPosition>()

        override suspend fun getPosition(bookId: BookId): ReadingPosition? = null

        override suspend fun savePosition(position: ReadingPosition) {
            savedPositions += position
        }

        override fun observePositions(): Flow<List<ReadingPosition>> = flowOf(savedPositions)
    }
}
