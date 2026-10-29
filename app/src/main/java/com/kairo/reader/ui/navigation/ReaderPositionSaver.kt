package com.kairo.reader.ui.navigation

import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.data.reading.ReadingPositionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val READER_POSITION_SAVE_DEBOUNCE_MS = 250L

internal class ReaderPositionSaver(
    private val scope: CoroutineScope,
    private val repository: ReadingPositionRepository,
    private val saveDispatcher: CoroutineDispatcher,
) {
    private var pendingSaveJob: Job? = null
    private var lastQueuedPosition: ReadingPosition? = null

    fun saveDebounced(position: ReadingPosition) {
        if (lastQueuedPosition == position) return
        queueSave(position, debounce = true)
    }

    fun saveImmediate(position: ReadingPosition) {
        queueSave(position, debounce = false)
    }

    suspend fun saveImmediateAndJoin(position: ReadingPosition) {
        pendingSaveJob?.cancel()
        lastQueuedPosition = position
        withContext(saveDispatcher) {
            repository.savePosition(position)
        }
    }

    suspend fun flush() {
        pendingSaveJob?.join()
    }

    private fun queueSave(
        position: ReadingPosition,
        debounce: Boolean,
    ) {
        pendingSaveJob?.cancel()
        lastQueuedPosition = position
        pendingSaveJob =
            scope.launch(saveDispatcher) {
                if (debounce) {
                    delay(READER_POSITION_SAVE_DEBOUNCE_MS)
                }
                repository.savePosition(position)
            }
    }
}
