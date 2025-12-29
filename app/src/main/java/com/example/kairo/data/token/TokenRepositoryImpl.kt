package com.example.kairo.data.token

import com.example.kairo.core.dispatchers.DispatcherProvider
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.countWords
import com.example.kairo.core.tokenization.TokenizerRegistry
import com.example.kairo.data.books.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class TokenRepositoryImpl(private val bookRepository: BookRepository, private val dispatcherProvider: DispatcherProvider,) :
    TokenRepository {
    // LRU cache with max 10 chapters to prevent unbounded memory growth
    private val cache =
        object : LinkedHashMap<String, List<Token>>(
            CACHE_INITIAL_CAPACITY,
            CACHE_LOAD_FACTOR,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, List<Token>>?
            ): Boolean =
                size > MAX_CACHED_CHAPTERS
        }
    private val mutex = Mutex()
    private val languageTagCache = mutableMapOf<String, String?>()
    private val tokenizationDispatcher = dispatcherProvider.default.limitedParallelism(1)
    private val prefetchScope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)

    override suspend fun getTokens(
        bookId: BookId,
        chapterIndex: Int,
        chapter: Chapter?,
    ): List<Token> {
        val languageTag = resolveLanguageTag(bookId)
        val key = buildCacheKey(bookId, chapterIndex, languageTag)
        val cached = mutex.withLock { cache[key] }
        if (cached != null) {
            prefetchNextChapter(bookId, chapterIndex + 1, languageTag)
            return cached
        }

        val resolvedChapter =
            chapter
                ?: withContext(dispatcherProvider.io) {
                    bookRepository.getChapter(bookId, chapterIndex)
                }
        val tokens =
            withContext(tokenizationDispatcher) {
                TokenizerRegistry.resolve(languageTag).tokenize(resolvedChapter)
            }
        updateChapterWordCount(bookId, chapterIndex, tokens)
        mutex.withLock { cache[key] = tokens }
        prefetchNextChapter(bookId, chapterIndex + 1, languageTag)
        return tokens
    }

    private fun prefetchNextChapter(
        bookId: BookId,
        nextIndex: Int,
        languageTag: String?,
    ) {
        val key = buildCacheKey(bookId, nextIndex, languageTag)
        prefetchScope.launch {
            val cached = mutex.withLock { cache.containsKey(key) }
            if (cached) return@launch

            val chapter =
                runCatching {
                    withContext(dispatcherProvider.io) {
                        bookRepository.getChapter(bookId, nextIndex)
                    }
                }.getOrNull() ?: return@launch
            val tokens =
                withContext(tokenizationDispatcher) {
                    TokenizerRegistry.resolve(languageTag).tokenize(chapter)
                }
            updateChapterWordCount(bookId, nextIndex, tokens)
            mutex.withLock { cache[key] = tokens }
        }
    }

    private suspend fun updateChapterWordCount(
        bookId: BookId,
        chapterIndex: Int,
        tokens: List<Token>,
    ) {
        if (tokens.isEmpty()) return
        val wordCount = countWords(tokens)
        if (wordCount <= 0) return
        bookRepository.updateChapterWordCount(bookId, chapterIndex, wordCount)
    }

    @Suppress("unused")
    fun clearCache() {
        prefetchScope.launch {
            mutex.withLock {
                cache.clear()
                languageTagCache.clear()
            }
        }
    }

    private suspend fun resolveLanguageTag(bookId: BookId): String? {
        val cached =
            mutex.withLock {
                if (languageTagCache.containsKey(bookId.value)) {
                    Pair(true, languageTagCache[bookId.value])
                } else {
                    Pair(false, null)
                }
            }
        if (cached.first) return cached.second
        val resolved = bookRepository.getBookLanguageTag(bookId)
        mutex.withLock { languageTagCache[bookId.value] = resolved }
        return resolved
    }

    private fun buildCacheKey(
        bookId: BookId,
        chapterIndex: Int,
        languageTag: String?,
    ): String {
        val tag = languageTag ?: "default"
        return "${bookId.value}-$chapterIndex-$tag"
    }

    companion object {
        private const val CACHE_INITIAL_CAPACITY = 16
        private const val CACHE_LOAD_FACTOR = 0.75f
        private const val MAX_CACHED_CHAPTERS = 10
    }
}
