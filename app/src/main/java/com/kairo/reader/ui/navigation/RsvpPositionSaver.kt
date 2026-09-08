package com.kairo.reader.ui.navigation

import com.kairo.reader.core.model.ReadingPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Called on the UI thread, preserving request order even when database writes are slow. */
internal class RsvpPositionSaver(private val scope: CoroutineScope, private val writePosition: suspend (ReadingPosition) -> Unit,) {
    private var pendingSave: Job? = null

    fun save(position: ReadingPosition): Job {
        val previousSave = pendingSave
        return scope.launch {
            previousSave?.join()
            writePosition(position)
        }.also { pendingSave = it }
    }

    suspend fun flush() {
        pendingSave?.join()
    }
}
