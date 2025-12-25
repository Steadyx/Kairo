package com.example.kairo.ui.reader

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.exp

internal sealed interface InvertedScrollCommand {
    data class Drag(val dy: Float) : InvertedScrollCommand

    data class Fling(val velocityY: Float) : InvertedScrollCommand
}

internal enum class Axis { Horizontal, Vertical }

internal suspend fun performInvertedFling(
    listState: LazyListState,
    initialVelocityY: Float,
) {
    var velocity = initialVelocityY
    var lastFrameNanos = withFrameNanos { it }
    val stopVelocityPxPerSec = 40f
    val frictionPerSecond = 8f

    while (abs(velocity) > stopVelocityPxPerSec) {
        val frameNanos = withFrameNanos { it }
        val dtSec = ((frameNanos - lastFrameNanos).coerceAtLeast(0L)) / 1_000_000_000f
        lastFrameNanos = frameNanos

        val dy = velocity * dtSec
        val consumed = listState.scrollBy(dy)
        if (consumed == 0f) break

        velocity *= exp(-frictionPerSecond * dtSec)
    }
}
