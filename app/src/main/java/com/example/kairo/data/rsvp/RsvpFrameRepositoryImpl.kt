package com.example.kairo.data.rsvp

import com.example.kairo.core.dispatchers.DispatcherProvider
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.rsvp.RsvpEngine
import com.example.kairo.data.token.TokenRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class RsvpFrameRepositoryImpl(
    private val tokenRepository: TokenRepository,
    private val engine: RsvpEngine,
    dispatcherProvider: DispatcherProvider,
) : RsvpFrameRepository {
    private data class CacheKey(val bookId: String, val chapterIndex: Int, val configHash: Int,)

    private val cache =
        object : LinkedHashMap<CacheKey, RsvpFrameSet>(
            CACHE_INITIAL_CAPACITY,
            CACHE_LOAD_FACTOR,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<CacheKey, RsvpFrameSet>?
            ): Boolean =
                size > MAX_CACHED_FRAME_SETS
        }

    private val mutex = Mutex()
    private val engineDispatcher = dispatcherProvider.default.limitedParallelism(1)

    override suspend fun getFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
    ): RsvpFrameSet {
        val key = CacheKey(bookId.value, chapterIndex, config.hashCode())
        val cached = mutex.withLock { cache[key] }
        if (cached != null) return cached

        val tokens = tokenRepository.getTokens(bookId, chapterIndex)
        val frames =
            withContext(engineDispatcher) {
                engine.generateFrames(tokens, startIndex = 0, config = config)
            }
        val frameSet = RsvpFrameSet(frames = frames, baseTempoMs = config.tempoMsPerWord)
        mutex.withLock { cache[key] = frameSet }
        return frameSet
    }

    override fun clearCache() {
        // Best-effort clear; no need to block callers on a mutex.
        runCatching {
            mutex.tryLock().takeIf { it }?.let {
                try {
                    cache.clear()
                } finally {
                    mutex.unlock()
                }
            }
        }
    }

    private companion object {
        private const val CACHE_INITIAL_CAPACITY = 12
        private const val CACHE_LOAD_FACTOR = 0.75f
        private const val MAX_CACHED_FRAME_SETS = 6
    }
}
