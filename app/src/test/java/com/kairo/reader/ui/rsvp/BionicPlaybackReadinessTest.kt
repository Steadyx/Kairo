package com.kairo.reader.ui.rsvp

import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BionicPlaybackReadinessTest {
    @Test
    fun bionicWaitsForCompleteFramesBeforeRenderingOrPlaying() {
        val previewState = frameState(isLoading = true, isComplete = false)

        assertTrue(shouldShowLoading(previewState, ReadingPresentationMode.BIONIC))
        assertFalse(isFrameSetReadyForPlayback(previewState, ReadingPresentationMode.BIONIC))
    }

    @Test
    fun bionicStartsWhenTheCompleteFrameSetArrives() {
        val completeState = frameState(isLoading = false, isComplete = true)

        assertFalse(shouldShowLoading(completeState, ReadingPresentationMode.BIONIC))
        assertTrue(isFrameSetReadyForPlayback(completeState, ReadingPresentationMode.BIONIC))
    }

    @Test
    fun rsvpShowsInstantPreviewWithoutPlayingIt() {
        val previewState = frameState(isLoading = true, isComplete = false)

        assertFalse(shouldShowLoading(previewState, ReadingPresentationMode.RSVP))
        assertFalse(isFrameSetReadyForPlayback(previewState, ReadingPresentationMode.RSVP))
    }

    @Test
    fun playbackWaitsForSuccessfulNonEmptyFrames() {
        val ready = frameState(isLoading = false, isComplete = true)

        assertTrue(isFrameSetReadyForPlayback(ready, ReadingPresentationMode.RSVP))
        assertFalse(isFrameSetReadyForPlayback(ready.copy(isLoading = true), ReadingPresentationMode.RSVP))
        assertTrue(isFrameSetReadyForPlayback(ready.copy(isComplete = false), ReadingPresentationMode.RSVP))
        assertFalse(isFrameSetReadyForPlayback(ready.copy(isComplete = false), ReadingPresentationMode.BIONIC))
        assertFalse(isFrameSetReadyForPlayback(ready.copy(loadFailed = true), ReadingPresentationMode.RSVP))
        assertFalse(isFrameSetReadyForPlayback(ready.copy(frames = emptyList()), ReadingPresentationMode.RSVP))
    }

    private fun frameState(
        isLoading: Boolean,
        isComplete: Boolean,
    ): RsvpFrameLoadState {
        val token = Token("word", TokenType.WORD)
        return RsvpFrameLoadState(
            frames =
            listOf(
                RsvpFrame(
                    tokens = listOf(token),
                    durationMs = 100L,
                    originalTokenIndex = 0,
                    nextOriginalTokenIndex = 1,
                    displayOriginalStartIndex = 0,
                    displayOriginalEndExclusive = 1,
                ),
            ),
            baseTempoMs = 100L,
            isLoading = isLoading,
            isComplete = isComplete,
        )
    }
}
