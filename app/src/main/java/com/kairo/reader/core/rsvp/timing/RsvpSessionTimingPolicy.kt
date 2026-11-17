package com.kairo.reader.core.rsvp.timing

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.engine.MIN_FRAME_MS
import kotlin.math.min

internal object RsvpSessionTimingPolicy {
    fun applyInitialSessionRamps(
        frames: MutableList<RsvpFrame>,
        config: RsvpConfig,
        startFrameIndex: Int = 0,
    ) {
        if (frames.isEmpty()) return

        val safeStart = startFrameIndex.coerceIn(frames.indices)
        applyWordRamps(frames, config, safeStart)

        frames[safeStart] =
            frames[safeStart].copy(
                durationMs = addNonNegativeDelay(frames[safeStart].durationMs, config.startDelayMs)
                    .coerceAtLeast(MIN_FRAME_MS),
            )

        frames[frames.lastIndex] =
            frames.last().copy(
                durationMs = addNonNegativeDelay(frames.last().durationMs, config.endDelayMs)
                    .coerceAtLeast(MIN_FRAME_MS),
            )
    }

    private fun applyWordRamps(frames: MutableList<RsvpFrame>, config: RsvpConfig, start: Int) {
        if (config.rampUpFrames <= 0 && config.rampDownFrames <= 0) return
        val progress = sourceWordProgress(frames)
        val total = progress.last() - progress[start]
        val rampUp = min(config.rampUpFrames.coerceAtLeast(0).toDouble(), total / 2)
        val rampDown = min(config.rampDownFrames.coerceAtLeast(0).toDouble(), total / 2)
        for (index in start until frames.size) {
            val opening = descendingRampWeight(progress[index] - progress[start], progress[index + 1] - progress[start], rampUp)
            val closing = descendingRampWeight(
                progress.last() - progress[index + 1],
                progress.last() - progress[index],
                rampDown,
            )
            val multiplier = 1.0 + RAMP_UP_REDUCTION * opening + RAMP_DOWN_INCREASE * closing
            if (multiplier > 1.0) {
                frames[index] = frames[index].copy(durationMs = (frames[index].durationMs * multiplier).toLong())
            }
        }
    }

    fun resumeRampMultiplier(
        config: RsvpConfig,
        frameIndex: Int,
        rampStartIndex: Int,
        wordProgress: DoubleArray,
        preparationScale: Double = 1.0,
        initialRampStartIndex: Int = 0,
    ): Double {
        if (hasInitialRamp(config, rampStartIndex, initialRampStartIndex, wordProgress)) return 1.0
        // A rewind can reach the baked-in opening before its resume ramp has finished.
        if (rampStartIndex < initialRampStartIndex && frameIndex >= initialRampStartIndex) return 1.0
        val start = wordProgress.getOrNull(rampStartIndex) ?: return 1.0
        if (frameIndex !in 0 until wordProgress.lastIndex) return 1.0
        val weight = descendingRampWeight(
            wordProgress[frameIndex] - start,
            wordProgress[frameIndex + 1] - start,
            config.rampUpFrames.toDouble(),
        )
        return 1.0 + RAMP_UP_REDUCTION * weight * preparationScale.coerceIn(0.0, 1.0)
    }

    fun resumeDelayMs(
        config: RsvpConfig,
        frameIndex: Int,
        rampStartIndex: Int,
        wordProgress: DoubleArray,
        preparationScale: Double = 1.0,
        initialRampStartIndex: Int = 0,
    ): Long {
        if (hasInitialRamp(config, rampStartIndex, initialRampStartIndex, wordProgress) ||
            frameIndex != rampStartIndex
        ) {
            return 0L
        }
        return (config.startDelayMs * preparationScale.coerceIn(0.0, 1.0)).toLong()
    }

    private fun hasInitialRamp(
        config: RsvpConfig,
        rampStartIndex: Int,
        initialRampStartIndex: Int,
        progress: DoubleArray,
    ): Boolean {
        val start = progress.getOrNull(rampStartIndex) ?: return true
        val initial = progress.getOrNull(initialRampStartIndex) ?: return false
        val initialLength = min(config.rampUpFrames.coerceAtLeast(0).toDouble(), (progress.last() - initial) / 2)
        return rampStartIndex == initialRampStartIndex || (start >= initial && start < initial + initialLength)
    }

    /** Split parts share one source word; presentation gaps and structural pauses consume none. */
    fun sourceWordProgress(frames: List<RsvpFrame>): DoubleArray {
        val splitParts = mutableMapOf<Int, Int>()
        var lastSource = -1
        for (frame in frames) {
            val words = frame.readingWordCount()
            if (words == 0) continue
            if (frame.originalTokenIndex == lastSource) {
                splitParts[lastSource] = (splitParts[lastSource] ?: 1) + 1
            }
            lastSource = frame.originalTokenIndex + words - 1
        }

        val progress = DoubleArray(frames.size + 1)
        var wordsStarted = 0
        var part = 0
        lastSource = -1
        frames.forEachIndexed { index, frame ->
            progress[index + 1] = progress[index]
            repeat(frame.readingWordCount()) { offset ->
                val source = frame.originalTokenIndex + offset
                if (source != lastSource) {
                    wordsStarted++
                    part = 0
                    lastSource = source
                }
                part++
                progress[index + 1] = wordsStarted - 1 + part.toDouble() / (splitParts[source] ?: 1)
            }
        }
        return progress
    }

    // The unit builder groups only adjacent words and never puts two parts of one word in a frame.
    private fun RsvpFrame.readingWordCount(): Int =
        if (isWordSeparation) 0 else tokens.count { it.type == TokenType.WORD }

    /** Average the linear easing over the source span, independent of display grouping. */
    private fun descendingRampWeight(from: Double, to: Double, length: Double): Double {
        if (length <= 0.0 || to <= from || to <= 0.0 || from >= length) return 0.0
        val a = from.coerceIn(0.0, length)
        val b = to.coerceIn(0.0, length)
        return ((b - a) - (b * b - a * a) / (2.0 * length)) / (to - from)
    }

    fun resumePreparationScale(pausedMs: Long): Double =
        ((pausedMs - BRIEF_PAUSE_MS).coerceAtLeast(0L).toDouble() / REORIENTATION_WINDOW_MS)
            .coerceIn(MIN_RESUME_PREPARATION, 1.0)

    private fun addNonNegativeDelay(
        durationMs: Long,
        delayMs: Long,
    ): Long {
        val safeDelay = delayMs.coerceAtLeast(0L)
        return if (Long.MAX_VALUE - durationMs < safeDelay) {
            Long.MAX_VALUE
        } else {
            durationMs + safeDelay
        }
    }

    private const val RAMP_UP_REDUCTION = 0.35
    private const val RAMP_DOWN_INCREASE = 0.25
    private const val BRIEF_PAUSE_MS = 1_000L
    private const val REORIENTATION_WINDOW_MS = 9_000.0
    private const val MIN_RESUME_PREPARATION = 0.15
}
