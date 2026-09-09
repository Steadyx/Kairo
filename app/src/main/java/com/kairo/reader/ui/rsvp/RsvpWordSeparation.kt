package com.kairo.reader.ui.rsvp

import androidx.compose.runtime.withFrameNanos
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.effectiveBlinkMode
import kotlin.math.roundToInt

internal fun wordSeparationAlpha(frame: RsvpFrame?, playing: Boolean, config: RsvpConfig): Float =
    when {
        !playing || frame?.isWordSeparation != true || config.effectiveBlinkMode() == BlinkMode.OFF -> 1f
        frame.isRepeatedWordSeparation -> 0f
        else -> SOFT_SEPARATION_ALPHA
    }

internal fun separationRefreshCount(durationMs: Long, refreshRate: Float): Int {
    val safeRate = refreshRate.takeIf { it.isFinite() && it in MIN_REFRESH_RATE..MAX_REFRESH_RATE } ?: DEFAULT_SEPARATION_REFRESH_RATE
    return (durationMs.coerceIn(1L, MAX_PULSE_MS) * safeRate / MILLIS_PER_SECOND).roundToInt().coerceAtLeast(1)
}

/** Latch the committed pulse, then keep it for whole display intervals. */
internal suspend fun awaitWordSeparationPulse(durationMs: Long, refreshRate: Float) {
    withFrameNanos { }
    repeat(separationRefreshCount(durationMs, refreshRate)) { withFrameNanos { } }
}

private const val SOFT_SEPARATION_ALPHA = 0.45f
internal const val DEFAULT_SEPARATION_REFRESH_RATE = 60f
private const val MIN_REFRESH_RATE = 24f
private const val MAX_REFRESH_RATE = 240f
private const val MILLIS_PER_SECOND = 1000f
private const val MAX_PULSE_MS = 100L
