package com.kairo.reader.data.search

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.LibrarySearchResult
import com.kairo.reader.core.model.LibrarySearchResultKind
import com.kairo.reader.data.local.SavedAnnotationDao
import com.kairo.reader.data.local.SearchDao
import com.kairo.reader.data.local.SearchPassageBookEntity
import com.kairo.reader.data.local.SearchPassageChapterPageEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class LibrarySearchRepositoryImpl(
    private val searchDao: SearchDao,
    private val annotationDao: SavedAnnotationDao,
    private val dispatcherProvider: DispatcherProvider,
) : LibrarySearchRepository {
    override suspend fun search(
        query: String,
        bookId: String?,
    ): List<LibrarySearchResult> =
        withContext(dispatcherProvider.io) {
            val normalized = normalizeLibrarySearchQuery(query)
            if (normalized.length < LibrarySearchConstraints.MIN_QUERY_LENGTH) return@withContext emptyList()
            currentCoroutineContext().ensureActive()
            if (bookId != null) {
                return@withContext searchPassages(normalized, bookId)
                    .take(LibrarySearchConstraints.MAX_RESULTS)
            }
            coroutineScope {
                // Start the cheap result groups before the complete-text passage query.
                val books = async { searchBookTitles(normalized.toSqlLikePattern()) }
                val saved = async { searchSaved(normalized.toSqlLikePattern()) }
                val passages = async { searchPassages(normalized, bookId = null) }
                fairMergeSearchResults(
                    groups = listOf(books.await(), passages.await(), saved.await()),
                    limit = LibrarySearchConstraints.MAX_RESULTS,
                )
            }
        }

    private suspend fun searchBookTitles(pattern: String): List<LibrarySearchResult> =
        searchDao.searchBooks(pattern, BOOK_RESULT_LIMIT).map { book ->
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

    private suspend fun searchPassages(
        query: String,
        bookId: String?,
    ): List<LibrarySearchResult> {
        val results = mutableListOf<LibrarySearchResult>()
        var matchingChapterCount = 0
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
                for (chapter in page) {
                    currentCoroutineContext().ensureActive()
                    if (appendChapterMatches(results, book, chapter, query)) matchingChapterCount += 1
                    if (
                        results.size >= PASSAGE_RESULT_LIMIT ||
                        matchingChapterCount >= PASSAGE_MATCHING_CHAPTER_LIMIT
                    ) {
                        return results
                    }
                }
                if (page.size < PASSAGE_PAGE_SIZE) break
                afterChapterIndex = page.last().chapterIndex
            }
        }
        return results
    }

    private suspend fun appendChapterMatches(
        results: MutableList<LibrarySearchResult>,
        book: SearchPassageBookEntity,
        chapter: SearchPassageChapterPageEntity,
        query: String,
    ): Boolean {
        val matchOffsets =
            findSearchMatchOffsets(chapter.plainText, query, MATCHES_PER_CHAPTER)
        if (matchOffsets.isEmpty()) return false
        for (matchOffset in matchOffsets) {
            currentCoroutineContext().ensureActive()
            results += chapter.toSearchResult(book, query, matchOffset)
            if (results.size >= PASSAGE_RESULT_LIMIT) break
        }
        return true
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

    private suspend fun searchSaved(pattern: String): List<LibrarySearchResult> =
        annotationDao.searchWithBook(pattern, SAVED_RESULT_LIMIT).map { item ->
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

    private companion object {
        const val BOOK_RESULT_LIMIT = 20
        const val PASSAGE_RESULT_LIMIT = 60
        const val PASSAGE_MATCHING_CHAPTER_LIMIT = 32
        const val PASSAGE_PAGE_SIZE = 8
        const val SAVED_RESULT_LIMIT = 20
        const val MATCHES_PER_CHAPTER = 3
        const val SNIPPET_CONTEXT_CHARS = 56
        const val INITIAL_CHAPTER_INDEX_CURSOR = -1
    }
}
