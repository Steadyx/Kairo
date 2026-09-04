package com.kairo.reader.ui.export

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.kairo.reader.core.export.NoteExportFormat
import com.kairo.reader.core.export.NoteExportScope
import com.kairo.reader.data.export.NoteExporter
import com.kairo.reader.data.export.PreparedNoteExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteExportCoordinatorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun preparedDescriptorSurvivesRecreationAndCanBeCancelled() =
        runTest(dispatcher) {
            Dispatchers.setMain(dispatcher)
            val exporter = FakeNoteExporter()
            val savedState = SavedStateHandle()
            val viewModel = coordinator(exporter, savedState)

            viewModel.requestExport(NoteExportScope.Single("note-1"))
            viewModel.selectScope(NoteExportScope.Book("book-1"))
            viewModel.selectFormat(NoteExportFormat.MARKDOWN)
            viewModel.save()
            viewModel.save()
            advanceUntilIdle()

            assertEquals(1, exporter.prepareCalls)
            assertEquals(NoteExportPhase.AWAITING_DESTINATION, viewModel.state.value.phase)
            val launch = viewModel.events.first() as NoteExportCoordinatorEvent.LaunchDocument
            assertEquals(NoteExportFormat.MARKDOWN, launch.format)

            val restored = coordinator(exporter, savedState)
            assertEquals(NoteExportPhase.AWAITING_DESTINATION, restored.state.value.phase)
            restored.cancelPending()

            assertEquals(NoteExportPhase.IDLE, restored.state.value.phase)
            assertEquals(listOf("request-1"), exporter.discardedIds)
        }

    @Test
    fun pickerCancellationReturnsToIdleWithoutWriting() =
        runTest(dispatcher) {
            Dispatchers.setMain(dispatcher)
            val exporter = FakeNoteExporter()
            val viewModel = coordinator(exporter, SavedStateHandle())
            viewModel.requestExport(NoteExportScope.All)
            viewModel.save()
            advanceUntilIdle()

            viewModel.onDestinationResult(NoteExportFormat.PDF, null)

            assertEquals(NoteExportPhase.IDLE, viewModel.state.value.phase)
            assertTrue(exporter.writes.isEmpty())
            assertEquals(listOf("request-1"), exporter.discardedIds)
        }

    private fun coordinator(
        exporter: NoteExporter,
        savedState: SavedStateHandle,
    ) =
        NoteExportCoordinatorViewModel(
            service = exporter,
            stringResource = { resourceId -> "message-$resourceId" },
            savedStateHandle = savedState,
        )
}

private class FakeNoteExporter : NoteExporter {
    var prepareCalls = 0
    val writes = mutableListOf<String>()
    val discardedIds = mutableListOf<String>()

    override suspend fun prepare(
        scope: NoteExportScope,
        format: NoteExportFormat,
    ): PreparedNoteExport {
        prepareCalls++
        return PreparedNoteExport(
            requestId = "request-$prepareCalls",
            scope = scope,
            format = format,
            suggestedFileName = "notes.${format.extension}",
        )
    }

    override suspend fun write(
        requestId: String,
        scope: NoteExportScope,
        format: NoteExportFormat,
        destination: Uri,
    ) {
        writes += requestId
    }

    override fun discard(requestId: String) {
        discardedIds += requestId
    }
}
