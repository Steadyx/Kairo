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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySearchRepositoryImplTest {
    @Test
    fun titleResultsArriveWhilePassageSearchIsStillBlocked() = runTest {
        val repository = repository(
            FakeSearchDao(
                bookSearch = { listOf(book()) },
                passageBooks = listOf(passageBook()),
                passagesByBook = mapOf("book" to listOf(passageChapter())),
                beforePassageChapterPage = { awaitCancellation() },
            ),
            FakeSavedAnnotationDao(),
        )
        val updates = mutableListOf<LibrarySearchUpdate>()
        val job = launch { repository.searchUpdates("needle").toList(updates) }
        runCurrent()
        assertTrue(updates.any { update -> update.results.any { it.kind == LibrarySearchResultKind.BOOK } })
        assertTrue(updates.none { it.isComplete })
        job.cancelAndJoin()
    }

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
    fun passageSearchFindsMatchesAcrossMoreThanThirtyTwoChapters() =
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

            assertEquals(40, results.size)
            assertEquals(1, searchDao.passageBookQueryCount)
            assertEquals(6, searchDao.passageChapterPageCallCount)
        }

    @Test
    fun resultPagesReachLaterMatchesAndBooksWithoutRepeatingResults() =
        runTest {
            val searchDao =
                FakeSearchDao(
                    passageBooks = listOf(passageBook("a", "A"), passageBook("b", "B")),
                    passagesByBook =
                    mapOf(
                        "a" to listOf(passageChapter(0, plainText = "needle ".repeat(65))),
                        "b" to listOf(passageChapter(0, plainText = "later needle")),
                    ),
                )
            val repository = repository(searchDao, FakeSavedAnnotationDao())

            val first = repository.searchUpdates("needle", resultPage = 0).toList().last()
            val second = repository.searchUpdates("needle", resultPage = 1).toList().last()

            assertEquals(60, first.results.size)
            assertTrue(first.hasMoreResults)
            assertEquals(6, second.results.size)
            assertEquals("b", second.results.last().bookId.value)
            assertFalse(second.hasMoreResults)
            assertTrue(first.results.map { it.id }.intersect(second.results.map { it.id }.toSet()).isEmpty())
        }

    @Test
    fun titleAndSavedResultsBeyondFirstTwentyAreReachable() = runTest {
        val books = (0..20).map { index -> book().copy(id = "book-$index", title = "Book $index") }
        val saved = (0..20).map { index ->
            savedWithBook().let { it.copy(annotation = it.annotation.copy(id = "saved-$index")) }
        }
        val repository = repository(FakeSearchDao(bookSearch = { books }), FakeSavedAnnotationDao { saved })

        val first = repository.searchUpdates("needle").toList().last()
        val second = repository.searchUpdates("needle", resultPage = 1).toList().last()

        assertEquals(40, first.results.size)
        assertTrue(first.hasMoreResults)
        assertEquals(setOf("book:book-20", "saved:saved-20"), second.results.map { it.id }.toSet())
        assertFalse(second.hasMoreResults)
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

    override suspend fun searchPassageChapterMetadataPage(
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

    override suspend fun getChapterHtmlChunk(
        bookId: String,
        chapterIndex: Int,
        offset: Int,
        byteCount: Int,
    ): com.kairo.reader.data.local.ChapterTextChunk? =
        error("Passage search must not load HTML")

    override suspend fun getChapterPlainTextChunk(
        bookId: String,
        chapterIndex: Int,
        offset: Int,
        byteCount: Int,
    ): com.kairo.reader.data.local.ChapterTextChunk? {
        val bytes = passagesByBook[bookId]?.firstOrNull { it.chapterIndex == chapterIndex }
            ?.plainText?.toByteArray() ?: return null
        val start = (offset - 1).coerceAtMost(bytes.size)
        return com.kairo.reader.data.local.ChapterTextChunk(bytes.copyOfRange(start, (start + byteCount).coerceAtMost(bytes.size)))
    }

    override suspend fun searchBooks(
        pattern: String,
        limit: Int,
        offset: Long,
    ): List<BookEntity> = bookSearch().drop(offset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take(limit)
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
        offset: Long,
    ): List<SavedAnnotationWithBookEntity> = search().drop(offset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take(limit)
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
