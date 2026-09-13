package com.kairo.reader.data.rsvp

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.rsvp.RsvpGenerationOptions

data class RsvpFrameSet(
    val frames: List<RsvpFrame>,
    val baseTempoMs: Long,
    val frameIndexMap: RsvpFrameIndexMap = RsvpFrameIndexMap.from(frames),
    val initialRampStartFrameIndex: Int = 0,
)

interface RsvpFrameRepository {
    /** Full chapter navigation; startIndex selects the initial playback position, not its bounds. */
    suspend fun getSeekableFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int = 0,
        options: RsvpGenerationOptions = RsvpGenerationOptions.DEFAULT,
    ): RsvpFrameSet = getFrames(bookId, chapterIndex, config, startIndex = 0, options = options)

    suspend fun getFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int = 0,
    ): RsvpFrameSet

    suspend fun getFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int = 0,
        options: RsvpGenerationOptions,
    ): RsvpFrameSet = getFrames(bookId, chapterIndex, config, startIndex)

    fun prefetchFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int = 0,
    ) {
        // Optional optimization; default no-op for implementations without prefetching.
    }

    fun prefetchFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int = 0,
        options: RsvpGenerationOptions,
    ) {
        prefetchFrames(bookId, chapterIndex, config, startIndex)
    }

    suspend fun getPreviewFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
        maxTokenCount: Int = DEFAULT_PREVIEW_TOKEN_COUNT,
    ): RsvpFrameSet =
        RsvpFrameSet(frames = emptyList(), baseTempoMs = config.tempoMsPerWord)

    suspend fun getPreviewFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
        maxTokenCount: Int = DEFAULT_PREVIEW_TOKEN_COUNT,
        options: RsvpGenerationOptions,
    ): RsvpFrameSet = getPreviewFrames(tokens, startIndex, config, maxTokenCount)

    fun clearCache()

    fun invalidateBook(bookId: BookId) {
        // Optional for stateless implementations.
    }

    companion object {
        const val DEFAULT_PREVIEW_TOKEN_COUNT = 320
    }
}
