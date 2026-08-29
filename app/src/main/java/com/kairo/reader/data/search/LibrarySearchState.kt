package com.kairo.reader.data.search

import com.kairo.reader.core.model.LibrarySearchResult
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LibrarySearchState {
    data object Idle : LibrarySearchState

    data class Loading(val query: String) : LibrarySearchState

    data class Success(val query: String, val results: List<LibrarySearchResult>,) : LibrarySearchState

    data class Error(val query: String) : LibrarySearchState
}

class LibrarySearchController(
    private val repository: LibrarySearchRepository,
    private val scope: CoroutineScope,
    private val bookId: String? = null,
    private val debounceMs: Long = SEARCH_DEBOUNCE_MS,
) {
    private val mutableState = MutableStateFlow<LibrarySearchState>(LibrarySearchState.Idle)
    val state: StateFlow<LibrarySearchState> = mutableState.asStateFlow()

    private var searchJob: Job? = null
    private var lastQuery = ""

    fun search(rawQuery: String) {
        val query = normalizeLibrarySearchQuery(rawQuery)
        lastQuery = query
        searchJob?.cancel()
        if (query.length < LibrarySearchConstraints.MIN_QUERY_LENGTH) {
            mutableState.value = LibrarySearchState.Idle
            return
        }
        mutableState.value = LibrarySearchState.Loading(query)
        searchJob =
            scope.launch {
                try {
                    delay(debounceMs)
                    mutableState.value =
                        LibrarySearchState.Success(
                            query,
                            repository.search(query, bookId).take(LibrarySearchConstraints.MAX_RESULTS),
                        )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = LibrarySearchState.Error(query)
                }
            }
    }

    fun retry() {
        search(lastQuery)
    }

    fun cancel() {
        searchJob?.cancel()
        searchJob = null
        mutableState.value = LibrarySearchState.Idle
    }
}

private const val SEARCH_DEBOUNCE_MS = 250L
