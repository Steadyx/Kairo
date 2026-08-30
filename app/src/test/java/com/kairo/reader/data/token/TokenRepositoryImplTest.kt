package com.kairo.reader.data.token

import android.net.Uri
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.data.books.BookImportResult
import com.kairo.reader.data.books.BookRepository
import com.kairo.reader.data.books.TextImportRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TokenRepositoryImplTest {
    @Test
    fun invalidateBookSynchronouslyEvictsOnlyThatBooksTokens() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val bookA = BookId("a")
        val bookB = BookId("b")
        val source = MutableBookRepository()
        val repository = TokenRepositoryImpl(source, dispatchers(dispatcher))

        val firstA = async { repository.getTokens(bookA, 0, chapter(0, "old alpha")) }
        val firstB = async { repository.getTokens(bookB, 0, chapter(0, "old beta")) }
        advanceUntilIdle()
        assertEquals(listOf("old", "alpha"), firstA.await().map { it.text })
        assertEquals(listOf("old", "beta"), firstB.await().map { it.text })

        repository.invalidateBook(bookA)

        val refreshedA = async { repository.getTokens(bookA, 0, chapter(0, "new alpha")) }
        val cachedB = async { repository.getTokens(bookB, 0, chapter(0, "new beta")) }
        advanceUntilIdle()
        assertEquals(listOf("new", "alpha"), refreshedA.await().map { it.text })
        assertEquals(listOf("old", "beta"), cachedB.await().map { it.text })
    }

    @Test
    fun invalidatedInFlightTokenizationCannotRepopulateCache() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val source = MutableBookRepository(blockFirstLanguageLookup = true)
        val repository = TokenRepositoryImpl(source, dispatchers(dispatcher))
        val bookId = BookId("book")

        val staleRequest = async { repository.getTokens(bookId, 0, chapter(0, "stale words")) }
        advanceUntilIdle()
        source.languageLookupStarted.await()

        repository.invalidateBook(bookId)
        source.releaseLanguageLookup.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("stale", "words"), staleRequest.await().map { it.text })

        val freshRequest = async { repository.getTokens(bookId, 0, chapter(0, "fresh words")) }
        advanceUntilIdle()
        assertEquals(listOf("fresh", "words"), freshRequest.await().map { it.text })

        val cachedRequest = async { repository.getTokens(bookId, 0, chapter(0, "wrong words")) }
        advanceUntilIdle()
        assertEquals(listOf("fresh", "words"), cachedRequest.await().map { it.text })
    }

    @Test
    fun clearCacheInvalidatesPreviouslyUntrackedInFlightTokenization() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val source = MutableBookRepository(blockFirstLanguageLookup = true)
        val repository = TokenRepositoryImpl(source, dispatchers(dispatcher))
        val bookId = BookId("book")

        val staleRequest = async { repository.getTokens(bookId, 0, chapter(0, "stale words")) }
        advanceUntilIdle()
        source.languageLookupStarted.await()

        repository.clearCache()
        source.releaseLanguageLookup.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("stale", "words"), staleRequest.await().map { it.text })

        val freshRequest = async { repository.getTokens(bookId, 0, chapter(0, "fresh words")) }
        advanceUntilIdle()
        assertEquals(listOf("fresh", "words"), freshRequest.await().map { it.text })

        val cachedRequest = async { repository.getTokens(bookId, 0, chapter(0, "wrong words")) }
        advanceUntilIdle()
        assertEquals(listOf("fresh", "words"), cachedRequest.await().map { it.text })
    }

    private fun dispatchers(dispatcher: CoroutineDispatcher): DispatcherProvider =
        object : DispatcherProvider {
            override val default: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
        }

    private fun chapter(
        index: Int,
        text: String,
    ): Chapter =
        Chapter(
            index = index,
            title = null,
            htmlContent = "<p>$text</p>",
            plainText = text,
        )

    private class MutableBookRepository(private val blockFirstLanguageLookup: Boolean = false,) : BookRepository {
        val languageLookupStarted = CompletableDeferred<Unit>()
        val releaseLanguageLookup = CompletableDeferred<Unit>()
        private var blocked = false

        override suspend fun importBook(uri: Uri): BookImportResult = error("Not used")

        override suspend fun importUrl(rawUrl: String): BookImportResult = error("Not used")

        override suspend fun importText(request: TextImportRequest): BookImportResult = error("Not used")

        override suspend fun getBook(bookId: BookId): Book = error("Not used")

        override suspend fun getChapter(
            bookId: BookId,
            chapterIndex: Int,
        ): Chapter = error("No prefetched chapter")

        override suspend fun updateChapterWordCount(
            bookId: BookId,
            chapterIndex: Int,
            wordCount: Int,
        ) = Unit

        override suspend fun getBookLanguageTag(bookId: BookId): String? {
            if (blockFirstLanguageLookup && !blocked) {
                blocked = true
                languageLookupStarted.complete(Unit)
                releaseLanguageLookup.await()
            }
            return null
        }

        override fun observeBooks(): Flow<List<Book>> = flowOf(emptyList())
    }
}
