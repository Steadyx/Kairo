package com.kairo.reader.data.sessions

import android.os.SystemClock

interface ReadingSessionClock {
    fun wallTimeMillis(): Long

    fun elapsedRealtimeMillis(): Long
}

class SystemReadingSessionClock : ReadingSessionClock {
    override fun wallTimeMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

data class ReadingSessionTimestamp(val wallTimeMillis: Long, val elapsedRealtimeMillis: Long,)

internal fun ReadingSessionClock.timestamp(): ReadingSessionTimestamp =
    ReadingSessionTimestamp(
        wallTimeMillis = wallTimeMillis(),
        elapsedRealtimeMillis = elapsedRealtimeMillis(),
    )
