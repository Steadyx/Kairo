package com.kairo.reader.ui.export

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairo.reader.KairoApplication
import com.kairo.reader.R
import com.kairo.reader.core.export.NoteExportFormat
import com.kairo.reader.core.export.NoteExportResolutionException
import com.kairo.reader.core.export.NoteExportResolutionFailure
import com.kairo.reader.core.export.NoteExportScope
import com.kairo.reader.data.export.NoteExporter
import com.kairo.reader.data.export.PreparedNoteExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal enum class NoteExportPhase {
    IDLE,
    PREPARING,
    AWAITING_DESTINATION,
    WRITING,
}

internal data class NoteExportUiState(
    val sheetOrigin: NoteExportScope? = null,
    val sheetScope: NoteExportScope? = null,
    val selectedFormat: NoteExportFormat = NoteExportFormat.PDF,
    val phase: NoteExportPhase = NoteExportPhase.IDLE,
)

internal data class NoteExportUiBindings(
    val state: NoteExportUiState,
    val requestExport: (NoteExportScope) -> Unit,
    val selectScope: (NoteExportScope) -> Unit,
    val selectFormat: (NoteExportFormat) -> Unit,
    val dismissSheet: () -> Unit,
    val save: () -> Unit,
    val cancelPending: () -> Unit,
)

internal sealed interface NoteExportCoordinatorEvent {
    data class LaunchDocument(val format: NoteExportFormat, val suggestedFileName: String,) : NoteExportCoordinatorEvent

    data class UserMessage(val message: String, val duration: SnackbarDuration,) : NoteExportCoordinatorEvent
}

@Composable
internal fun rememberNoteExportCoordinator(
    container: KairoApplication,
    onShowUserMessage: (String, SnackbarDuration) -> Unit,
): NoteExportUiBindings {
    val exportViewModel: NoteExportCoordinatorViewModel =
        viewModel(factory = NoteExportCoordinatorViewModel.factory(container))
    val state by exportViewModel.state.collectAsState()
    val latestShowUserMessage by rememberUpdatedState(onShowUserMessage)
    val pdfLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument(PDF_MIME_TYPE),
        ) { uri: Uri? ->
            exportViewModel.onDestinationResult(NoteExportFormat.PDF, uri)
        }
    val markdownLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument(MARKDOWN_MIME_TYPE),
        ) { uri: Uri? ->
            exportViewModel.onDestinationResult(NoteExportFormat.MARKDOWN, uri)
        }

    LaunchedEffect(exportViewModel, pdfLauncher, markdownLauncher) {
        exportViewModel.events.collect { event ->
            when (event) {
                is NoteExportCoordinatorEvent.LaunchDocument -> {
                    val result =
                        runCatching {
                            when (event.format) {
                                NoteExportFormat.PDF -> pdfLauncher.launch(event.suggestedFileName)
                                NoteExportFormat.MARKDOWN -> markdownLauncher.launch(event.suggestedFileName)
                            }
                        }
                    if (result.isFailure) exportViewModel.onPickerLaunchFailed(event.format)
                }
                is NoteExportCoordinatorEvent.UserMessage ->
                    latestShowUserMessage(event.message, event.duration)
            }
        }
    }

    return NoteExportUiBindings(
        state = state,
        requestExport = exportViewModel::requestExport,
        selectScope = exportViewModel::selectScope,
        selectFormat = exportViewModel::selectFormat,
        dismissSheet = exportViewModel::dismissSheet,
        save = exportViewModel::save,
        cancelPending = exportViewModel::cancelPending,
    )
}

internal class NoteExportCoordinatorViewModel(
    private val service: NoteExporter,
    private val stringResource: (Int) -> String,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restoredDescriptor = restorePendingDescriptor()
    private val _state =
        MutableStateFlow(
            NoteExportUiState(
                selectedFormat = restoredDescriptor?.format ?: NoteExportFormat.PDF,
                phase =
                if (restoredDescriptor == null) {
                    NoteExportPhase.IDLE
                } else {
                    NoteExportPhase.AWAITING_DESTINATION
                },
            ),
        )
    val state: StateFlow<NoteExportUiState> = _state.asStateFlow()

    private val _events = Channel<NoteExportCoordinatorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun requestExport(scope: NoteExportScope) {
        if (_state.value.phase != NoteExportPhase.IDLE || _state.value.sheetScope != null) return
        if (!scope.isValid()) return
        _state.value =
            NoteExportUiState(
                sheetOrigin = scope,
                sheetScope = scope,
                selectedFormat = NoteExportFormat.PDF,
            )
    }

    fun selectScope(scope: NoteExportScope) {
        val current = _state.value
        if (current.phase != NoteExportPhase.IDLE || current.sheetScope == null || !scope.isValid()) return
        _state.value = current.copy(sheetScope = scope)
    }

    fun selectFormat(format: NoteExportFormat) {
        val current = _state.value
        if (current.phase != NoteExportPhase.IDLE || current.sheetScope == null) return
        _state.value = current.copy(selectedFormat = format)
    }

    fun dismissSheet() {
        val current = _state.value
        if (current.phase != NoteExportPhase.IDLE) return
        _state.value = NoteExportUiState()
    }

    fun save() {
        val current = _state.value
        val scope = current.sheetScope ?: return
        if (current.phase != NoteExportPhase.IDLE) return
        _state.value = current.copy(sheetScope = null, phase = NoteExportPhase.PREPARING)
        viewModelScope.launch {
            val preparation = runCatching { service.prepare(scope, current.selectedFormat) }
            val failure = preparation.exceptionOrNull()
            if (failure != null) {
                when (failure) {
                    is CancellationException -> throw failure
                    is Error -> throw failure
                    else -> clearWithFailure(failure)
                }
                return@launch
            }
            val prepared = preparation.getOrThrow()
            if (_state.value.phase != NoteExportPhase.PREPARING) {
                service.discard(prepared.requestId)
                return@launch
            }
            val descriptor = prepared.toPending()
            savePendingDescriptor(descriptor)
            _state.value =
                _state.value.copy(
                    selectedFormat = prepared.format,
                    phase = NoteExportPhase.AWAITING_DESTINATION,
                )
            _events.trySend(
                NoteExportCoordinatorEvent.LaunchDocument(
                    prepared.format,
                    prepared.suggestedFileName,
                ),
            )
        }
    }

    fun onPickerLaunchFailed(format: NoteExportFormat) {
        val pending = restorePendingDescriptor()
        if (pending?.format != format) return
        service.discard(pending.requestId)
        clearPendingDescriptor()
        _state.value = NoteExportUiState()
        emitFailureMessage(R.string.note_export_failed)
    }

    fun cancelPending() {
        if (_state.value.phase != NoteExportPhase.AWAITING_DESTINATION) return
        restorePendingDescriptor()?.let { service.discard(it.requestId) }
        clearPendingDescriptor()
        _state.value = NoteExportUiState()
    }

    fun onDestinationResult(
        format: NoteExportFormat,
        destination: Uri?,
    ) {
        val pending = restorePendingDescriptor()
        if (pending == null || pending.format != format || _state.value.phase != NoteExportPhase.AWAITING_DESTINATION) {
            if (destination != null) emitFailureMessage(R.string.note_export_failed)
            return
        }
        clearPendingDescriptor()
        if (destination == null) {
            service.discard(pending.requestId)
            _state.value = NoteExportUiState()
            return
        }
        _state.value =
            _state.value.copy(
                sheetScope = null,
                selectedFormat = format,
                phase = NoteExportPhase.WRITING,
            )
        viewModelScope.launch {
            val writing =
                runCatching {
                    service.write(
                        requestId = pending.requestId,
                        scope = pending.scope,
                        format = format,
                        destination = destination,
                    )
                }
            val failure = writing.exceptionOrNull()
            if (failure != null) {
                service.discard(pending.requestId)
                when (failure) {
                    is CancellationException -> throw failure
                    is Error -> throw failure
                    else -> clearWithFailure(failure)
                }
                return@launch
            }
            _state.value = NoteExportUiState()
            _events.trySend(
                NoteExportCoordinatorEvent.UserMessage(
                    message =
                    stringResource(
                        when (format) {
                            NoteExportFormat.PDF -> R.string.note_export_success_pdf
                            NoteExportFormat.MARKDOWN -> R.string.note_export_success_markdown
                        },
                    ),
                    duration = SnackbarDuration.Short,
                ),
            )
        }
    }

    private fun clearWithFailure(failure: Throwable) {
        clearPendingDescriptor()
        _state.value = NoteExportUiState()
        val message =
            when ((failure as? NoteExportResolutionException)?.failure) {
                NoteExportResolutionFailure.NO_NOTES -> R.string.note_export_no_notes
                NoteExportResolutionFailure.STALE_SINGLE,
                NoteExportResolutionFailure.WRONG_KIND,
                -> R.string.note_export_note_unavailable
                null -> R.string.note_export_failed
            }
        emitFailureMessage(message)
    }

    private fun emitFailureMessage(messageResource: Int) {
        _events.trySend(
            NoteExportCoordinatorEvent.UserMessage(
                message = stringResource(messageResource),
                duration = SnackbarDuration.Long,
            ),
        )
    }

    private fun savePendingDescriptor(descriptor: PendingNoteExport) {
        savedStateHandle[KEY_SCOPE_KIND] =
            when (descriptor.scope) {
                NoteExportScope.All -> SCOPE_ALL
                is NoteExportScope.Book -> SCOPE_BOOK
                is NoteExportScope.Single -> SCOPE_SINGLE
            }
        savedStateHandle[KEY_ANNOTATION_ID] =
            (descriptor.scope as? NoteExportScope.Single)?.annotationId
        savedStateHandle[KEY_BOOK_ID] = (descriptor.scope as? NoteExportScope.Book)?.bookId
        savedStateHandle[KEY_REQUEST_ID] = descriptor.requestId
        savedStateHandle[KEY_FORMAT] = descriptor.format.name
    }

    private fun restorePendingDescriptor(): PendingNoteExport? {
        val format =
            savedStateHandle.get<String>(KEY_FORMAT)?.let { stored ->
                NoteExportFormat.entries.firstOrNull { it.name == stored }
            } ?: return null
        val requestId = savedStateHandle.get<String>(KEY_REQUEST_ID)?.takeIf(String::isNotBlank) ?: return null
        val scope =
            when (savedStateHandle.get<String>(KEY_SCOPE_KIND)) {
                SCOPE_ALL -> NoteExportScope.All
                SCOPE_BOOK -> {
                    val bookId =
                        savedStateHandle.get<String>(KEY_BOOK_ID)?.takeIf(String::isNotBlank)
                            ?: return null
                    NoteExportScope.Book(bookId)
                }
                SCOPE_SINGLE -> {
                    val annotationId =
                        savedStateHandle.get<String>(KEY_ANNOTATION_ID)?.takeIf(String::isNotBlank)
                            ?: return null
                    NoteExportScope.Single(annotationId)
                }
                else -> return null
            }
        return PendingNoteExport(requestId, scope, format)
    }

    private fun clearPendingDescriptor() {
        savedStateHandle.remove<String>(KEY_SCOPE_KIND)
        savedStateHandle.remove<String>(KEY_ANNOTATION_ID)
        savedStateHandle.remove<String>(KEY_BOOK_ID)
        savedStateHandle.remove<String>(KEY_REQUEST_ID)
        savedStateHandle.remove<String>(KEY_FORMAT)
    }

    companion object {
        fun factory(container: KairoApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    NoteExportCoordinatorViewModel(
                        service = container.noteExportService,
                        stringResource = { resourceId -> container.resources.getString(resourceId) },
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }

        private const val KEY_SCOPE_KIND = "noteExport.scope"
        private const val KEY_ANNOTATION_ID = "noteExport.annotationId"
        private const val KEY_BOOK_ID = "noteExport.bookId"
        private const val KEY_REQUEST_ID = "noteExport.requestId"
        private const val KEY_FORMAT = "noteExport.format"
        private const val SCOPE_ALL = "all"
        private const val SCOPE_BOOK = "book"
        private const val SCOPE_SINGLE = "single"
    }
}

private data class PendingNoteExport(val requestId: String, val scope: NoteExportScope, val format: NoteExportFormat,)

private fun PreparedNoteExport.toPending(): PendingNoteExport =
    PendingNoteExport(requestId = requestId, scope = scope, format = format)

private fun NoteExportScope.isValid(): Boolean =
    when (this) {
        NoteExportScope.All -> true
        is NoteExportScope.Book -> bookId.isNotBlank()
        is NoteExportScope.Single -> annotationId.isNotBlank()
    }

private const val PDF_MIME_TYPE = "application/pdf"
private const val MARKDOWN_MIME_TYPE = "text/markdown"
