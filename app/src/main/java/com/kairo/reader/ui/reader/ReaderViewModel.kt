package com.kairo.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TableOfContentsTarget
import com.kairo.reader.core.model.countWords
import com.kairo.reader.core.model.nearestWordIndex
import com.kairo.reader.data.books.BookRepository
import com.kairo.reader.data.search.codePointOffsetToUtf16Offset
import com.kairo.reader.data.token.TokenRepository
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGINATION_WORDS_MIN_DELTA = 14

@Suppress("TooGenericExceptionCaught")
private suspend fun <T> runCatchingPreservingCancellation(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        Result.failure(failure)
    }

private class ReaderBookSession(val book: Book,) {
    val cache = ReaderChapterCache()
}

/**
 * ViewModel for the Reader screen.
 * Handles chapter loading, tokenization, and paragraph computation off the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    private val bookRepository: BookRepository,
    private val tokenRepository: TokenRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val chapterProcessor: ReaderChapterProcessor = ReaderChapterProcessor(),
    private val imageBoundsResolver: ReaderImageBoundsResolver = ReaderImageBoundsResolver.NoOp,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val tokenizationDispatcher = dispatcherProvider.default.limitedParallelism(1)

    private val activeSession = AtomicReference<ReaderBookSession?>(null)
    private val chapterLoadSequence = AtomicInteger(0)

    // Pending focus index to apply after chapter loads (thread-safe for cross-coroutine access)
    private val pendingFocusIndex = AtomicReference<Int?>(null)
    private val pendingPageIndex = AtomicReference<Int?>(null)
    private val pendingCharacterOffset = AtomicReference<Int?>(null)
    private val pendingSearchCodePointOffset = AtomicReference<Int?>(null)
    private val wordsPerPageTarget = AtomicReference(DEFAULT_WORDS_PER_PAGE)

    /**
     * Load a book and optionally jump to a specific chapter and focus position.
     */
    fun loadBook(
        book: Book,
        initialChapterIndex: Int = 0,
        initialFocusIndex: Int = 0,
        initialSearchCodePointOffset: Int? = null,
    ) {
        val session = ReaderBookSession(book)
        activeSession.set(session)
        chapterLoadSequence.incrementAndGet()
        pendingFocusIndex.set(if (initialFocusIndex > 0) initialFocusIndex else null)
        pendingPageIndex.set(null)
        pendingCharacterOffset.set(null)
        pendingSearchCodePointOffset.set(initialSearchCodePointOffset)
        _uiState.update { it.copy(bookWordCounts = emptyList(), bookTotalWords = 0) }
        loadBookWordCounts(session)
        loadChapter(
            session = session,
            chapterIndex = initialChapterIndex,
            initialSearchCodePointOffset = initialSearchCodePointOffset,
        )
    }

    private fun loadBookWordCounts(session: ReaderBookSession) {
        val book = session.book
        val bookId = book.id
        val initialCounts = book.chapters.map { it.wordCount }
        _uiState.update {
            it.copy(
                bookWordCounts = initialCounts,
                bookTotalWords = initialCounts.sum(),
            )
        }
        if (initialCounts.all { it > 0 } || book.chapters.isEmpty()) return

        viewModelScope.launch {
            val counts =
                runCatching {
                    withContext(dispatcherProvider.io) {
                        book.chapters.map { chapter ->
                            if (chapter.wordCount > 0) {
                                chapter.wordCount
                            } else {
                                val resolved =
                                    runCatching {
                                        bookRepository.getChapter(bookId, chapter.index)
                                    }.getOrNull()
                                val count =
                                    if (resolved == null) {
                                        0
                                    } else {
                                        countWords(resolved.plainText)
                                    }
                                if (count > 0) {
                                    bookRepository.updateChapterWordCount(
                                        bookId,
                                        chapter.index,
                                        count,
                                    )
                                }
                                count
                            }
                        }
                    }
                }.getOrNull() ?: emptyList()

            if (activeSession.get() !== session) return@launch
            val total = counts.sum()
            _uiState.update { it.copy(bookWordCounts = counts, bookTotalWords = total) }
        }
    }

    /**
     * Load a chapter by index. Shows loading state immediately,
     * then processes tokens in background.
     */
    fun loadChapter(
        chapterIndex: Int,
        initialFocusIndex: Int? = null,
        initialPageIndex: Int? = null,
        initialCharacterOffset: Int? = null,
        initialSearchCodePointOffset: Int? = null,
    ) {
        val session = activeSession.get() ?: return
        loadChapter(
            session = session,
            chapterIndex = chapterIndex,
            initialFocusIndex = initialFocusIndex,
            initialPageIndex = initialPageIndex,
            initialCharacterOffset = initialCharacterOffset,
            initialSearchCodePointOffset = initialSearchCodePointOffset,
        )
    }

    private fun loadChapter(
        session: ReaderBookSession,
        chapterIndex: Int,
        initialFocusIndex: Int? = null,
        initialPageIndex: Int? = null,
        initialCharacterOffset: Int? = null,
        initialSearchCodePointOffset: Int? = null,
    ) {
        if (activeSession.get() !== session) return
        val book = session.book

        if (chapterIndex !in book.chapters.indices) return
        val requestId = chapterLoadSequence.incrementAndGet()

        pendingCharacterOffset.set(initialCharacterOffset)
        pendingSearchCodePointOffset.set(initialSearchCodePointOffset)
        if (initialCharacterOffset != null || initialSearchCodePointOffset != null) {
            pendingFocusIndex.set(null)
            pendingPageIndex.set(null)
        } else if (initialFocusIndex != null) {
            pendingFocusIndex.set(initialFocusIndex)
        }
        when {
            initialPageIndex != null -> pendingPageIndex.set(initialPageIndex)
            initialFocusIndex == Int.MAX_VALUE -> pendingPageIndex.set(Int.MAX_VALUE)
        }

        // Check cache first - instant load if available
        val cached = session.cache[chapterIndex]
        if (cached != null) {
            if (!isActiveSessionRequest(session, requestId)) return
            // Use pending focus if set, otherwise use first word
            val pageIdx = pendingPageIndex.getAndSet(null)
            val focusIdx =
                consumePendingUtf16Offset(cached.plainText)?.let { characterOffset ->
                    ReaderTextPositionResolver.resolveTokenIndex(
                        plainText = cached.plainText,
                        tokens = cached.tokens,
                        characterOffset = characterOffset,
                    )
                }
                    ?: pendingFocusIndex.getAndSet(null)?.let { cached.tokens.nearestWordIndex(it) }
                    ?: cached.firstWordIndex.coerceAtLeast(0)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    chapterIndex = chapterIndex,
                    chapterData = cached,
                    chapterLoadError = null,
                    focusIndex = focusIdx,
                    pageIndexOverride = pageIdx,
                )
            }
            // Preload adjacent chapters in background
            preloadAdjacentChapters(session, chapterIndex)
        } else {
            // Not cached - show loading state immediately (UI stays responsive)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    chapterIndex = chapterIndex,
                    chapterData = null, // Clear old data while loading
                    chapterLoadError = null,
                )
            }

            viewModelScope.launch {
                val processed =
                    runCatchingPreservingCancellation {
                        val chapter =
                            withContext(dispatcherProvider.io) {
                                bookRepository.getChapter(book.id, chapterIndex)
                            }
                        val tokens = tokenRepository.getTokens(book.id, chapterIndex, chapter)
                        processChapter(chapter, tokens)
                    }
                val result = processed.getOrNull()
                val errorMessage =
                    processed.exceptionOrNull()?.message
                        ?.takeIf { it.isNotBlank() }
                        ?: if (processed.isFailure) DEFAULT_CHAPTER_LOAD_ERROR else null

                if (!isActiveSessionRequest(session, requestId)) {
                    return@launch
                }

                // Cache the result
                result?.let {
                    session.cache[chapterIndex] = it
                }

                // Use pending focus if set, otherwise use first word
                val focusIdx =
                    if (result != null) {
                        consumePendingUtf16Offset(result.plainText)?.let { characterOffset ->
                            ReaderTextPositionResolver.resolveTokenIndex(
                                plainText = result.plainText,
                                tokens = result.tokens,
                                characterOffset = characterOffset,
                            )
                        }
                            ?: pendingFocusIndex.getAndSet(null)?.let { result.tokens.nearestWordIndex(it) }
                            ?: result.firstWordIndex.coerceAtLeast(0)
                    } else {
                        pendingFocusIndex.set(null)
                        pendingPageIndex.set(null)
                        pendingCharacterOffset.set(null)
                        pendingSearchCodePointOffset.set(null)
                        0
                    }
                val pageIdx = if (result != null) pendingPageIndex.getAndSet(null) else null

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        chapterData = result,
                        chapterLoadError = errorMessage,
                        focusIndex = focusIdx,
                        pageIndexOverride = pageIdx,
                    )
                }

                // Preload adjacent chapters after current one loads
                preloadAdjacentChapters(session, chapterIndex)
            }
        }
    }

    private fun isActiveSessionRequest(
        session: ReaderBookSession,
        requestId: Int,
    ): Boolean =
        activeSession.get() === session &&
            chapterLoadSequence.get() == requestId

    fun loadTableOfContentsTarget(target: TableOfContentsTarget) {
        loadChapter(
            chapterIndex = target.chapterIndex,
            initialCharacterOffset = target.characterOffset,
        )
    }

    private fun consumePendingUtf16Offset(plainText: String): Int? {
        val searchCodePointOffset = pendingSearchCodePointOffset.getAndSet(null)
        val characterOffset = pendingCharacterOffset.getAndSet(null)
        return searchCodePointOffset?.let { offset ->
            codePointOffsetToUtf16Offset(plainText, offset)
        } ?: characterOffset
    }

    /**
     * Process a chapter on a background thread.
     * Returns null if chapter is empty.
     */
    private suspend fun processChapter(
        chapter: Chapter,
        tokens: List<Token>,
    ): ChapterData? {
        val chapterData =
            withContext(tokenizationDispatcher) {
                chapterProcessor.process(chapter, tokens, wordsPerPageTarget.get())
            } ?: return null

        return resolveMissingImageBounds(chapterData)
    }

    private suspend fun resolveMissingImageBounds(chapterData: ChapterData): ChapterData {
        val unresolvedPaths =
            chapterData.blocks
                .asSequence()
                .filterIsInstance<ReaderImageBlock>()
                .filterNot { it.imageSize.hasCompleteValidImageSize() }
                .map { it.imagePath }
                .distinct()
                .toList()
        if (unresolvedPaths.isEmpty()) return chapterData

        val intrinsicSizesByPath = mutableMapOf<String, ReaderImageSize?>()
        unresolvedPaths.forEach { imagePath ->
            intrinsicSizesByPath[imagePath] = imageBoundsResolver.resolve(imagePath)
        }
        val resolvedBlocks =
            chapterData.blocks.map { block ->
                if (block !is ReaderImageBlock || block.imageSize.hasCompleteValidImageSize()) {
                    block
                } else {
                    val mergedSize =
                        mergeReaderImageSize(
                            authoredSize = block.imageSize,
                            intrinsicSize = intrinsicSizesByPath[block.imagePath],
                        )
                    if (mergedSize == block.imageSize) block else block.copy(imageSize = mergedSize)
                }
            }

        return if (resolvedBlocks == chapterData.blocks) {
            chapterData
        } else {
            chapterData.copy(blocks = resolvedBlocks)
        }
    }

    /**
     * Preload adjacent chapters in background so chapter switching feels instant.
     */
    private fun preloadAdjacentChapters(
        session: ReaderBookSession,
        currentIndex: Int,
    ) {
        val book = session.book

        viewModelScope.launch(tokenizationDispatcher) {
            listOf(currentIndex + 1)
                .filter { it in book.chapters.indices }
                .filter { index ->
                    !session.cache.contains(index)
                }
                .forEach { index ->
                    val data =
                        runCatchingPreservingCancellation {
                            val chapter =
                                withContext(dispatcherProvider.io) {
                                    bookRepository.getChapter(book.id, index)
                                }
                            val tokens = tokenRepository.getTokens(book.id, index, chapter)
                            processChapter(chapter, tokens)
                        }.getOrNull() ?: return@forEach
                    session.cache[index] = data
                }
        }
    }

    fun setFocusIndex(index: Int) {
        _uiState.update { it.copy(focusIndex = index, pageIndexOverride = null) }
    }

    fun applyFocusIndex(index: Int) {
        val uiState = _uiState.value
        if (uiState.isLoading || uiState.chapterData == null) {
            pendingFocusIndex.set(index)
            pendingPageIndex.set(null)
        }
        _uiState.update { it.copy(focusIndex = index, pageIndexOverride = null) }
    }

    fun setPageIndex(
        pageIndex: Int,
        focusIndex: Int,
    ) {
        val uiState = _uiState.value
        if (uiState.isLoading || uiState.chapterData == null) {
            pendingFocusIndex.set(focusIndex)
            pendingPageIndex.set(pageIndex)
        }
        _uiState.update {
            it.copy(
                focusIndex = focusIndex,
                pageIndexOverride = pageIndex,
            )
        }
    }

    fun updatePaginationMetrics(
        fontSizeSp: Float,
        viewportHeightDp: Int,
    ) {
        val resolvedWordsPerPage = chapterProcessor.wordsPerPage(fontSizeSp, viewportHeightDp)
        val previousWordsPerPage = wordsPerPageTarget.get()
        if (abs(resolvedWordsPerPage - previousWordsPerPage) < PAGINATION_WORDS_MIN_DELTA) return
        wordsPerPageTarget.set(resolvedWordsPerPage)

        val session = activeSession.get() ?: return
        session.cache.transformAll { chapterProcessor.repage(it, resolvedWordsPerPage) }
        if (activeSession.get() !== session) return

        _uiState.update { state ->
            val chapterData = state.chapterData ?: return@update state
            state.copy(chapterData = chapterProcessor.repage(chapterData, resolvedWordsPerPage))
        }
    }

    @Suppress("unused")
    fun nextChapter() {
        val session = activeSession.get() ?: return
        val book = session.book
        val nextIndex = (_uiState.value.chapterIndex + 1).coerceAtMost(book.chapters.lastIndex)
        if (nextIndex != _uiState.value.chapterIndex) {
            loadChapter(session = session, chapterIndex = nextIndex)
        }
    }

    @Suppress("unused")
    fun previousChapter() {
        val session = activeSession.get() ?: return
        val prevIndex = (_uiState.value.chapterIndex - 1).coerceAtLeast(0)
        if (prevIndex != _uiState.value.chapterIndex) {
            loadChapter(session = session, chapterIndex = prevIndex)
        }
    }

    /**
     * Clear cache when ViewModel is cleared to free memory.
     */
    override fun onCleared() {
        super.onCleared()
        val session = activeSession.getAndSet(null)
        chapterLoadSequence.incrementAndGet()
        session?.cache?.clear()
    }

    companion object {
        private const val DEFAULT_CHAPTER_LOAD_ERROR = "Chapter could not be loaded."

        fun factory(
            bookRepository: BookRepository,
            tokenRepository: TokenRepository,
            dispatcherProvider: DispatcherProvider,
            imageBoundsResolver: ReaderImageBoundsResolver = ReaderImageBoundsResolver.NoOp,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
                        return ReaderViewModel(
                            bookRepository,
                            tokenRepository,
                            dispatcherProvider,
                            imageBoundsResolver = imageBoundsResolver,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

// =============================================================================
// State classes
// =============================================================================
