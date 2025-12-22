package com.example.kairo.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kairo.core.dispatchers.DispatcherProvider
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.nearestWordIndex
import com.example.kairo.data.books.BookRepository
import com.example.kairo.data.token.TokenRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HEX_RADIX = 16

/**
 * ViewModel for the Reader screen.
 * Handles chapter loading, tokenization, and paragraph computation off the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    private val bookRepository: BookRepository,
    private val tokenRepository: TokenRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // LRU cache for processed chapters - avoids re-tokenizing when switching back
    private val chapterCache =
        object : LinkedHashMap<Int, ChapterData>(
            CHAPTER_CACHE_INITIAL_CAPACITY,
            CHAPTER_CACHE_LOAD_FACTOR,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Int, ChapterData>?
            ): Boolean =
                size > MAX_CACHED_CHAPTERS
        }
    private val tokenizationDispatcher = dispatcherProvider.default.limitedParallelism(1)

    private var currentBook: Book? = null

    // Pending focus index to apply after chapter loads
    private var pendingFocusIndex: Int? = null

    /**
     * Load a book and optionally jump to a specific chapter and focus position.
     */
    fun loadBook(
        book: Book,
        initialChapterIndex: Int = 0,
        initialFocusIndex: Int = 0,
    ) {
        currentBook = book
        chapterCache.clear() // Clear cache when loading new book
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
        val cached = chapterCache[chapterIndex]
        if (cached != null) {
            // Use pending focus if set, otherwise use first word
            val focusIdx =
                pendingFocusIndex?.let { cached.tokens.nearestWordIndex(it) }
                    ?: cached.firstWordIndex.coerceAtLeast(0)
            pendingFocusIndex = null

            _uiState.update {
                it.copy(
                    isLoading = false,
                    chapterIndex = chapterIndex,
                    chapterData = cached,
                    focusIndex = focusIdx,
                )
            }
            // Preload adjacent chapters in background
            preloadAdjacentChapters(chapterIndex)
        } else {
            // Not cached - show loading state immediately (UI stays responsive)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    chapterIndex = chapterIndex,
                    chapterData = null, // Clear old data while loading
                )
            }

            viewModelScope.launch {
                val chapter =
                    runCatching {
                        withContext(dispatcherProvider.io) {
                            bookRepository.getChapter(book.id, chapterIndex)
                        }
                    }.getOrNull()

                val tokens =
                    if (chapter != null) {
                        runCatching {
                            tokenRepository.getTokens(book.id, chapterIndex, chapter)
                        }.getOrNull()
                    } else {
                        null
                    }

                val result =
                    if (chapter != null && tokens != null) {
                        processChapter(chapter, tokens)
                    } else {
                        null
                    }

                // Cache the result
                result?.let { chapterCache[chapterIndex] = it }

                // Use pending focus if set, otherwise use first word
                val focusIdx =
                    if (result != null) {
                        pendingFocusIndex?.let { result.tokens.nearestWordIndex(it) }
                            ?: result.firstWordIndex.coerceAtLeast(0)
                    } else {
                        0
                    }
                pendingFocusIndex = null

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        chapterData = result,
                        focusIndex = focusIdx,
                    )
                }

                // Preload adjacent chapters after current one loads
                preloadAdjacentChapters(chapterIndex)
            }
        }
    }

    /**
     * Process a chapter on a background thread.
     * Returns null if chapter is empty.
     */
    private suspend fun processChapter(
        chapter: Chapter,
        tokens: List<Token>,
    ): ChapterData? {
        return withContext(tokenizationDispatcher) {
            // Pre-compute paragraphs for lazy rendering
            val paragraphs = tokens.toParagraphs()

            // Find first word index for initial focus
            val firstWordIndex = tokens.indexOfFirst { it.type == TokenType.WORD }

            if (tokens.isEmpty() && chapter.imagePaths.isEmpty()) return@withContext null

            val blocks =
                buildReaderBlocks(
                    htmlContent = chapter.htmlContent,
                    paragraphs = paragraphs,
                    imagePaths = chapter.imagePaths,
                )

            ChapterData(
                tokens = tokens,
                paragraphs = paragraphs,
                blocks = blocks,
                firstWordIndex = firstWordIndex,
                imagePaths = chapter.imagePaths,
            )
        }
    }

    /**
     * Preload adjacent chapters in background so chapter switching feels instant.
     */
    private fun preloadAdjacentChapters(currentIndex: Int) {
        val book = currentBook ?: return

        viewModelScope.launch(tokenizationDispatcher) {
            listOf(currentIndex + 1)
                .filter { it in book.chapters.indices }
                .filter { it !in chapterCache }
                .forEach { index ->
                    val chapter =
                        runCatching {
                            withContext(dispatcherProvider.io) {
                                bookRepository.getChapter(book.id, index)
                            }
                        }.getOrNull() ?: return@forEach

                    val tokens = runCatching {
                        tokenRepository.getTokens(book.id, index, chapter)
                    }.getOrNull()
                    if (tokens != null) {
                        processChapter(chapter, tokens)?.let { data -> chapterCache[index] = data }
                    }
                }
        }
    }

    fun setFocusIndex(index: Int) {
        _uiState.update { it.copy(focusIndex = index) }
    }

    fun applyFocusIndex(index: Int) {
        val uiState = _uiState.value
        if (uiState.isLoading || uiState.chapterData == null) {
            pendingFocusIndex = index
        }
        _uiState.update { it.copy(focusIndex = index) }
    }

    @Suppress("unused")
    fun nextChapter() {
        val book = currentBook ?: return
        val nextIndex = (_uiState.value.chapterIndex + 1).coerceAtMost(book.chapters.lastIndex)
        if (nextIndex != _uiState.value.chapterIndex) {
            loadChapter(nextIndex)
        }
    }

    @Suppress("unused")
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

    companion object {
        private const val CHAPTER_CACHE_INITIAL_CAPACITY = 5
        private const val CHAPTER_CACHE_LOAD_FACTOR = 0.75f
        private const val MAX_CACHED_CHAPTERS = 5

        fun factory(
            bookRepository: BookRepository,
            tokenRepository: TokenRepository,
            dispatcherProvider: DispatcherProvider,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
                        return ReaderViewModel(
                            bookRepository,
                            tokenRepository,
                            dispatcherProvider
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

data class ReaderUiState(
    val isLoading: Boolean = false,
    val chapterIndex: Int = 0,
    val focusIndex: Int = 0,
    val chapterData: ChapterData? = null,
)

data class ChapterData(
    val tokens: List<Token>,
    val paragraphs: List<Paragraph>,
    val blocks: List<ReaderBlock>,
    val firstWordIndex: Int,
    val imagePaths: List<String>,
)

/**
 * A paragraph is a group of tokens between PARAGRAPH_BREAK tokens.
 * Stores the starting index for mapping back to the original token list.
 */
data class Paragraph(val tokens: List<Token>, val startIndex: Int,)

sealed interface ReaderBlock {
    val key: String
}

data class ReaderParagraphBlock(val paragraph: Paragraph,) : ReaderBlock {
    override val key: String = "paragraph_${paragraph.startIndex}"
}

data class ReaderImageBlock(val imagePath: String, val index: Int,) : ReaderBlock {
    override val key: String = "image_${index}_$imagePath"
}

/**
 * Split tokens into paragraphs for lazy rendering.
 * Your Tokenizer already creates PARAGRAPH_BREAK tokens, so we just split on those.
 */
fun List<Token>.toParagraphs(): List<Paragraph> {
    if (isEmpty()) return emptyList()

    val paragraphs = mutableListOf<Paragraph>()
    val currentTokens = mutableListOf<Token>()
    var startIndex = 0

    forEachIndexed { index, token ->
        when (token.type) {
            TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> {
                if (currentTokens.isNotEmpty()) {
                    paragraphs.add(Paragraph(currentTokens.toList(), startIndex))
                    currentTokens.clear()
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

private sealed interface HtmlBlockMarker {
    data object Paragraph : HtmlBlockMarker

    data class Image(val path: String,) : HtmlBlockMarker
}

private fun buildReaderBlocks(
    htmlContent: String,
    paragraphs: List<Paragraph>,
    imagePaths: List<String>,
): List<ReaderBlock> {
    if (paragraphs.isEmpty() && imagePaths.isEmpty()) {
        return emptyList()
    }

    val markers = extractHtmlBlockMarkers(htmlContent, imagePaths)
    val blocks = mutableListOf<ReaderBlock>()
    var paragraphIndex = 0
    var imageIndex = 0

    if (markers.isEmpty() && paragraphs.isEmpty() && imagePaths.isNotEmpty()) {
        imagePaths.forEachIndexed { index, path ->
            blocks.add(ReaderImageBlock(path, index))
        }
    } else {
        for (marker in markers) {
            when (marker) {
                HtmlBlockMarker.Paragraph -> {
                    val paragraph = paragraphs.getOrNull(paragraphIndex) ?: continue
                    blocks.add(ReaderParagraphBlock(paragraph))
                    paragraphIndex += 1
                }
                is HtmlBlockMarker.Image -> {
                    blocks.add(ReaderImageBlock(marker.path, imageIndex))
                    imageIndex += 1
                }
            }
        }

        while (paragraphIndex < paragraphs.size) {
            blocks.add(ReaderParagraphBlock(paragraphs[paragraphIndex]))
            paragraphIndex += 1
        }
    }

    return blocks
}

private fun extractHtmlBlockMarkers(
    htmlContent: String,
    imagePaths: List<String>,
): List<HtmlBlockMarker> {
    if (htmlContent.isBlank()) {
        return emptyList()
    }

    val cleaned =
        htmlContent
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")

    val blockSeparated =
        cleaned.replace(
            Regex("</?(p|div|br|h[1-6]|li|tr)[^>]*>", RegexOption.IGNORE_CASE),
            "\n\n",
        )
    val rawBlocks =
        blockSeparated
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

    val markers = mutableListOf<HtmlBlockMarker>()
    var fallbackIndex = 0
    val imgRegex =
        Regex(
            "<img[^>]+?src\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"][^>]*>",
            RegexOption.IGNORE_CASE,
        )

    rawBlocks.forEach { block ->
        val imageMarkers = mutableListOf<HtmlBlockMarker.Image>()
        imgRegex.findAll(block).forEach { match ->
            val resolved = resolveInlineImagePath(match.groupValues[1], imagePaths, fallbackIndex)
            if (resolved != null) {
                imageMarkers += HtmlBlockMarker.Image(resolved.first)
                fallbackIndex = resolved.second
            }
        }

        val text =
            block
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace(Regex("&#(\\d+);")) { match ->
                    match.groupValues[1]
                        .toIntOrNull()
                        ?.toChar()
                        ?.toString()
                        .orEmpty()
                }.replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                    match.groupValues[1]
                        .toIntOrNull(HEX_RADIX)
                        ?.toChar()
                        ?.toString()
                        .orEmpty()
                }.replace(Regex("[ \\t]+"), " ")
                .trim()

        if (text.isNotBlank()) {
            markers += HtmlBlockMarker.Paragraph
        }

        markers.addAll(imageMarkers)
    }

    return markers
}

private fun resolveInlineImagePath(
    rawSrc: String,
    imagePaths: List<String>,
    fallbackIndex: Int,
): Pair<String, Int>? {
    val src = sanitizeInlineSrc(rawSrc)
    return when {
        src.isBlank() -> null
        src.startsWith("data:", ignoreCase = true) -> null
        src.startsWith("http://", ignoreCase = true) -> null
        src.startsWith("https://", ignoreCase = true) -> null
        src.startsWith("kairo_epub_assets/") -> src to fallbackIndex
        else -> imagePaths.getOrNull(fallbackIndex)?.let { it to (fallbackIndex + 1) }
    }
}

private fun sanitizeInlineSrc(rawSrc: String): String {
    val trimmed = rawSrc.trim()
    if (trimmed.isBlank()) return ""
    return trimmed
        .substringBefore('#')
        .substringBefore('?')
        .trim()
}
