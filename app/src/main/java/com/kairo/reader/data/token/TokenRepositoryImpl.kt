package com.kairo.reader.data.token

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.countWords
import com.kairo.reader.core.tokenization.TokenizerRegistry
import com.kairo.reader.data.books.BookRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class TokenRepositoryImpl(private val bookRepository: BookRepository, private val dispatcherProvider: DispatcherProvider,) :
    TokenRepository {
    private data class CacheKey(val bookId: String, val chapterIndex: Int, val languageTag: String?,)

    // LRU cache with max 10 chapters to prevent unbounded memory growth.
    private val cache =
        object : LinkedHashMap<CacheKey, List<Token>>(
            CACHE_INITIAL_CAPACITY,
            CACHE_LOAD_FACTOR,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<CacheKey, List<Token>>?
            ): Boolean = size > MAX_CACHED_CHAPTERS
        }
    private val cacheLock = Any()
    private data class LoadKey(val chapter: CacheKey, val generation: Long)

    private val inFlight = mutableMapOf<LoadKey, Deferred<List<Token>>>()
    private val languageTagCache = mutableMapOf<String, String?>()
    private val bookGenerations = mutableMapOf<String, Long>()
    private var globalGeneration = 0L
    private var generationCounter = 0L
    private val tokenizationDispatcher = dispatcherProvider.default.limitedParallelism(1)
    private val loadScope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)

    override suspend fun getTokens(
        bookId: BookId,
        chapterIndex: Int,
        chapter: Chapter?,
    ): List<Token> {
        val generation = currentGeneration(bookId)
        val languageTag = resolveLanguageTag(bookId, generation)
        val key = CacheKey(bookId.value, chapterIndex, languageTag)
        return loadTokens(key, generation, chapter).await()
    }

    private fun loadTokens(
        key: CacheKey,
        generation: Long,
        chapter: Chapter? = null,
    ): Deferred<List<Token>> = synchronized(cacheLock) {
        val loadKey = LoadKey(key, generation)
        cache[key]?.takeIf { generationOfLocked(key.bookId) == generation }?.let { CompletableDeferred(it) }
            ?: inFlight[loadKey]
            ?: loadScope.async(start = CoroutineStart.LAZY) {
                try {
                    val bookId = BookId(key.bookId)
                    val source = chapter ?: bookRepository.getChapter(bookId, key.chapterIndex)
                    val tokens = withContext(tokenizationDispatcher) {
                        TokenizerRegistry.resolve(key.languageTag).tokenize(source)
                    }
                    if (isCurrentGeneration(bookId, generation)) {
                        updateChapterWordCount(bookId, key.chapterIndex, tokens)
                        synchronized(cacheLock) {
                            if (generationOfLocked(key.bookId) == generation) cache[key] = tokens
                        }
                    }
                    tokens
                } finally {
                    synchronized(cacheLock) { inFlight.remove(loadKey) }
                }
            }.also { pending ->
                inFlight[loadKey] = pending
                pending.start()
            }
    }

    private suspend fun updateChapterWordCount(
        bookId: BookId,
        chapterIndex: Int,
        tokens: List<Token>,
    ) {
        val wordCount = countWords(tokens)
        bookRepository.updateChapterWordCount(bookId, chapterIndex, wordCount)
    }

    override fun invalidateBook(bookId: BookId) {
        synchronized(cacheLock) {
            bookGenerations[bookId.value] = nextGenerationLocked()
            cache.keys.removeAll { key -> key.bookId == bookId.value }
            languageTagCache.remove(bookId.value)
        }
    }

    override fun clearCache() {
        synchronized(cacheLock) {
            globalGeneration = nextGenerationLocked()
            bookGenerations.clear()
            cache.clear()
            languageTagCache.clear()
        }
    }

    private suspend fun resolveLanguageTag(
        bookId: BookId,
        generation: Long,
    ): String? {
        val cached =
            synchronized(cacheLock) {
                if (languageTagCache.containsKey(bookId.value)) {
                    Pair(true, languageTagCache[bookId.value])
                } else {
                    Pair(false, null)
                }
            }
        if (cached.first) return cached.second
        val resolved = bookRepository.getBookLanguageTag(bookId)
        synchronized(cacheLock) {
            if (generationOfLocked(bookId.value) == generation) {
                languageTagCache[bookId.value] = resolved
            }
        }
        return resolved
    }

    private fun currentGeneration(bookId: BookId): Long =
        synchronized(cacheLock) { generationOfLocked(bookId.value) }

    private fun isCurrentGeneration(
        bookId: BookId,
        generation: Long,
    ): Boolean = synchronized(cacheLock) { generationOfLocked(bookId.value) == generation }

    private fun generationOfLocked(bookId: String): Long = bookGenerations[bookId] ?: globalGeneration

    private fun nextGenerationLocked(): Long {
        generationCounter += 1L
        return generationCounter
    }

    companion object {
        private const val CACHE_INITIAL_CAPACITY = 16
        private const val CACHE_LOAD_FACTOR = 0.75f
        private const val MAX_CACHED_CHAPTERS = 10
    }
}
