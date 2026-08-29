package com.kairo.reader.data.sessions

class ReadingSessionTracker(startedAt: Long, initiallyActive: Boolean,) {
    val startedAt: Long = startedAt
    private var activeSince: Long? = startedAt.takeIf { initiallyActive }
    private var accumulatedActiveMs: Long = 0L

    fun setActive(
        active: Boolean,
        now: Long,
    ) {
        if (active) {
            if (activeSince == null) activeSince = now
        } else {
            accumulatedActiveMs += activeSince?.let { (now - it).coerceAtLeast(0L) } ?: 0L
            activeSince = null
        }
    }

    fun activeDurationMs(now: Long): Long =
        accumulatedActiveMs + (activeSince?.let { (now - it).coerceAtLeast(0L) } ?: 0L)
}
