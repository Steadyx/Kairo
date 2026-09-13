package com.kairo.reader.core.rsvp.timing

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
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
        val total = frames.size - safeStart
        val rampUp = min(config.rampUpFrames.coerceAtLeast(0), total / 2)
        for (i in 0 until rampUp) {
            val index = safeStart + i
            frames[index] =
                frames[index].copy(
                    durationMs = (frames[index].durationMs * rampUpMultiplier(i, rampUp)).toLong(),
                )
        }

        frames[safeStart] =
            frames[safeStart].copy(
                durationMs = addNonNegativeDelay(frames[safeStart].durationMs, config.startDelayMs)
                    .coerceAtLeast(MIN_FRAME_MS),
            )

        val rampDown = min(config.rampDownFrames.coerceAtLeast(0), total / 2)
        val start = frames.size - rampDown
        for (i in start until frames.size) {
            frames[i] =
                frames[i].copy(
                    durationMs = (frames[i].durationMs * rampDownMultiplier(i - start, rampDown)).toLong(),
                )
        }

        frames[frames.lastIndex] =
            frames.last().copy(
                durationMs = addNonNegativeDelay(frames.last().durationMs, config.endDelayMs)
                    .coerceAtLeast(MIN_FRAME_MS),
            )
    }

    fun resumeRampMultiplier(
        config: RsvpConfig,
        frameIndex: Int,
        rampStartIndex: Int,
        preparationScale: Double = 1.0,
        initialRampStartIndex: Int = 0,
    ): Double {
        val rampFrames = config.rampUpFrames
        if (hasInitialRamp(config, rampStartIndex, initialRampStartIndex)) return 1.0
        val offset = frameIndex - rampStartIndex
        if (rampFrames <= 0 || offset < 0 || offset >= rampFrames) return 1.0
        return 1.0 + (rampUpMultiplier(offset, rampFrames) - 1.0) * preparationScale.coerceIn(0.0, 1.0)
    }

    fun resumeDelayMs(
        config: RsvpConfig,
        frameIndex: Int,
        rampStartIndex: Int,
        preparationScale: Double = 1.0,
        initialRampStartIndex: Int = 0,
    ): Long {
        if (hasInitialRamp(config, rampStartIndex, initialRampStartIndex) ||
            frameIndex != rampStartIndex
        ) {
            return 0L
        }
        return (config.startDelayMs * preparationScale.coerceIn(0.0, 1.0)).toLong()
    }

    private fun hasInitialRamp(config: RsvpConfig, rampStartIndex: Int, initialRampStartIndex: Int): Boolean =
        rampStartIndex < 0 ||
            (rampStartIndex - initialRampStartIndex) in 0 until config.rampUpFrames.coerceAtLeast(1)

    fun resumePreparationScale(pausedMs: Long): Double =
        ((pausedMs - BRIEF_PAUSE_MS).coerceAtLeast(0L).toDouble() / REORIENTATION_WINDOW_MS)
            .coerceIn(MIN_RESUME_PREPARATION, 1.0)

    private fun rampUpMultiplier(
        offset: Int,
        rampFrames: Int,
    ): Double {
        val progress = offset.toDouble() / rampFrames.coerceAtLeast(1).toDouble()
        return RAMP_UP_INITIAL_MULTIPLIER - (RAMP_UP_REDUCTION * progress)
    }

    private fun rampDownMultiplier(
        offset: Int,
        rampFrames: Int,
    ): Double {
        val progress = offset.toDouble() / rampFrames.coerceAtLeast(1).toDouble()
        return 1.0 + (RAMP_DOWN_INCREASE * progress)
    }

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

    private const val RAMP_UP_INITIAL_MULTIPLIER = 1.35
    private const val RAMP_UP_REDUCTION = 0.35
    private const val RAMP_DOWN_INCREASE = 0.25
    private const val BRIEF_PAUSE_MS = 1_000L
    private const val REORIENTATION_WINDOW_MS = 9_000.0
    private const val MIN_RESUME_PREPARATION = 0.15
}
