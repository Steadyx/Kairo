package com.example.kairo.data.rsvp

import com.example.kairo.core.dispatchers.DispatcherProvider
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.rsvp.RsvpEngine
import com.example.kairo.data.token.TokenRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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
    private val inFlight = mutableMapOf<CacheKey, Deferred<RsvpFrameSet>>()
    private val engineDispatcher = dispatcherProvider.default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)

    override suspend fun getFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
    ): RsvpFrameSet {
        val key = CacheKey(bookId.value, chapterIndex, config.hashCode())
        val cached = mutex.withLock { cache[key] }
        if (cached != null) return cached

        return ensureFramesAsync(key, bookId, chapterIndex, config).await()
    }

    override fun prefetchFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
    ) {
        val key = CacheKey(bookId.value, chapterIndex, config.hashCode())
        scope.launch {
            val cached = mutex.withLock { cache.containsKey(key) }
            if (cached) return@launch
            runCatching {
                ensureFramesAsync(key, bookId, chapterIndex, config)
            }
        }
    }

    private suspend fun ensureFramesAsync(
        key: CacheKey,
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
    ): Deferred<RsvpFrameSet> =
        mutex.withLock {
            cache[key]?.let { cached -> CompletableDeferred(cached) }
                ?: inFlight[key]?.takeIf { it.isActive }
                ?: scope.async {
                    buildFrameSet(key, bookId, chapterIndex, config)
                }.also { inFlight[key] = it }
        }

    private suspend fun buildFrameSet(
        key: CacheKey,
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
    ): RsvpFrameSet {
        return try {
            val tokens = tokenRepository.getTokens(bookId, chapterIndex)
            val frames =
                withContext(engineDispatcher) {
                    engine.generateFrames(tokens, startIndex = 0, config = config)
                }
            val frameSet = RsvpFrameSet(frames = frames, baseTempoMs = config.tempoMsPerWord)
            mutex.withLock {
                cache[key] = frameSet
                inFlight.remove(key)
            }
            frameSet
        } catch (error: Throwable) {
            mutex.withLock { inFlight.remove(key) }
            throw error
        }
    }

    override fun clearCache() {
        // Best-effort clear; no need to block callers on a mutex.
        runCatching {
            mutex.tryLock().takeIf { it }?.let {
                try {
                    cache.clear()
                    inFlight.values.forEach { deferred -> deferred.cancel() }
                    inFlight.clear()
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
