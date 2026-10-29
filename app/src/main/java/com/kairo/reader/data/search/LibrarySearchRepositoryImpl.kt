package com.kairo.reader.data.search

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.LibrarySearchResult
import com.kairo.reader.core.model.LibrarySearchResultKind
import com.kairo.reader.data.local.SavedAnnotationDao
import com.kairo.reader.data.local.SearchDao
import com.kairo.reader.data.local.SearchPassageBookEntity
import com.kairo.reader.data.local.SearchPassageChapterPageEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LibrarySearchRepositoryImpl(
    private val searchDao: SearchDao,
    private val annotationDao: SavedAnnotationDao,
    private val dispatcherProvider: DispatcherProvider,
) : LibrarySearchRepository {
    override suspend fun search(query: String, bookId: String?): List<LibrarySearchResult> =
        searchUpdates(query, bookId).last().results

    override fun searchUpdates(query: String, bookId: String?, resultPage: Int): Flow<LibrarySearchUpdate> = channelFlow {
        val normalized = normalizeLibrarySearchQuery(query)
        if (normalized.length < LibrarySearchConstraints.MIN_QUERY_LENGTH) {
            send(LibrarySearchUpdate(emptyList(), isComplete = true))
            return@channelFlow
        }
        if (bookId != null) {
            val results = searchPassages(normalized, bookId, resultPage) { partial ->
                send(LibrarySearchUpdate(partial, isComplete = false))
            }
            send(LibrarySearchUpdate(results.items, isComplete = true, hasMoreResults = results.hasMore))
            return@channelFlow
        }
        val groups = MutableList(SEARCH_GROUP_COUNT) { emptyList<LibrarySearchResult>() }
        val completed = mutableSetOf<Int>()
        val publicationMutex = Mutex()
        val hasMoreByGroup = BooleanArray(SEARCH_GROUP_COUNT)
        suspend fun publish(index: Int, results: List<LibrarySearchResult>, complete: Boolean, hasMore: Boolean = false) {
            publicationMutex.withLock {
                groups[index] = results
                if (complete) completed += index
                if (complete) hasMoreByGroup[index] = hasMore
                send(
                    LibrarySearchUpdate(
                        fairMergeSearchResults(groups, LibrarySearchConstraints.MAX_RESULTS),
                        isComplete = completed.size == SEARCH_GROUP_COUNT,
                        hasMoreResults = hasMoreByGroup.any { it },
                    )
                )
            }
        }
        launch {
            val results = searchBookTitles(normalized.toSqlLikePattern(), resultPage)
            publish(BOOK_GROUP, results.items, complete = true, hasMore = results.hasMore)
        }
        launch {
            val results = searchSaved(normalized.toSqlLikePattern(), resultPage)
            publish(SAVED_GROUP, results.items, complete = true, hasMore = results.hasMore)
        }
        launch {
            val results = searchPassages(normalized, bookId = null, resultPage = resultPage) { partial ->
                publish(PASSAGE_GROUP, partial, complete = false)
            }
            publish(PASSAGE_GROUP, results.items, complete = true, hasMore = results.hasMore)
        }
    }.flowOn(dispatcherProvider.io)

    private suspend fun searchBookTitles(pattern: String, page: Int): SearchResultPage {
        val books = searchDao.searchBooks(pattern, BOOK_RESULT_LIMIT + 1, pageOffset(page, BOOK_RESULT_LIMIT))
        val results = books.take(BOOK_RESULT_LIMIT).map { book ->
            LibrarySearchResult(
                id = "book:${book.id}",
                kind = LibrarySearchResultKind.BOOK,
                bookId = BookId(book.id),
                bookTitle = book.title,
                chapterIndex = 0,
                chapterTitle = null,
                tokenIndex = 0,
                endTokenIndex = 0,
                title = book.title,
                snippet = book.authors.joinToString(", "),
            )
        }
        return SearchResultPage(results, hasMore = books.size > BOOK_RESULT_LIMIT)
    }

    private suspend fun searchPassages(
        query: String,
        bookId: String?,
        resultPage: Int,
        onResults: suspend (List<LibrarySearchResult>) -> Unit,
    ): SearchResultPage {
        val results = mutableListOf<LibrarySearchResult>()
        val scan = PassageScan(resultPage.coerceAtLeast(0).toLong() * LibrarySearchConstraints.PASSAGE_RESULTS_PER_PAGE)
        val books = searchDao.searchPassageBooks(bookId)
        for (book in books) {
            currentCoroutineContext().ensureActive()
            var afterChapterIndex = INITIAL_CHAPTER_INDEX_CURSOR
            while (true) {
                currentCoroutineContext().ensureActive()
                val page =
                    searchDao.searchPassageChapterPage(
                        bookId = book.bookId,
                        afterChapterIndex = afterChapterIndex,
                        pageSize = PASSAGE_PAGE_SIZE,
                    )
                if (page.isEmpty()) break
                currentCoroutineContext().ensureActive()
                val previousResultCount = results.size
                for (chapter in page) {
                    currentCoroutineContext().ensureActive()
                    if (appendChapterMatches(results, book, chapter, query, scan)) {
                        return SearchResultPage(results, hasMore = true)
                    }
                }
                if (results.size != previousResultCount) onResults(results.toList())
                if (page.size < PASSAGE_PAGE_SIZE) break
                afterChapterIndex = page.last().chapterIndex
            }
        }
        return SearchResultPage(results, hasMore = false)
    }

    private suspend fun appendChapterMatches(
        results: MutableList<LibrarySearchResult>,
        book: SearchPassageBookEntity,
        chapter: SearchPassageChapterPageEntity,
        query: String,
        scan: PassageScan,
    ): Boolean {
        var searchFrom = 0
        while (searchFrom <= chapter.plainText.length - query.length) {
            currentCoroutineContext().ensureActive()
            val match = chapter.plainText.indexOf(query, startIndex = searchFrom, ignoreCase = true)
            if (match < 0) break
            if (scan.seenMatches >= scan.startOffset) {
                if (results.size == LibrarySearchConstraints.PASSAGE_RESULTS_PER_PAGE) return true
                results += chapter.toSearchResult(book, query, match)
            }
            scan.seenMatches += 1
            searchFrom = match + query.length
        }
        return false
    }

    private fun SearchPassageChapterPageEntity.toSearchResult(
        book: SearchPassageBookEntity,
        query: String,
        matchStartUtf16Offset: Int,
    ): LibrarySearchResult {
        val matchEndUtf16Offset =
            (matchStartUtf16Offset.toLong() + query.length).coerceAtMost(plainText.length.toLong())
                .toInt()
        val matchStartCodePointOffset =
            plainText.codePointCount(0, matchStartUtf16Offset)
        val matchEndCodePointOffset =
            plainText.codePointCount(0, matchEndUtf16Offset)
        return LibrarySearchResult(
            id = "passage:${book.bookId}:$chapterIndex:$matchStartCodePointOffset",
            kind = LibrarySearchResultKind.PASSAGE,
            bookId = BookId(book.bookId),
            bookTitle = book.bookTitle,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            tokenIndex = 0,
            endTokenIndex = 0,
            matchStartCodePointOffset = matchStartCodePointOffset,
            matchLengthCodePoints = matchEndCodePointOffset - matchStartCodePointOffset,
            title = chapterTitle?.takeIf(String::isNotBlank) ?: book.bookTitle,
            snippet =
            buildSearchSnippet(
                text = plainText,
                matchOffset = matchStartUtf16Offset,
                matchLength = query.length,
                contextCharacters = SNIPPET_CONTEXT_CHARS,
            ),
        )
    }

    private suspend fun searchSaved(pattern: String, page: Int): SearchResultPage {
        val saved = annotationDao.searchWithBook(pattern, SAVED_RESULT_LIMIT + 1, pageOffset(page, SAVED_RESULT_LIMIT))
        val results = saved.take(SAVED_RESULT_LIMIT).map { item ->
            val annotation = item.annotation
            LibrarySearchResult(
                id = "saved:${annotation.id}",
                kind = LibrarySearchResultKind.SAVED,
                bookId = BookId(annotation.bookId),
                bookTitle = item.book.title,
                chapterIndex = annotation.chapterIndex,
                chapterTitle = null,
                tokenIndex = annotation.startTokenIndex,
                endTokenIndex = annotation.endTokenIndex,
                title = annotation.note.takeIf(String::isNotBlank) ?: item.book.title,
                snippet = annotation.selectedText,
            )
        }
        return SearchResultPage(results, hasMore = saved.size > SAVED_RESULT_LIMIT)
    }

    private fun pageOffset(page: Int, pageSize: Int): Long = page.coerceAtLeast(0).toLong() * pageSize

    private companion object {
        const val SEARCH_GROUP_COUNT = 3
        const val BOOK_GROUP = 0
        const val PASSAGE_GROUP = 1
        const val SAVED_GROUP = 2
        const val BOOK_RESULT_LIMIT = 20
        const val PASSAGE_PAGE_SIZE = 8
        const val SAVED_RESULT_LIMIT = 20
        const val SNIPPET_CONTEXT_CHARS = 56
        const val INITIAL_CHAPTER_INDEX_CURSOR = -1
    }
}

private data class SearchResultPage(val items: List<LibrarySearchResult>, val hasMore: Boolean)

private class PassageScan(val startOffset: Long, var seenMatches: Long = 0)
