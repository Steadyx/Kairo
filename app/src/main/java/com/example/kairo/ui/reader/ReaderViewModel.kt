package com.example.kairo.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.nearestWordIndex
import com.example.kairo.core.tokenization.Tokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Reader screen.
 * Handles chapter loading, tokenization, and paragraph computation off the main thread.
 */
class ReaderViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // Tokenizer instance - has internal state (inDialogue) so we create per-use
    // or could keep one and rely on its reset behavior per chapter
    private val tokenizer = Tokenizer()

    // LRU cache for processed chapters - avoids re-tokenizing when switching back
    private val chapterCache = object : LinkedHashMap<Int, ChapterData>(5, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ChapterData>?): Boolean {
            return size > 5
        }
    }

    private var currentBook: Book? = null

    // Pending focus index to apply after chapter loads
    private var pendingFocusIndex: Int? = null

    /**
     * Load a book and optionally jump to a specific chapter and focus position.
     */
    fun loadBook(book: Book, initialChapterIndex: Int = 0, initialFocusIndex: Int = 0) {
        currentBook = book
        chapterCache.clear()  // Clear cache when loading new book
        pendingFocusIndex = if (initialFocusIndex > 0) initialFocusIndex else null
        loadChapter(initialChapterIndex)
    }

    /**
     * Load a chapter by index. Shows loading state immediately,
     * then processes tokens in background.
     */
    fun loadChapter(chapterIndex: Int) {
        val book = currentBook ?: return
        
        if (chapterIndex !in book.chapters.indices) return

        // Check cache first - instant load if available
        chapterCache[chapterIndex]?.let { cached ->
            // Use pending focus if set, otherwise use first word
            val focusIdx = pendingFocusIndex?.let { cached.tokens.nearestWordIndex(it) }
                ?: cached.firstWordIndex.coerceAtLeast(0)
            pendingFocusIndex = null

            _uiState.update {
                it.copy(
                    isLoading = false,
                    chapterIndex = chapterIndex,
                    chapterData = cached,
                    focusIndex = focusIdx
                )
            }
            // Preload adjacent chapters in background
            preloadAdjacentChapters(chapterIndex)
            return
        }

        // Not cached - show loading state immediately (UI stays responsive)
        _uiState.update { 
            it.copy(
                isLoading = true, 
                chapterIndex = chapterIndex,
                chapterData = null  // Clear old data while loading
            ) 
        }

        viewModelScope.launch {
            val result = processChapter(book.chapters[chapterIndex])

            // Cache the result
            result?.let { chapterCache[chapterIndex] = it }

            // Use pending focus if set, otherwise use first word
            val focusIdx = if (result != null) {
                pendingFocusIndex?.let { result.tokens.nearestWordIndex(it) }
                    ?: result.firstWordIndex.coerceAtLeast(0)
            } else 0
            pendingFocusIndex = null

            _uiState.update {
                it.copy(
                    isLoading = false,
                    chapterData = result,
                    focusIndex = focusIdx
                )
            }

            // Preload adjacent chapters after current one loads
            preloadAdjacentChapters(chapterIndex)
        }
    }

    /**
     * Process a chapter on a background thread.
     * Returns null if chapter is empty.
     */
    private suspend fun processChapter(chapter: Chapter): ChapterData? {
        return withContext(Dispatchers.Default) {
            // Tokenize using your existing Tokenizer
            val tokens = tokenizer.tokenize(chapter)
            
            if (tokens.isEmpty()) return@withContext null

            // Pre-compute paragraphs for lazy rendering
            val paragraphs = tokens.toParagraphs()

            // Find first word index for initial focus
            val firstWordIndex = tokens.indexOfFirst { it.type == TokenType.WORD }

            ChapterData(
                tokens = tokens,
                paragraphs = paragraphs,
                firstWordIndex = firstWordIndex
            )
        }
    }

    /**
     * Preload adjacent chapters in background so chapter switching feels instant.
     */
    private fun preloadAdjacentChapters(currentIndex: Int) {
        val book = currentBook ?: return

        viewModelScope.launch(Dispatchers.Default) {
            listOf(currentIndex - 1, currentIndex + 1)
                .filter { it in book.chapters.indices }
                .filter { it !in chapterCache }
                .forEach { index ->
                    processChapter(book.chapters[index])?.let { data ->
                        chapterCache[index] = data
                    }
                }
        }
    }

    fun setFocusIndex(index: Int) {
        _uiState.update { it.copy(focusIndex = index) }
    }

    fun nextChapter() {
        val book = currentBook ?: return
        val nextIndex = (_uiState.value.chapterIndex + 1).coerceAtMost(book.chapters.lastIndex)
        if (nextIndex != _uiState.value.chapterIndex) {
            loadChapter(nextIndex)
        }
    }

    fun previousChapter() {
        val prevIndex = (_uiState.value.chapterIndex - 1).coerceAtLeast(0)
        if (prevIndex != _uiState.value.chapterIndex) {
            loadChapter(prevIndex)
        }
    }

    /**
     * Clear cache when ViewModel is cleared to free memory.
     */
    override fun onCleared() {
        super.onCleared()
        chapterCache.clear()
    }
}

// =============================================================================
// State classes
// =============================================================================

data class ReaderUiState(
    val isLoading: Boolean = false,
    val chapterIndex: Int = 0,
    val focusIndex: Int = 0,
    val chapterData: ChapterData? = null
)

data class ChapterData(
    val tokens: List<Token>,
    val paragraphs: List<Paragraph>,
    val firstWordIndex: Int
)

/**
 * A paragraph is a group of tokens between PARAGRAPH_BREAK tokens.
 * Stores the starting index for mapping back to the original token list.
 */
data class Paragraph(
    val tokens: List<Token>,
    val startIndex: Int
)

/**
 * Split tokens into paragraphs for lazy rendering.
 * Your Tokenizer already creates PARAGRAPH_BREAK tokens, so we just split on those.
 */
fun List<Token>.toParagraphs(): List<Paragraph> {
    if (isEmpty()) return emptyList()

    val paragraphs = mutableListOf<Paragraph>()
    var currentTokens = mutableListOf<Token>()
    var startIndex = 0

    forEachIndexed { index, token ->
        when (token.type) {
            TokenType.PARAGRAPH_BREAK -> {
                if (currentTokens.isNotEmpty()) {
                    paragraphs.add(Paragraph(currentTokens.toList(), startIndex))
                    currentTokens = mutableListOf()
                }
                startIndex = index + 1
            }
            else -> {
                currentTokens.add(token)
            }
        }
    }

    // Don't forget the last paragraph (no trailing PARAGRAPH_BREAK)
    if (currentTokens.isNotEmpty()) {
        paragraphs.add(Paragraph(currentTokens.toList(), startIndex))
    }

    return paragraphs
}
