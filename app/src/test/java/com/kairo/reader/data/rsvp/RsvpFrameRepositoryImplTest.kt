package com.kairo.reader.data.rsvp

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.RsvpResumeCursor
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.ComprehensionRsvpEngine
import com.kairo.reader.core.rsvp.RsvpEngine
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy
import com.kairo.reader.core.rsvp.RsvpSegmentationStrategy
import com.kairo.reader.data.token.TokenRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RsvpFrameRepositoryImplTest {
    @Test
    fun invalidateBookSynchronouslyClearsOnlyThatBooksFrames() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CountingEngine()
        val repository =
            RsvpFrameRepositoryImpl(
                tokenRepository = CountingTokenRepository(),
                engine = engine,
                dispatcherProvider =
                object : DispatcherProvider {
                    override val default: CoroutineDispatcher = dispatcher
                    override val io: CoroutineDispatcher = dispatcher
                },
            )
        val bookId = BookId("book")
        val otherBookId = BookId("other")
        val config = RsvpConfig()

        val firstRequest = backgroundScope.launch { repository.getFrames(bookId, 0, config) }
        val otherRequest = backgroundScope.launch { repository.getFrames(otherBookId, 0, config) }
        advanceUntilIdle()
        firstRequest.join()
        otherRequest.join()
        assertEquals(2, repository.cacheSize())

        repository.invalidateBook(bookId)
        assertEquals(1, repository.cacheSize())

        val refreshed = backgroundScope.launch { repository.getFrames(bookId, 0, config) }
        advanceUntilIdle()
        refreshed.join()

        assertEquals(3, engine.startIndexes.size)
    }

    @Test
    fun invalidationPreventsInFlightFramesFromPopulatingCache() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val tokens = BlockingTokenRepository()
        val repository =
            RsvpFrameRepositoryImpl(
                tokenRepository = tokens,
                engine = CountingEngine(),
                dispatcherProvider =
                object : DispatcherProvider {
                    override val default: CoroutineDispatcher = dispatcher
                    override val io: CoroutineDispatcher = dispatcher
                },
            )
        val bookId = BookId("book")

        val stale = backgroundScope.async { repository.getFrames(bookId, 0, RsvpConfig()) }
        advanceUntilIdle()
        tokens.started.await()

        repository.invalidateBook(bookId)
        tokens.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, repository.cacheSize())
        stale.cancel()
    }

    @Test
    fun getFramesReusesChapterCacheForNearbyStartIndexes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CountingEngine()
        val repository =
            RsvpFrameRepositoryImpl(
                tokenRepository = CountingTokenRepository(),
                engine = engine,
                dispatcherProvider =
                object : DispatcherProvider {
                    override val default: CoroutineDispatcher = dispatcher
                    override val io: CoroutineDispatcher = dispatcher
                },
            )
        val bookId = BookId("book")
        val config = RsvpConfig()
        var firstFrameSet: RsvpFrameSet? = null
        var secondFrameSet: RsvpFrameSet? = null

        val firstRequest = backgroundScope.launch {
            firstFrameSet = repository.getFrames(bookId, 0, config, startIndex = 4)
        }
        advanceUntilIdle()
        firstRequest.join()

        val secondRequest = backgroundScope.launch {
            secondFrameSet = repository.getFrames(bookId, 0, config, startIndex = 7)
        }
        advanceUntilIdle()
        secondRequest.join()

        assertEquals(listOf(0), engine.startIndexes)
        assertEquals(4, requireNotNull(firstFrameSet).frames.first().originalTokenIndex)
        assertEquals(7, requireNotNull(secondFrameSet).frames.first().originalTokenIndex)
    }

    @Test
    fun getFramesReusesCacheForVisualOnlyConfigChanges() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CountingEngine()
        val repository =
            RsvpFrameRepositoryImpl(
                tokenRepository = CountingTokenRepository(),
                engine = engine,
                dispatcherProvider =
                object : DispatcherProvider {
                    override val default: CoroutineDispatcher = dispatcher
                    override val io: CoroutineDispatcher = dispatcher
                },
            )
        val bookId = BookId("book")
        val config = RsvpConfig()

        val firstRequest = backgroundScope.launch {
            repository.getFrames(bookId, 0, config, startIndex = 4)
        }
        advanceUntilIdle()
        firstRequest.join()

        val secondRequest = backgroundScope.launch {
            repository.getFrames(
                bookId,
                0,
                config.copy(
                    orpHighlightEnabled = !config.orpHighlightEnabled,
                    orpGuideEnabled = !config.orpGuideEnabled,
                    orpGuideBrightness = config.orpGuideBrightness + 0.25,
                    orpGuideThickness = config.orpGuideThickness + 0.25,
                ),
                startIndex = 4,
            )
        }
        advanceUntilIdle()
        secondRequest.join()

        assertEquals(listOf(0), engine.startIndexes)
    }

    @Test
    fun getFramesReusesBaseCacheForSessionRampOnlyConfigChanges() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CountingEngine()
        val repository =
            RsvpFrameRepositoryImpl(
                tokenRepository = CountingTokenRepository(),
                engine = engine,
                dispatcherProvider =
                object : DispatcherProvider {
                    override val default: CoroutineDispatcher = dispatcher
                    override val io: CoroutineDispatcher = dispatcher
                },
            )
        val bookId = BookId("book")
        val config =
            RsvpConfig(
                startDelayMs = 10L,
                endDelayMs = 0L,
                rampUpFrames = 0,
                rampDownFrames = 0,
            )
        var firstFrameSet: RsvpFrameSet? = null
        var secondFrameSet: RsvpFrameSet? = null

        val firstRequest = backgroundScope.launch {
            firstFrameSet = repository.getFrames(bookId, 0, config, startIndex = 4)
        }
        advanceUntilIdle()
        firstRequest.join()

        val secondRequest = backgroundScope.launch {
            secondFrameSet =
                repository.getFrames(
                    bookId,
                    0,
                    config.copy(startDelayMs = 50L),
                    startIndex = 4,
                )
        }
        advanceUntilIdle()
        secondRequest.join()

        assertEquals(listOf(0), engine.startIndexes)
        assertEquals(110L, requireNotNull(firstFrameSet).frames.first().durationMs)
        assertEquals(150L, requireNotNull(secondFrameSet).frames.first().durationMs)
    }

    @Test
    fun getFramesFallsBackToExactGenerationWhenPhraseStartsBeforeRequestedToken() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = PhraseLikeEngine()
        val repository =
            RsvpFrameRepositoryImpl(
                tokenRepository = CountingTokenRepository(),
                engine = engine,
                dispatcherProvider =
                object : DispatcherProvider {
                    override val default: CoroutineDispatcher = dispatcher
                    override val io: CoroutineDispatcher = dispatcher
                },
            )
        val bookId = BookId("book")
        val scored =
            RsvpGenerationOptions(
                languagePolicy = RsvpLanguagePolicy.ENGLISH,
                segmentationStrategy = RsvpSegmentationStrategy.SCORED_DP_V2,
            )
        var frameSet: RsvpFrameSet? = null

        val request = backgroundScope.launch {
            frameSet =
                repository.getFrames(
                    bookId,
                    0,
                    RsvpConfig(),
                    startIndex = 1,
                    options = scored,
                )
        }
        advanceUntilIdle()
        request.join()

        assertEquals(listOf(0, 1), engine.startIndexes)
        assertEquals(listOf(scored, scored), engine.generationOptions)
        assertEquals(1, requireNotNull(frameSet).frames.first().originalTokenIndex)
    }

    @Test
    fun getPreviewFramesKeepsOriginalCoordinatesAndSourceCursors() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CountingEngine()
        val repository =
            RsvpFrameRepositoryImpl(
                tokenRepository = CountingTokenRepository(),
                engine = engine,
                dispatcherProvider =
                object : DispatcherProvider {
                    override val default: CoroutineDispatcher = dispatcher
                    override val io: CoroutineDispatcher = dispatcher
                },
            )
        val tokens = (0 until 10).map { index -> Token(text = "w$index", type = TokenType.WORD) }
        var preview: RsvpFrameSet? = null

        val request = backgroundScope.launch {
            preview = repository.getPreviewFrames(
                tokens = tokens,
                startIndex = 4,
                config = RsvpConfig(),
                maxTokenCount = 3,
            )
        }
        advanceUntilIdle()
        request.join()

        val frames = requireNotNull(preview).frames
        assertEquals(listOf(4), engine.startIndexes)
        assertEquals(listOf(7), engine.tokenCounts)
        assertEquals(3, frames.size)
        assertEquals(4, frames.first().originalTokenIndex)
        assertEquals(7, frames.last().nextOriginalTokenIndex)
        assertTrue(frames.all { RsvpResumeCursor.characterOffset(it.resumeCursor) == 0 })
        assertEquals(listOf("w4", "w5", "w6"), frames.flatMap { it.tokens }.map(Token::text))
    }

    @Test
    fun generationOptionsAreForwardedAndParticipateInCacheIdentity() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CountingEngine()
        val repository = repository(dispatcher, engine)
        val bookId = BookId("options")
        val scored =
            RsvpGenerationOptions(
                languagePolicy = RsvpLanguagePolicy.ENGLISH,
                segmentationStrategy = RsvpSegmentationStrategy.SCORED_DP_V2,
            )

        val legacyRequest = backgroundScope.launch {
            repository.getFrames(bookId, 0, RsvpConfig(), options = RsvpGenerationOptions.LEGACY)
        }
        advanceUntilIdle()
        legacyRequest.join()
        val scoredRequest = backgroundScope.launch {
            repository.getFrames(bookId, 0, RsvpConfig(), options = scored)
        }
        advanceUntilIdle()
        scoredRequest.join()

        assertEquals(listOf(RsvpGenerationOptions.LEGACY, scored), engine.generationOptions)
        assertEquals(listOf(0, 0), engine.startIndexes)
        assertEquals(2, repository.cacheSize())
    }

    @Test
    fun previewForwardsOptionsUsesLookaheadAndNeverExposesTailText() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CountingEngine()
        val repository = repository(dispatcher, engine)
        val scored =
            RsvpGenerationOptions(
                languagePolicy = RsvpLanguagePolicy.ENGLISH,
                segmentationStrategy = RsvpSegmentationStrategy.SCORED_DP_V2,
            )
        val tokens = (0 until 12).map { index -> Token(text = "w$index", type = TokenType.WORD) }
        var preview: RsvpFrameSet? = null

        val request = backgroundScope.launch {
            preview =
                repository.getPreviewFrames(
                    tokens = tokens,
                    startIndex = 2,
                    config =
                    RsvpConfig(
                        enablePhraseChunking = true,
                        maxWordsPerUnit = 2,
                        rampDownFrames = 0,
                    ),
                    maxTokenCount = 2,
                    options = scored,
                )
        }
        advanceUntilIdle()
        request.join()

        assertEquals(listOf(scored), engine.generationOptions)
        assertEquals(listOf(9), engine.tokenCounts)
        assertEquals(
            listOf("w2", "w3"),
            requireNotNull(preview).frames.flatMap(RsvpFrame::tokens).map(Token::text),
        )
        assertTrue(requireNotNull(preview).frames.all { it.displayOriginalEndExclusive <= 4 })
    }

    @Test
    fun previewFallbacksUseOnlyThePriorVisibleSlice() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CountingEngine()
        val repository = repository(dispatcher, engine)
        val tokens = (0 until 12).map { index -> Token(text = "w$index", type = TokenType.WORD) }
        val eligibleConfig =
            RsvpConfig(
                enablePhraseChunking = true,
                maxWordsPerUnit = 2,
            )
        val scoredEnglish =
            RsvpGenerationOptions(
                languagePolicy = RsvpLanguagePolicy.ENGLISH,
                segmentationStrategy = RsvpSegmentationStrategy.SCORED_DP_V2,
            )
        val fallbackCases =
            listOf(
                RsvpGenerationOptions(
                    languagePolicy = RsvpLanguagePolicy.ENGLISH,
                    segmentationStrategy = RsvpSegmentationStrategy.LEGACY_GREEDY,
                ) to eligibleConfig,
                scoredEnglish.copy(languagePolicy = RsvpLanguagePolicy.UNKNOWN) to eligibleConfig,
                scoredEnglish to eligibleConfig.copy(maxWordsPerUnit = 4),
            )

        fallbackCases.forEach { (options, config) ->
            val request =
                backgroundScope.launch {
                    repository.getPreviewFrames(
                        tokens = tokens,
                        startIndex = 2,
                        config = config,
                        maxTokenCount = 2,
                        options = options,
                    )
                }
            advanceUntilIdle()
            request.join()
        }

        assertEquals(List(fallbackCases.size) { 4 }, engine.tokenCounts)
    }

    @Test
    fun legacyPreviewFramesAndDurationsMatchDirectVisibleSliceGeneration() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = ComprehensionRsvpEngine()
        val repository = repository(dispatcher, engine)
        val tokens =
            listOf("in", "the", "quiet", "library", "readers", "settled", "into", "chairs")
                .map { word -> Token(text = word, type = TokenType.WORD) }
        val config =
            RsvpConfig(
                enablePhraseChunking = true,
                maxWordsPerUnit = 2,
                startDelayMs = 80L,
                endDelayMs = 120L,
                rampUpFrames = 2,
                rampDownFrames = 3,
            )
        val visibleTokens = tokens.take(4)
        val expected =
            engine.generateFrames(
                tokens = visibleTokens,
                startIndex = 0,
                config = config,
            )
        var preview: RsvpFrameSet? = null

        val request =
            backgroundScope.launch {
                preview =
                    repository.getPreviewFrames(
                        tokens = tokens,
                        startIndex = 0,
                        config = config,
                        maxTokenCount = visibleTokens.size,
                    )
            }
        advanceUntilIdle()
        request.join()

        assertEquals(expected, requireNotNull(preview).frames)
    }

    @Test
    fun concurrentStrategiesDoNotShareInFlightGeneration() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CountingEngine()
        val repository = repository(dispatcher, engine)
        val bookId = BookId("parallel-options")
        val scored =
            RsvpGenerationOptions(
                languagePolicy = RsvpLanguagePolicy.ENGLISH,
                segmentationStrategy = RsvpSegmentationStrategy.SCORED_DP_V2,
            )

        val legacy = backgroundScope.async {
            repository.getFrames(bookId, 0, RsvpConfig(), options = RsvpGenerationOptions.LEGACY)
        }
        val scoredRequest = backgroundScope.async {
            repository.getFrames(bookId, 0, RsvpConfig(), options = scored)
        }
        advanceUntilIdle()

        legacy.await()
        scoredRequest.await()
        assertEquals(setOf(RsvpGenerationOptions.LEGACY, scored), engine.generationOptions.toSet())
        assertEquals(2, engine.startIndexes.size)
    }

    @Test
    fun prefetchForwardsGenerationOptions() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CountingEngine()
        val repository = repository(dispatcher, engine)
        val scored =
            RsvpGenerationOptions(
                languagePolicy = RsvpLanguagePolicy.ENGLISH,
                segmentationStrategy = RsvpSegmentationStrategy.SCORED_DP_V2,
            )

        repository.prefetchFrames(
            bookId = BookId("prefetch-options"),
            chapterIndex = 0,
            config = RsvpConfig(),
            options = scored,
        )
        advanceUntilIdle()

        assertEquals(listOf(scored), engine.generationOptions)
        assertEquals(listOf(0), engine.startIndexes)
    }

    private fun repository(
        dispatcher: CoroutineDispatcher,
        engine: RsvpEngine,
    ): RsvpFrameRepositoryImpl =
        RsvpFrameRepositoryImpl(
            tokenRepository = CountingTokenRepository(),
            engine = engine,
            dispatcherProvider =
            object : DispatcherProvider {
                override val default: CoroutineDispatcher = dispatcher
                override val io: CoroutineDispatcher = dispatcher
            },
        )

    private class CountingTokenRepository(private val tokenCount: Int = 20,) : TokenRepository {
        override suspend fun getTokens(
            bookId: BookId,
            chapterIndex: Int,
            chapter: com.kairo.reader.core.model.Chapter?,
        ): List<Token> =
            (0 until tokenCount).map { index ->
                Token(text = "w$index", type = TokenType.WORD)
            }
    }

    private class BlockingTokenRepository : TokenRepository {
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()

        override suspend fun getTokens(
            bookId: BookId,
            chapterIndex: Int,
            chapter: com.kairo.reader.core.model.Chapter?,
        ): List<Token> {
            started.complete(Unit)
            release.await()
            return listOf(Token(text = "word", type = TokenType.WORD))
        }
    }

    private class CountingEngine : RsvpEngine {
        val startIndexes = mutableListOf<Int>()
        val tokenCounts = mutableListOf<Int>()
        val generationOptions = mutableListOf<RsvpGenerationOptions>()

        override fun generateFrames(
            tokens: List<Token>,
            startIndex: Int,
            config: RsvpConfig,
        ): List<RsvpFrame> = buildFrames(tokens, startIndex)

        override fun generateFrames(
            tokens: List<Token>,
            startIndex: Int,
            config: RsvpConfig,
            options: RsvpGenerationOptions,
        ): List<RsvpFrame> {
            generationOptions += options
            return buildFrames(tokens, startIndex)
        }

        private fun buildFrames(
            tokens: List<Token>,
            startIndex: Int,
        ): List<RsvpFrame> {
            assertTrue(tokens.isNotEmpty())
            startIndexes += startIndex
            tokenCounts += tokens.size
            return (startIndex until tokens.size).map { index ->
                RsvpFrame(
                    tokens = listOf(tokens[index]),
                    durationMs = 100L,
                    originalTokenIndex = index,
                    nextOriginalTokenIndex = index + 1,
                )
            }
        }
    }

    private class PhraseLikeEngine : RsvpEngine {
        val startIndexes = mutableListOf<Int>()
        val generationOptions = mutableListOf<RsvpGenerationOptions>()

        override fun generateFrames(
            tokens: List<Token>,
            startIndex: Int,
            config: RsvpConfig,
        ): List<RsvpFrame> = buildFrames(tokens, startIndex)

        override fun generateFrames(
            tokens: List<Token>,
            startIndex: Int,
            config: RsvpConfig,
            options: RsvpGenerationOptions,
        ): List<RsvpFrame> {
            generationOptions += options
            return buildFrames(tokens, startIndex)
        }

        private fun buildFrames(
            tokens: List<Token>,
            startIndex: Int,
        ): List<RsvpFrame> {
            assertTrue(tokens.isNotEmpty())
            startIndexes += startIndex
            return if (startIndex == 0) {
                listOf(
                    RsvpFrame(
                        tokens = listOf(tokens[0], tokens[1]),
                        durationMs = 100L,
                        originalTokenIndex = 0,
                        nextOriginalTokenIndex = 2,
                    ),
                    RsvpFrame(
                        tokens = listOf(tokens[2]),
                        durationMs = 100L,
                        originalTokenIndex = 2,
                        nextOriginalTokenIndex = 3,
                    ),
                )
            } else {
                listOf(
                    RsvpFrame(
                        tokens = listOf(tokens[startIndex]),
                        durationMs = 100L,
                        originalTokenIndex = startIndex,
                        nextOriginalTokenIndex = startIndex + 1,
                    )
                )
            }
        }
    }

    private fun RsvpFrameRepositoryImpl.cacheSize(): Int {
        val field = javaClass.getDeclaredField("cache")
        field.isAccessible = true
        val cache = field.get(this) as Map<*, *>
        return cache.size
    }
}
