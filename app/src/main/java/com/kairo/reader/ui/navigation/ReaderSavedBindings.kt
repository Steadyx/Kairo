package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import com.kairo.reader.KairoApplication
import com.kairo.reader.R
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.SaveAnnotationRequest
import com.kairo.reader.core.model.SavedAnnotation
import com.kairo.reader.data.search.LibrarySearchController
import com.kairo.reader.data.search.LibrarySearchState
import java.util.UUID
import kotlinx.coroutines.launch

internal data class ReaderSavedBindings(
    val annotations: List<SavedAnnotation>,
    val searchState: LibrarySearchState,
    val onSearch: (String) -> Unit,
    val onRetrySearch: () -> Unit,
    val onSearchPageChange: (Int) -> Unit,
    val onSaveAnnotation: (SaveAnnotationRequest) -> Unit,
)

@Composable
internal fun rememberReaderSavedBindings(
    container: KairoApplication,
    bookId: BookId,
    chapterIndex: Int,
    onShowUserMessage: (String) -> Unit,
): ReaderSavedBindings {
    val annotations by
        container.savedAnnotationRepository.observeForBook(bookId).collectAsState(
            initial = emptyList(),
        )
    val coroutineScope = rememberCoroutineScope()
    val resources = LocalResources.current
    val searchController = remember(container.searchRepository, coroutineScope, bookId) {
        LibrarySearchController(container.searchRepository, coroutineScope, bookId.value)
    }
    val searchState by searchController.state.collectAsState()
    return ReaderSavedBindings(
        annotations = annotations,
        searchState = searchState,
        onSearch = searchController::search,
        onRetrySearch = searchController::retry,
        onSearchPageChange = searchController::changePage,
        onSaveAnnotation = { request ->
            coroutineScope.launch {
                val now = System.currentTimeMillis()
                val saved = container.savedAnnotationRepository.save(
                    request.toSavedAnnotation(
                        id = UUID.randomUUID().toString(),
                        bookId = bookId,
                        chapterIndex = chapterIndex,
                        now = now,
                    ),
                )
                onShowUserMessage(
                    resources.getString(
                        if (saved) R.string.toast_passage_saved else R.string.toast_passage_save_failed,
                    ),
                )
            }
        },
    )
}

private fun SaveAnnotationRequest.toSavedAnnotation(
    id: String,
    bookId: BookId,
    chapterIndex: Int,
    now: Long,
): SavedAnnotation =
    SavedAnnotation(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        startTokenIndex = startTokenIndex,
        endTokenIndex = endTokenIndex,
        selectedText = selectedText,
        note = note,
        color = color,
        kind = kind,
        createdAt = now,
        updatedAt = now,
    )
