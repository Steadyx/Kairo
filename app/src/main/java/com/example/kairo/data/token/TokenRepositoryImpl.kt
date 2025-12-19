package com.example.kairo.data.token

import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.Token
import com.example.kairo.core.tokenization.Tokenizer
import com.example.kairo.data.books.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TokenRepositoryImpl(
    private val bookRepository: BookRepository
) : TokenRepository {

    // LRU cache with max 10 chapters to prevent unbounded memory growth
    private val cache = object : LinkedHashMap<String, List<Token>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Token>>?): Boolean {
            return size > MAX_CACHED_CHAPTERS
        }
    }
    private val mutex = Mutex()
    private val tokenizationDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun getTokens(bookId: BookId, chapterIndex: Int, chapter: Chapter?): List<Token> {
        val key = "${bookId.value}-$chapterIndex"
        val cached = mutex.withLock { cache[key] }
        if (cached != null) {
            prefetchNextChapter(bookId, chapterIndex + 1)
            return cached
        }

        val resolvedChapter = chapter
            ?: withContext(Dispatchers.IO) { bookRepository.getChapter(bookId, chapterIndex) }
        val tokens = withContext(tokenizationDispatcher) { Tokenizer().tokenize(resolvedChapter) }
        mutex.withLock { cache[key] = tokens }
        prefetchNextChapter(bookId, chapterIndex + 1)
        return tokens
    }

    private fun prefetchNextChapter(bookId: BookId, nextIndex: Int) {
        val key = "${bookId.value}-$nextIndex"
        prefetchScope.launch {
            val cached = mutex.withLock { cache.containsKey(key) }
            if (cached) return@launch

            try {
                val chapter = withContext(Dispatchers.IO) { bookRepository.getChapter(bookId, nextIndex) }
                val tokens = withContext(tokenizationDispatcher) { Tokenizer().tokenize(chapter) }
                mutex.withLock { cache[key] = tokens }
            } catch (e: Exception) {
                // Chapter doesn't exist, ignore
            }
        }
    }

    fun clearCache() {
        prefetchScope.launch {
            mutex.withLock {
                cache.clear()
            }
        }
    }

    companion object {
        private const val MAX_CACHED_CHAPTERS = 10
    }
}
