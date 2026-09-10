package com.kairo.reader.ui.rsvp

import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.TokenType

/**
 * Resolves only what the RSVP surface draws. Playback continues to use the real frame and its
 * duration, position, and resume cursor.
 */
internal fun resolveRsvpDisplayFrame(
    frames: List<RsvpFrame>,
    frameIndex: Int,
    contextAssistMode: RsvpContextAssistMode,
    isPlaying: Boolean = true,
): RsvpFrame? {
    val currentFrame = frames.getOrNull(frameIndex) ?: return null
    val pausedContext = !isPlaying && contextAssistMode != RsvpContextAssistMode.OFF
    if (!currentFrame.isWordSeparation && !(pausedContext && currentFrame.rendersEmptyOrpFrame())) return currentFrame

    return frames.findReadableFrameBefore(frameIndex)
        ?: frames.findReadableFrameAfter(frameIndex)
        ?: currentFrame
}

private fun RsvpFrame.rendersEmptyOrpFrame(): Boolean =
    buildOrpTextContent(tokens).fullText.isBlank()

private fun List<RsvpFrame>.findReadableFrameBefore(frameIndex: Int): RsvpFrame? {
    for (index in (frameIndex - 1) downTo 0) {
        val frame = this[index]
        if (frame.tokens.any { it.type == TokenType.WORD }) return frame
    }
    return null
}

private fun List<RsvpFrame>.findReadableFrameAfter(frameIndex: Int): RsvpFrame? {
    for (index in (frameIndex + 1) until size) {
        val frame = this[index]
        if (frame.tokens.any { it.type == TokenType.WORD }) return frame
    }
    return null
}
