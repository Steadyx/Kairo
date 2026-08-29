package com.kairo.reader.data.search

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.LibrarySearchResultKind
import com.kairo.reader.data.local.BookEntity
import com.kairo.reader.data.local.SavedAnnotationDao
import com.kairo.reader.data.local.SavedAnnotationEntity
import com.kairo.reader.data.local.SavedAnnotationWithBookEntity
import com.kairo.reader.data.local.SearchDao
import com.kairo.reader.data.local.SearchPassageBookEntity
import com.kairo.reader.data.local.SearchPassageChapterPageEntity
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySearchRepositoryImplTest {
    @Test
    fun searchStartsCheapGroupsFirstAndFairlyMergesPassages() =
        runTest {
            var booksStarted = false
            var savedStarted = false
            val searchDao =
                FakeSearchDao(
                    bookSearch = {
                        booksStarted = true
                        listOf(book())
                    },
                    passageBooks = listOf(passageBook()),
                    passagesByBook = mapOf("book" to listOf(passageChapter())),
                    beforePassageBooks = {
                        assertTrue(booksStarted)
                        assertTrue(savedStarted)
                    },
                )
            val annotationDao =
                FakeSavedAnnotationDao {
                    savedStarted = true
                    listOf(savedWithBook())
                }
            val repository = repository(searchDao, annotationDao)

            val results = repository.search("needle", bookId = null)

            assertEquals(
                listOf(
                    LibrarySearchResultKind.BOOK,
                    LibrarySearchResultKind.PASSAGE,
                    LibrarySearchResultKind.SAVED,
                ),
                results.map { it.kind },
            )
            val passage = results.single { it.kind == LibrarySearchResultKind.PASSAGE }
            assertEquals(7, passage.matchStartCodePointOffset)
            assertEquals("needle".length, passage.matchLengthCodePoints)
            assertEquals("before needle after", passage.snippet)
            assertEquals(0, passage.tokenIndex)
        }

    @Test
    fun cancellationStopsTheInFlightPassageQuery() =
        runTest {
            var passageStarted = false
            val repository =
                repository(
                    searchDao =
                    FakeSearchDao(
                        passageBooks = listOf(passageBook()),
                        passagesByBook = mapOf("book" to listOf(passageChapter())),
                        beforePassageChapterPage = {
                            passageStarted = true
                            awaitCancellation()
                        },
                    ),
                    annotationDao = FakeSavedAnnotationDao(),
                )
            val searchJob = launch { repository.search("needle", bookId = null) }
            runCurrent()
            assertTrue(passageStarted)

            searchJob.cancelAndJoin()

            assertTrue(searchJob.isCancelled)
        }

    @Test
    fun expandingLowercasePrefixDoesNotShiftOriginalTextMatch() =
        runTest {
            val plainText = "İ before needle after"
            val searchDao =
                FakeSearchDao(
                    passageBooks = listOf(passageBook()),
                    passagesByBook =
                    mapOf("book" to listOf(passageChapter(plainText = plainText))),
                )
            val repository = repository(searchDao, FakeSavedAnnotationDao())

            val result = repository.search("needle", bookId = "book").single()

            assertEquals(9, result.matchStartCodePointOffset)
            assertEquals(6, result.matchLengthCodePoints)
            assertEquals(plainText, result.snippet)
            assertEquals("book", searchDao.requestedBookFilters.single())
            assertEquals(1, searchDao.passageBookQueryCount)
            assertEquals(1, searchDao.passageChapterPageCallCount)
        }

    @Test
    fun supplementaryQueryLengthUsesBothOriginalMatchBoundaries() =
        runTest {
            val plainText = "😀 before 😀needle after"
            val searchDao =
                FakeSearchDao(
                    passageBooks = listOf(passageBook()),
                    passagesByBook =
                    mapOf("book" to listOf(passageChapter(plainText = plainText))),
                )
            val repository = repository(searchDao, FakeSavedAnnotationDao())

            val result = repository.search("😀needle", bookId = "book").single()

            assertEquals(9, result.matchStartCodePointOffset)
            assertEquals(7, result.matchLengthCodePoints)
        }

    @Test
    fun unmatchedEarlierChaptersDoNotPreventLaterMatches() =
        runTest {
            val searchDao =
                FakeSearchDao(
                    passageBooks = listOf(passageBook()),
                    passagesByBook =
                    mapOf(
                        "book" to
                            (0..35).map { chapterIndex ->
                                passageChapter(
                                    chapterIndex = chapterIndex,
                                    plainText =
                                    if (chapterIndex == 35) {
                                        "finally needle"
                                    } else {
                                        "no match here"
                                    },
                                )
                            },
                    ),
                )
            val repository = repository(searchDao, FakeSavedAnnotationDao())

            val result = repository.search("needle", bookId = "book").single()

            assertEquals(35, result.chapterIndex)
            assertEquals(1, searchDao.passageBookQueryCount)
            assertEquals(5, searchDao.passageChapterPageCallCount)
        }

    @Test
    fun passageSearchCapsMatchingChaptersRatherThanAllCandidates() =
        runTest {
            val searchDao =
                FakeSearchDao(
                    passageBooks = listOf(passageBook()),
                    passagesByBook =
                    mapOf(
                        "book" to
                            (0 until 40).map { chapterIndex ->
                                passageChapter(chapterIndex, plainText = "one needle")
                            },
                    ),
                )
            val repository = repository(searchDao, FakeSavedAnnotationDao())

            val results = repository.search("needle", bookId = "book")

            assertEquals(32, results.size)
            assertEquals(1, searchDao.passageBookQueryCount)
            assertEquals(4, searchDao.passageChapterPageCallCount)
        }

    @Test
    fun passageSearchPreservesPerChapterAndResultLimits() =
        runTest {
            val searchDao =
                FakeSearchDao(
                    passageBooks = listOf(passageBook()),
                    passagesByBook =
                    mapOf(
                        "book" to
                            (0 until 32).map { chapterIndex ->
                                passageChapter(
                                    chapterIndex,
                                    plainText = "needle needle needle needle",
                                )
                            },
                    ),
                )
            val repository = repository(searchDao, FakeSavedAnnotationDao())

            val results = repository.search("needle", bookId = "book")

            assertEquals(60, results.size)
            assertEquals(3, results.count { it.chapterIndex == 0 })
            assertEquals(1, searchDao.passageBookQueryCount)
            assertEquals(3, searchDao.passageChapterPageCallCount)
        }

    private fun repository(
        searchDao: SearchDao,
        annotationDao: SavedAnnotationDao,
    ): LibrarySearchRepositoryImpl =
        LibrarySearchRepositoryImpl(
            searchDao = searchDao,
            annotationDao = annotationDao,
            dispatcherProvider = UnconfinedDispatcherProvider,
        )
}

private class FakeSearchDao(
    private val bookSearch: suspend () -> List<BookEntity> = { emptyList() },
    private val passageBooks: List<SearchPassageBookEntity> = emptyList(),
    private val passagesByBook: Map<String, List<SearchPassageChapterPageEntity>> = emptyMap(),
    private val beforePassageBooks: suspend () -> Unit = {},
    private val beforePassageChapterPage: suspend () -> Unit = {},
) : SearchDao {
    var passageBookQueryCount = 0
        private set
    var passageChapterPageCallCount = 0
        private set
    val requestedBookFilters = mutableListOf<String?>()

    override suspend fun searchPassageBooks(bookId: String?): List<SearchPassageBookEntity> {
        passageBookQueryCount += 1
        requestedBookFilters += bookId
        beforePassageBooks()
        return passageBooks
            .filter { bookId == null || it.bookId == bookId }
            .filter { passagesByBook[it.bookId].orEmpty().isNotEmpty() }
            .sortedWith(
                compareBy<SearchPassageBookEntity> { it.bookTitle.lowercase(Locale.ROOT) }
                    .thenBy { it.bookId },
            )
    }

    override suspend fun searchPassageChapterPage(
        bookId: String,
        afterChapterIndex: Int,
        pageSize: Int,
    ): List<SearchPassageChapterPageEntity> {
        passageChapterPageCallCount += 1
        beforePassageChapterPage()
        return passagesByBook[bookId]
            .orEmpty()
            .asSequence()
            .filter { it.chapterIndex > afterChapterIndex }
            .sortedBy { it.chapterIndex }
            .take(pageSize)
            .toList()
    }

    override suspend fun searchBooks(
        pattern: String,
        limit: Int,
    ): List<BookEntity> = bookSearch()
}

private class FakeSavedAnnotationDao(private val search: suspend () -> List<SavedAnnotationWithBookEntity> = { emptyList() },) :
    SavedAnnotationDao {
    override suspend fun upsertInternal(entity: SavedAnnotationEntity) = Unit

    override suspend fun bookExists(bookId: String): Boolean = true

    override suspend fun delete(annotationId: String) = Unit

    override suspend fun deleteForBook(bookId: String) = Unit

    override fun observeForBook(bookId: String): Flow<List<SavedAnnotationEntity>> = emptyFlow()

    override fun observeWithBook(): Flow<List<SavedAnnotationWithBookEntity>> = emptyFlow()

    override suspend fun searchWithBook(
        pattern: String,
        limit: Int,
    ): List<SavedAnnotationWithBookEntity> = search()
}

private object UnconfinedDispatcherProvider : DispatcherProvider {
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
}

private fun book(): BookEntity =
    BookEntity(
        id = "book",
        title = "Needle Book",
        authors = listOf("Author"),
        languageTag = "en",
        coverImage = null,
    )

private fun passageBook(
    bookId: String = "book",
    bookTitle: String = "Needle Book",
): SearchPassageBookEntity =
    SearchPassageBookEntity(
        bookId = bookId,
        bookTitle = bookTitle,
    )

private fun passageChapter(
    chapterIndex: Int = 3,
    plainText: String = "before needle after",
): SearchPassageChapterPageEntity =
    SearchPassageChapterPageEntity(
        chapterIndex = chapterIndex,
        chapterTitle = "Late chapter",
        plainText = plainText,
    )

private fun savedWithBook(): SavedAnnotationWithBookEntity =
    SavedAnnotationWithBookEntity(
        annotation =
        SavedAnnotationEntity(
            id = "saved",
            bookId = "book",
            chapterIndex = 1,
            startTokenIndex = 4,
            endTokenIndex = 5,
            selectedText = "needle",
            note = "Saved needle",
            color = "YELLOW",
            kind = "HIGHLIGHT",
            createdAt = 1L,
            updatedAt = 1L,
        ),
        book = book(),
        chapterCount = 4,
    )
