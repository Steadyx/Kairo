package com.kairo.reader.data.rsvp

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.nearestWordIndex
import com.kairo.reader.core.rsvp.RsvpEngine
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.core.rsvp.engine.applyPlaybackEffects
import com.kairo.reader.core.rsvp.engine.normalizedForPlayback

/** Keep the cached chapter intact except for a reading unit bisected by the requested start. */
internal fun buildSeekableFrameSet(
    tokens: List<Token>,
    base: RsvpFrameSet,
    config: RsvpConfig,
    startIndex: Int,
    engine: RsvpEngine,
    options: RsvpGenerationOptions,
): RsvpFrameSet {
    if (base.frames.isEmpty() || tokens.isEmpty()) return base
    val start = tokens.nearestWordIndex(startIndex.coerceIn(tokens.indices))
    val frames = base.frames.toMutableList()
    val index = base.frameIndexMap.alignFrameIndex(start, frameCount = frames.size)
    val boundary = frames[index]
    val normalized = config.normalizedForPlayback()
    if (boundary.originalTokenIndex < start && boundary.displayOriginalEndExclusive > start) {
        // Only rebuild the two sides of this one unit. Re-generating the whole remaining chapter
        // would make rotation/settings changes expensive when starting inside a grouped phrase.
        val splitConfig = normalized.withoutPlaybackEffects()
        val before = engine.generateFrames(
            tokens.subList(0, start),
            boundary.originalTokenIndex,
            splitConfig,
            options,
        )
        val after = engine.generateFrames(
            tokens.subList(0, boundary.nextOriginalTokenIndex.coerceAtMost(tokens.size)),
            start,
            splitConfig,
            options,
        )
        frames.removeAt(index)
        frames.addAll(index, before + after)
    }
    val startFrameIndex = frames.indexOfFirst { it.originalTokenIndex >= start }.coerceAtLeast(0)
    applyPlaybackEffects(frames, normalized, startFrameIndex = startFrameIndex)
    val indexMap = RsvpFrameIndexMap.from(frames)
    return RsvpFrameSet(
        frames = frames,
        baseTempoMs = normalized.tempoMsPerWord,
        frameIndexMap = indexMap,
        initialRampStartFrameIndex = indexMap.alignFrameIndex(start, frameCount = frames.size),
    )
}
