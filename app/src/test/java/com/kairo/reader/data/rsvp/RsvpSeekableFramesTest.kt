package com.kairo.reader.data.rsvp

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.RsvpResumeCursor
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.ComprehensionRsvpEngine
import com.kairo.reader.core.rsvp.RsvpEngine
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.data.token.TokenRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RsvpSeekableFramesTest {
    @Test
    fun distantStartsAndReloadsKeepTheWholeChapterAndReuseGeneration() = runTest {
        val tokens = List(1600) { Token("reading", TokenType.WORD) }
        val engine = RecordingEngine()
        val repository = repository(StandardTestDispatcher(testScheduler), tokens, engine)
        val config = RsvpConfig(enablePhraseChunking = false, blinkMode = BlinkMode.OFF)
        for (start in listOf(4, 520, 1050, 520)) {
            val loaded = repository.getSeekableFrames(BookId("chapter"), 0, config, start)
            assertEquals(0, loaded.frames.first().originalTokenIndex)
            assertEquals(1599, loaded.frames.last().originalTokenIndex)
            val index = loaded.frameIndexMap.alignFrameIndex(start, frameCount = loaded.frames.size)
            assertEquals(start, loaded.frames[index].originalTokenIndex)
            assertEquals(index, loaded.initialRampStartFrameIndex)
        }
        assertEquals(listOf(0), engine.starts)
    }

    @Test
    fun startingInsideAGroupedUnitPreservesEveryWordAndOnlyRebuildsThatUnit() = runTest {
        val tokens = List(100) {
            "They waited beside the quiet station while the morning train arrived ."
                .split(' ').map { Token(it, if (it == ".") TokenType.PUNCTUATION else TokenType.WORD) }
        }.flatten()
        val engine = RecordingEngine()
        val repository = repository(StandardTestDispatcher(testScheduler), tokens, engine)
        val config = RsvpConfig(enablePhraseChunking = true, maxWordsPerUnit = 3, blinkMode = BlinkMode.OFF)
        val options = RsvpGenerationOptions.fromLanguageTag("en")
        val full = repository.getSeekableFrames(BookId("groups"), 0, config, options = options)
        val group = full.frames.first { it.tokens.count { token -> token.type == TokenType.WORD } > 1 }
        val start = (group.originalTokenIndex + 1 until group.displayOriginalEndExclusive)
            .first { tokens[it].type == TokenType.WORD }
        val loaded = repository.getSeekableFrames(BookId("groups"), 0, config, start, options)
        val words = loaded.frames.flatMap { it.tokens }.filter { it.type == TokenType.WORD }.map { it.text }
        assertEquals(tokens.filter { it.type == TokenType.WORD }.map { it.text }, words)
        val index = loaded.frameIndexMap.alignFrameIndex(start, frameCount = loaded.frames.size)
        assertEquals(start, loaded.frames[index].originalTokenIndex)
        assertEquals(3, engine.generatedFrameCounts.size)
        assertTrue(engine.generatedFrameCounts.drop(1).all { it <= 3 })
    }

    @Test
    fun splitWordCursorRestoresExactlyWhileEarlierWordsRemainReachable() = runTest {
        val tokens = listOf("before", "mother-in-law", "after").map { Token(it, TokenType.WORD) }
        val repository = repository(StandardTestDispatcher(testScheduler), tokens, RecordingEngine())
        val loaded = repository.getSeekableFrames(BookId("split"), 0, RsvpConfig(enablePhraseChunking = false), 1)
        val index = loaded.frameIndexMap.alignFrameIndex(1, RsvpResumeCursor.fromCharacterOffset(7), loaded.frames.size)
        assertEquals("in-", loaded.frames[index].tokens.single().text)
        assertEquals("before", loaded.frames.first().tokens.single().text)
    }

    private fun repository(dispatcher: CoroutineDispatcher, tokens: List<Token>, engine: RsvpEngine): RsvpFrameRepositoryImpl =
        RsvpFrameRepositoryImpl(
            tokenRepository = object : TokenRepository {
                override suspend fun getTokens(bookId: BookId, chapterIndex: Int, chapter: Chapter?): List<Token> = tokens
            },
            engine = engine,
            dispatcherProvider = object : DispatcherProvider {
                override val default = dispatcher
                override val io = dispatcher
            },
        )

    private class RecordingEngine : RsvpEngine {
        val starts = mutableListOf<Int>()
        val generatedFrameCounts = mutableListOf<Int>()
        private val delegate = ComprehensionRsvpEngine()
        override fun generateFrames(tokens: List<Token>, startIndex: Int, config: RsvpConfig): List<RsvpFrame> =
            generateFrames(tokens, startIndex, config, RsvpGenerationOptions.DEFAULT)
        override fun generateFrames(
            tokens: List<Token>,
            startIndex: Int,
            config: RsvpConfig,
            options: RsvpGenerationOptions
        ): List<RsvpFrame> {
            starts += startIndex
            return delegate.generateFrames(tokens, startIndex, config, options).also { generatedFrameCounts += it.size }
        }
    }
}
