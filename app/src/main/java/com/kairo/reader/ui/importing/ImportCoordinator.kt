package com.kairo.reader.ui.importing

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.R
import com.kairo.reader.data.books.BookImportResult
import com.kairo.reader.data.books.SharedTextImport
import com.kairo.reader.data.books.TextImportRequest
import com.kairo.reader.data.books.WebArticleUrl
import com.kairo.reader.ui.library.ImportUiState
import com.kairo.reader.ui.navigation.KairoRoutes
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jsoup.HttpStatusException

private const val IMPORT_COMPLETE_HOLD_MS = 200L
private const val URL_IMPORT_COMPLETE_HOLD_MS = 40L
private const val IMPORT_PROGRESS_UPDATE_MS = 120L
private const val IMPORT_PROGRESS_FAST_CAP = 0.92f
private const val IMPORT_PROGRESS_SLOW_CAP = 0.985f
private const val IMPORT_PROGRESS_FAST_STEP = 0.08f
private const val IMPORT_PROGRESS_SLOW_STEP = 0.012f
private const val IMPORT_PROGRESS_MIN_SLOW_INCREMENT = 0.001f

internal data class ImportCoordinator(
    val state: ImportUiState,
    val importFile: (Uri) -> Unit,
    val importUrl: (String) -> Unit,
    val importText: (TextImportRequest) -> Unit,
)

@Composable
internal fun rememberImportCoordinator(
    container: KairoApplication,
    navController: NavHostController,
    externalImportUri: Uri?,
    externalArticleUrl: String?,
    externalSharedText: SharedTextImport?,
    onExternalImportUriConsumed: (Uri) -> Unit,
    onExternalArticleUrlConsumed: (String) -> Unit,
    onExternalSharedTextConsumed: (SharedTextImport) -> Unit,
    onShowUserMessage: (String, SnackbarDuration) -> Unit,
): ImportCoordinator {
    val importViewModel: ImportCoordinatorViewModel =
        viewModel(factory = ImportCoordinatorViewModel.factory(container))
    val importState by importViewModel.state.collectAsState()
    val latestShowUserMessage by rememberUpdatedState(onShowUserMessage)

    LaunchedEffect(importViewModel, navController) {
        importViewModel.events.collect { event ->
            when (event) {
                is ImportCoordinatorEvent.OpenReader ->
                    navController.navigate(KairoRoutes.reader(event.bookId)) {
                        launchSingleTop = true
                    }

                is ImportCoordinatorEvent.UserMessage ->
                    latestShowUserMessage(event.message, event.duration)
            }
        }
    }

    LaunchedEffect(externalImportUri, importState.isImporting) {
        val uri = externalImportUri ?: return@LaunchedEffect
        if (importState.isImporting) return@LaunchedEffect
        val accepted = importViewModel.importFile(uri)
        if (!accepted) return@LaunchedEffect
        navController.navigate(KairoRoutes.LIBRARY) {
            popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
            launchSingleTop = true
        }
        onExternalImportUriConsumed(uri)
    }

    LaunchedEffect(externalArticleUrl, importState.isImporting) {
        val url = externalArticleUrl ?: return@LaunchedEffect
        if (importState.isImporting) return@LaunchedEffect
        val accepted = importViewModel.importUrl(url, openReaderOnSuccess = true)
        if (!accepted) return@LaunchedEffect
        navController.navigate(KairoRoutes.LIBRARY) {
            popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
            launchSingleTop = true
        }
        onExternalArticleUrlConsumed(url)
    }

    LaunchedEffect(externalSharedText, importState.isImporting) {
        val sharedText = externalSharedText ?: return@LaunchedEffect
        if (importState.isImporting) return@LaunchedEffect
        val request =
            TextImportRequest(
                content = sharedText.content,
                title =
                sharedText.title
                    ?.takeIf(String::isNotBlank)
                    ?: container.getString(R.string.library_text_default_title),
            )
        val accepted = importViewModel.importText(request, openReaderOnSuccess = true)
        if (!accepted) return@LaunchedEffect
        navController.navigate(KairoRoutes.LIBRARY) {
            popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
            launchSingleTop = true
        }
        onExternalSharedTextConsumed(sharedText)
    }

    return ImportCoordinator(
        state = importState,
        importFile = { uri -> importViewModel.importFile(uri) },
        importUrl = { url -> importViewModel.importUrl(url, openReaderOnSuccess = true) },
        importText = { request ->
            importViewModel.importText(request, openReaderOnSuccess = true)
        },
    )
}

internal sealed interface ImportCoordinatorEvent {
    data class UserMessage(val message: String, val duration: SnackbarDuration,) : ImportCoordinatorEvent

    data class OpenReader(val bookId: String) : ImportCoordinatorEvent
}

internal class ImportCoordinatorViewModel(private val container: KairoApplication,) : ViewModel() {
    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private val _events = Channel<ImportCoordinatorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val resources: Resources = container.resources
    private var importProgressJob: Job? = null

    fun importFile(uri: Uri): Boolean =
        handleImport(resolveImportFileName(container, uri)) {
            container.libraryRepository.import(uri)
        }

    fun importUrl(
        rawUrl: String,
        openReaderOnSuccess: Boolean,
    ): Boolean =
        handleImport(
            displayName = resolveImportUrlName(rawUrl),
            completionHoldMs = URL_IMPORT_COMPLETE_HOLD_MS,
            onImported = { importResult ->
                if (openReaderOnSuccess) {
                    emitEvent(ImportCoordinatorEvent.OpenReader(importResult.book.id.value))
                }
            },
        ) {
            container.libraryRepository.importUrl(rawUrl)
        }

    fun importText(
        request: TextImportRequest,
        openReaderOnSuccess: Boolean,
    ): Boolean =
        handleImport(
            displayName = request.title?.takeIf(String::isNotBlank),
            completionHoldMs = URL_IMPORT_COMPLETE_HOLD_MS,
            onImported = { importResult ->
                if (openReaderOnSuccess) {
                    emitEvent(ImportCoordinatorEvent.OpenReader(importResult.book.id.value))
                }
            },
        ) {
            container.libraryRepository.importText(request)
        }

    private fun handleImport(
        displayName: String?,
        completionHoldMs: Long = IMPORT_COMPLETE_HOLD_MS,
        onImported: (BookImportResult) -> Unit = {},
        importBook: suspend () -> BookImportResult,
    ): Boolean {
        if (_state.value.isImporting) return false
        _state.value =
            ImportUiState(
                isImporting = true,
                progress = 0f,
                fileName = displayName,
            )
        importProgressJob?.cancel()
        importProgressJob =
            viewModelScope.launch {
                driveImportProgress { progress ->
                    _state.value = _state.value.copy(progress = progress)
                }
            }
        viewModelScope.launch(container.dispatcherProvider.io) {
            val result = runCatching { importBook() }
            importProgressJob?.cancel()
            if (result.isSuccess) {
                _state.value = _state.value.copy(progress = 1f)
                if (completionHoldMs > 0L) {
                    delay(completionHoldMs)
                }
            }
            _state.value = ImportUiState()
            result.onSuccess { importResult ->
                val book = importResult.book
                if (importResult.alreadyImported) {
                    emitEvent(
                        ImportCoordinatorEvent.UserMessage(
                            resources.getString(
                                R.string.toast_import_duplicate_detail,
                                book.title,
                            ),
                            SnackbarDuration.Long,
                        )
                    )
                    onImported(importResult)
                    return@onSuccess
                }
                val chapterCount = book.chapters.size
                emitEvent(
                    ImportCoordinatorEvent.UserMessage(
                        resources.getQuantityString(
                            R.plurals.toast_imported_with_chapter_count,
                            chapterCount,
                            book.title,
                            chapterCount,
                        ),
                        SnackbarDuration.Short,
                    )
                )
                onImported(importResult)
            }
            result.onFailure { error ->
                emitEvent(
                    ImportCoordinatorEvent.UserMessage(
                        resolveImportFailureMessage(resources, error),
                        SnackbarDuration.Long,
                    )
                )
            }
        }
        return true
    }

    private fun emitEvent(event: ImportCoordinatorEvent) {
        _events.trySend(event)
    }

    companion object {
        fun factory(container: KairoApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ImportCoordinatorViewModel::class.java)) {
                        return ImportCoordinatorViewModel(container) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

private fun resolveImportFileName(
    context: Context,
    uri: Uri,
): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    }.getOrNull()

private fun resolveImportUrlName(rawUrl: String): String? =
    runCatching { WebArticleUrl.displayHost(WebArticleUrl.normalize(rawUrl)) }.getOrNull()

internal fun resolveImportFailureMessage(
    resources: Resources,
    error: Throwable,
): String {
    val message =
        when (val root = error.rootCause()) {
            is HttpStatusException ->
                when (root.statusCode) {
                    HTTP_UNAUTHORIZED, HTTP_FORBIDDEN ->
                        resources.getString(R.string.toast_import_failed_blocked)
                    HTTP_NOT_FOUND -> resources.getString(R.string.toast_import_failed_not_found)
                    HTTP_TOO_MANY_REQUESTS ->
                        resources.getString(R.string.toast_import_failed_rate_limited)
                    in 500..599 -> resources.getString(R.string.toast_import_failed_server)
                    else -> resources.getString(R.string.toast_import_failed_detail, root.message)
                }
            is UnknownHostException -> resources.getString(R.string.toast_import_failed_network)
            is SocketTimeoutException -> resources.getString(R.string.toast_import_failed_timeout)
            is SSLException -> resources.getString(R.string.toast_import_failed_secure)
            else ->
                error.message?.let {
                    resources.getString(R.string.toast_import_failed_detail, it)
                } ?: resources.getString(R.string.toast_import_failed_unknown)
        }
    return message
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429

private fun Throwable.rootCause(): Throwable {
    var current = this
    while (current.cause != null && current.cause !== current) {
        current = current.cause ?: break
    }
    return current
}

private suspend fun driveImportProgress(onUpdate: (Float) -> Unit) {
    var progress = 0f
    onUpdate(progress)
    while (currentCoroutineContext().isActive && progress < IMPORT_PROGRESS_SLOW_CAP) {
        delay(IMPORT_PROGRESS_UPDATE_MS)
        progress =
            if (progress < IMPORT_PROGRESS_FAST_CAP) {
                (progress + (1f - progress) * IMPORT_PROGRESS_FAST_STEP)
                    .coerceAtMost(IMPORT_PROGRESS_FAST_CAP)
            } else {
                val nextIncrement =
                    ((IMPORT_PROGRESS_SLOW_CAP - progress) * IMPORT_PROGRESS_SLOW_STEP)
                        .coerceAtLeast(IMPORT_PROGRESS_MIN_SLOW_INCREMENT)
                (progress + nextIncrement).coerceAtMost(IMPORT_PROGRESS_SLOW_CAP)
            }
        onUpdate(progress)
    }
}
