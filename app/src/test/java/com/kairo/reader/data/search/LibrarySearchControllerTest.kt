package com.kairo.reader.data.search

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.LibrarySearchResult
import com.kairo.reader.core.model.LibrarySearchResultKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySearchControllerTest {
    @Test
    fun pageChangesKeepQueryAndNewQueryResetsPage() = runTest {
        val calls = mutableListOf<Pair<String, Int>>()
        val repository = object : LibrarySearchRepository {
            override suspend fun search(query: String, bookId: String?): List<LibrarySearchResult> = emptyList()

            override fun searchUpdates(query: String, bookId: String?, resultPage: Int) =
                flowOf(LibrarySearchUpdate(listOf(result("$query-$resultPage")), true, resultPage == 0)).also {
                    calls += query to resultPage
                }
        }
        val controller = LibrarySearchController(repository, this, debounceMs = 0L)

        controller.search("needle")
        advanceUntilIdle()
        assertTrue((controller.state.value as LibrarySearchState.Success).hasMoreResults)
        controller.changePage(1)
        advanceUntilIdle()
        assertEquals(1, (controller.state.value as LibrarySearchState.Success).resultPage)
        controller.search("other")
        advanceUntilIdle()

        assertEquals(listOf("needle" to 0, "needle" to 1, "other" to 0), calls)
    }

    @Test
    fun failedPageCanBeRequestedAgain() = runTest {
        var failPage = true
        val repository = object : LibrarySearchRepository {
            override suspend fun search(query: String, bookId: String?): List<LibrarySearchResult> = emptyList()

            override fun searchUpdates(query: String, bookId: String?, resultPage: Int) = flow {
                if (resultPage == 1 && failPage) error("unavailable")
                emit(LibrarySearchUpdate(listOf(result("$resultPage")), true, resultPage == 0))
            }
        }
        val controller = LibrarySearchController(repository, this, debounceMs = 0L)
        controller.search("needle")
        advanceUntilIdle()
        controller.changePage(1)
        advanceUntilIdle()
        assertTrue(controller.state.value is LibrarySearchState.Error)
        failPage = false
        controller.changePage(1)
        advanceUntilIdle()
        assertEquals(1, (controller.state.value as LibrarySearchState.Success).resultPage)
    }

    @Test
    fun newerQueryCancelsStaleWork() =
        runTest {
            val repository =
                FakeSearchRepository { query ->
                    if (query == "first") delay(1_000L)
                    listOf(result(query))
                }
            val controller = LibrarySearchController(repository, this, debounceMs = 0L)

            controller.search("first")
            runCurrent()
            controller.search("second")
            advanceUntilIdle()

            val state = controller.state.value as LibrarySearchState.Success
            assertEquals("second", state.query)
            assertEquals(listOf("second"), state.results.map { it.id })
        }

    @Test
    fun errorCanRetryAndResultsAreBounded() =
        runTest {
            var shouldFail = true
            val repository =
                FakeSearchRepository {
                    if (shouldFail) error("search failed")
                    List(LibrarySearchConstraints.MAX_RESULTS + 20) { result("result-$it") }
                }
            val controller = LibrarySearchController(repository, this, debounceMs = 0L)

            controller.search("query")
            advanceUntilIdle()
            assertTrue(controller.state.value is LibrarySearchState.Error)

            shouldFail = false
            controller.retry()
            advanceUntilIdle()

            val state = controller.state.value as LibrarySearchState.Success
            assertEquals(LibrarySearchConstraints.MAX_RESULTS, state.results.size)
        }

    @Test
    fun shortAndCancelledQueriesReturnToIdle() =
        runTest {
            val controller =
                LibrarySearchController(
                    repository = FakeSearchRepository { emptyList() },
                    scope = this,
                    debounceMs = 1_000L,
                )

            controller.search("valid")
            assertTrue(controller.state.value is LibrarySearchState.Loading)
            controller.cancel()
            assertEquals(LibrarySearchState.Idle, controller.state.value)
            controller.search("x")
            assertEquals(LibrarySearchState.Idle, controller.state.value)
        }
}

private class FakeSearchRepository(private val searchHandler: suspend (String) -> List<LibrarySearchResult>,) : LibrarySearchRepository {
    override suspend fun search(
        query: String,
        bookId: String?,
    ): List<LibrarySearchResult> = searchHandler(query)
}

private fun result(id: String): LibrarySearchResult =
    LibrarySearchResult(
        id = id,
        kind = LibrarySearchResultKind.PASSAGE,
        bookId = BookId("book"),
        bookTitle = "Book",
        chapterIndex = 0,
        chapterTitle = null,
        tokenIndex = 0,
        title = id,
        snippet = id,
    )
