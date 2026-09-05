package com.kairo.reader.data.rsvp

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.RsvpEngine
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.core.rsvp.engine.applyPlaybackEffects
import com.kairo.reader.core.rsvp.engine.frameTimingKey
import com.kairo.reader.core.rsvp.engine.normalizedForPlayback
import com.kairo.reader.core.rsvp.usesScoredSegmentation
import com.kairo.reader.data.token.TokenRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class RsvpFrameRepositoryImpl(
    private val tokenRepository: TokenRepository,
    private val engine: RsvpEngine,
    dispatcherProvider: DispatcherProvider,
) : RsvpFrameRepository {
    private enum class CacheMode {
        CHAPTER_BASE,
        EXACT_PLAYBACK,
    }

    private data class CacheKey(
        val bookId: String,
        val chapterIndex: Int,
        val timingConfig: RsvpConfig,
        val startIndex: Int,
        val mode: CacheMode,
        val options: RsvpGenerationOptions,
        val generation: Long,
    )

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

    private val cacheLock = Any()
    private val inFlight = mutableMapOf<CacheKey, Deferred<RsvpFrameSet>>()
    private val bookGenerations = mutableMapOf<String, Long>()
    private val engineDispatcher = dispatcherProvider.default.limitedParallelism(1)
    private val previewDispatcher = dispatcherProvider.default
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)

    override suspend fun getFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int,
    ): RsvpFrameSet =
        getFrames(
            bookId = bookId,
            chapterIndex = chapterIndex,
            config = config,
            startIndex = startIndex,
            options = RsvpGenerationOptions.LEGACY,
        )

    override suspend fun getFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int,
        options: RsvpGenerationOptions,
    ): RsvpFrameSet {
        val safeStartIndex = startIndex.coerceAtLeast(0)
        val chapterStartIndex = 0
        val baseConfig = config.normalizedForPlayback().withoutPlaybackEffects()
        val generation = currentGeneration(bookId)
        val baseKey =
            CacheKey(
                bookId.value,
                chapterIndex,
                baseConfig.frameTimingKey(),
                chapterStartIndex,
                CacheMode.CHAPTER_BASE,
                options,
                generation,
            )
        val baseFrameSet =
            ensureFramesAsync(
                key = baseKey,
                bookId = bookId,
                chapterIndex = chapterIndex,
                config = baseConfig,
                startIndex = chapterStartIndex,
                options = options,
            ).await()
        val playbackFrameSet = baseFrameSet.asPlaybackFrameSet(safeStartIndex, config)
        if (!playbackFrameSet.startsBefore(safeStartIndex)) return playbackFrameSet

        val exactKey =
            CacheKey(
                bookId.value,
                chapterIndex,
                config.frameTimingKey(),
                safeStartIndex,
                CacheMode.EXACT_PLAYBACK,
                options,
                generation,
            )
        return ensureFramesAsync(
            exactKey,
            bookId,
            chapterIndex,
            config,
            safeStartIndex,
            options,
        ).await()
    }

    override fun prefetchFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int,
    ) {
        prefetchFrames(
            bookId = bookId,
            chapterIndex = chapterIndex,
            config = config,
            startIndex = startIndex,
            options = RsvpGenerationOptions.LEGACY,
        )
    }

    override fun prefetchFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int,
        options: RsvpGenerationOptions,
    ) {
        val chapterStartIndex = 0
        val baseConfig = config.normalizedForPlayback().withoutPlaybackEffects()
        val generation = currentGeneration(bookId)
        val key =
            CacheKey(
                bookId.value,
                chapterIndex,
                baseConfig.frameTimingKey(),
                chapterStartIndex,
                CacheMode.CHAPTER_BASE,
                options,
                generation,
            )
        scope.launch {
            val cached = synchronized(cacheLock) { cache.containsKey(key) }
            if (cached) return@launch
            runCatching {
                ensureFramesAsync(
                    key,
                    bookId,
                    chapterIndex,
                    baseConfig,
                    chapterStartIndex,
                    options,
                )
            }
        }
    }

    override suspend fun getPreviewFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
        maxTokenCount: Int,
    ): RsvpFrameSet =
        getPreviewFrames(
            tokens = tokens,
            startIndex = startIndex,
            config = config,
            maxTokenCount = maxTokenCount,
            options = RsvpGenerationOptions.LEGACY,
        )

    override suspend fun getPreviewFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
        maxTokenCount: Int,
        options: RsvpGenerationOptions,
    ): RsvpFrameSet {
        if (tokens.isEmpty()) {
            return RsvpFrameSet(frames = emptyList(), baseTempoMs = config.tempoMsPerWord)
        }
        val safeStartIndex = startIndex.coerceIn(0, tokens.lastIndex)
        val visibleEndExclusive =
            (safeStartIndex + maxTokenCount.coerceAtLeast(1)).coerceAtMost(tokens.size)
        if (safeStartIndex >= visibleEndExclusive) {
            return RsvpFrameSet(frames = emptyList(), baseTempoMs = config.tempoMsPerWord)
        }
        val useScoredSegmentation = options.usesScoredSegmentation(config)
        val endExclusive =
            if (useScoredSegmentation) {
                previewLookaheadEndExclusive(
                    tokens = tokens,
                    visibleEndExclusive = visibleEndExclusive,
                    requiredWordCount = previewLookaheadWordCount(config),
                )
            } else {
                visibleEndExclusive
            }
        // Keep the source prefix available for bracket/quote state reconstruction.
        val previewTokens = tokens.subList(0, endExclusive)
        val frames =
            withContext(previewDispatcher) {
                engine.generateFrames(
                    tokens = previewTokens,
                    startIndex = safeStartIndex,
                    config = config,
                    options = options,
                )
            }.map { frame ->
                frame.asPreviewFrame(
                    tokenCount = tokens.size,
                    visibleEndExclusive =
                    visibleEndExclusive.takeIf { useScoredSegmentation },
                )
            }.let { previewFrames ->
                if (useScoredSegmentation) {
                    previewFrames.filter { frame ->
                        frame.displayOriginalEndExclusive <= visibleEndExclusive
                    }
                } else {
                    previewFrames
                }
            }
        return RsvpFrameSet(frames = frames, baseTempoMs = config.tempoMsPerWord)
    }

    private fun ensureFramesAsync(
        key: CacheKey,
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int,
        options: RsvpGenerationOptions,
    ): Deferred<RsvpFrameSet> =
        synchronized(cacheLock) {
            cache[key]?.let { cached -> CompletableDeferred(cached) }
                ?: inFlight[key]?.takeIf { it.isActive }
                ?: scope.async {
                    buildFrameSet(key, bookId, chapterIndex, config, startIndex, options)
                }.also { inFlight[key] = it }
        }

    private suspend fun buildFrameSet(
        key: CacheKey,
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int,
        options: RsvpGenerationOptions,
    ): RsvpFrameSet {
        return try {
            val tokens = tokenRepository.getTokens(bookId, chapterIndex)
            val frames =
                withContext(engineDispatcher) {
                    engine.generateFrames(
                        tokens = tokens,
                        startIndex = startIndex,
                        config = config,
                        options = options,
                    )
                }
            val frameSet = RsvpFrameSet(frames = frames, baseTempoMs = config.tempoMsPerWord)
            synchronized(cacheLock) {
                // Exact phrase starts are transient: retain only one chapter backing set.
                if (key.mode == CacheMode.CHAPTER_BASE &&
                    generationOfLocked(bookId.value) == key.generation
                ) {
                    cache[key] = frameSet
                }
            }
            frameSet
        } finally {
            synchronized(cacheLock) { inFlight.remove(key) }
        }
    }

    private fun RsvpFrameSet.asPlaybackFrameSet(
        startIndex: Int,
        config: RsvpConfig,
    ): RsvpFrameSet {
        if (frames.isEmpty()) {
            return RsvpFrameSet(frames = emptyList(), baseTempoMs = config.tempoMsPerWord)
        }

        val frameIndex =
            frameIndexMap.alignFrameIndex(
                tokenIndex = startIndex,
                frameCount = frames.size,
            )
        val playbackFrames = frames.subList(frameIndex, frames.size).toMutableList()
        applyPlaybackEffects(playbackFrames, config.normalizedForPlayback())
        return RsvpFrameSet(frames = playbackFrames, baseTempoMs = config.tempoMsPerWord)
    }

    private fun RsvpFrameSet.startsBefore(startIndex: Int): Boolean =
        startIndex > 0 && frames.firstOrNull()?.originalTokenIndex?.let { it < startIndex } == true

    private fun RsvpConfig.withoutPlaybackEffects(): RsvpConfig =
        copy(
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
            blinkMode = BlinkMode.OFF,
        )

    private fun RsvpFrame.asPreviewFrame(
        tokenCount: Int,
        visibleEndExclusive: Int? = null,
    ): RsvpFrame =
        copy(
            nextOriginalTokenIndex =
            nextOriginalTokenIndex
                .coerceIn(0, minOf(tokenCount, visibleEndExclusive ?: tokenCount)),
        )

    override fun clearCache() {
        val deferredToCancel =
            synchronized(cacheLock) {
                val affectedBookIds =
                    buildSet {
                        addAll(bookGenerations.keys)
                        addAll(cache.keys.map { key -> key.bookId })
                        addAll(inFlight.keys.map { key -> key.bookId })
                    }
                affectedBookIds.forEach { bookId ->
                    bookGenerations[bookId] = generationOfLocked(bookId) + 1L
                }
                cache.clear()
                inFlight.values.toList().also { inFlight.clear() }
            }
        deferredToCancel.forEach { deferred -> deferred.cancel() }
    }

    override fun invalidateBook(bookId: BookId) {
        val deferredToCancel =
            synchronized(cacheLock) {
                bookGenerations[bookId.value] = generationOfLocked(bookId.value) + 1L
                cache.keys.removeAll { key -> key.bookId == bookId.value }
                inFlight
                    .filterKeys { key -> key.bookId == bookId.value }
                    .values
                    .toList()
                    .also { inFlight.keys.removeAll { key -> key.bookId == bookId.value } }
            }
        deferredToCancel.forEach { deferred -> deferred.cancel() }
    }

    private fun currentGeneration(bookId: BookId): Long =
        synchronized(cacheLock) { generationOfLocked(bookId.value) }

    private fun generationOfLocked(bookId: String): Long = bookGenerations[bookId] ?: 0L

    private companion object {
        private const val CACHE_INITIAL_CAPACITY = 12
        private const val CACHE_LOAD_FACTOR = 0.75f
        private const val MAX_CACHED_FRAME_SETS = 8
    }
}

private fun previewLookaheadWordCount(config: RsvpConfig): Int =
    PREVIEW_MIN_LOOKAHEAD_WORDS +
        (
            config.rampDownFrames.coerceAtLeast(0) *
                config.maxWordsPerUnit.coerceIn(1, PREVIEW_MAX_SCORED_WORDS_PER_FRAME)
            )

private fun previewLookaheadEndExclusive(
    tokens: List<Token>,
    visibleEndExclusive: Int,
    requiredWordCount: Int,
): Int {
    var cursor = visibleEndExclusive.coerceIn(0, tokens.size)
    var wordCount = 0
    while (cursor < tokens.size && wordCount < requiredWordCount) {
        if (tokens[cursor].type == TokenType.WORD) wordCount++
        cursor++
    }
    while (
        cursor < tokens.size &&
        tokens[cursor].type == TokenType.PUNCTUATION
    ) {
        cursor++
    }
    return cursor
}

private const val PREVIEW_MIN_LOOKAHEAD_WORDS = 5
private const val PREVIEW_MAX_SCORED_WORDS_PER_FRAME = 3
