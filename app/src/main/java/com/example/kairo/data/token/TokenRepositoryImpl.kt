package com.example.kairo.data.token

import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Token
import com.example.kairo.core.tokenization.Tokenizer
import com.example.kairo.data.books.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TokenRepositoryImpl(
    private val bookRepository: BookRepository,
    private val tokenizer: Tokenizer
) : TokenRepository {

    // LRU cache with max 10 chapters to prevent unbounded memory growth
    private val cache = object : LinkedHashMap<String, List<Token>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Token>>?): Boolean {
            return size > MAX_CACHED_CHAPTERS
        }
    }
    private val mutex = Mutex()
    private val prefetchScope = CoroutineScope(Dispatchers.IO)

    override suspend fun getTokens(bookId: BookId, chapterIndex: Int): List<Token> {
        val key = "${bookId.value}-$chapterIndex"
        return mutex.withLock {
            cache[key] ?: run {
                val chapter = bookRepository.getChapter(bookId, chapterIndex)
                val tokens = tokenizer.tokenize(chapter)
                cache[key] = tokens
                tokens
            }
        }.also {
            // Prefetch next chapter in background for smoother reading
            prefetchNextChapter(bookId, chapterIndex + 1)
        }
    }

    private fun prefetchNextChapter(bookId: BookId, nextIndex: Int) {
        val key = "${bookId.value}-$nextIndex"
        prefetchScope.launch {
            mutex.withLock {
                // Only prefetch if not already cached
                if (!cache.containsKey(key)) {
                    try {
                        val chapter = bookRepository.getChapter(bookId, nextIndex)
                        val tokens = tokenizer.tokenize(chapter)
                        cache[key] = tokens
                    } catch (e: Exception) {
                        // Chapter doesn't exist, ignore
                    }
                }
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
