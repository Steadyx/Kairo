package com.example.kairo.data.rsvp

import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFrame

data class RsvpFrameSet(
    val frames: List<RsvpFrame>,
    val baseTempoMs: Long
)

interface RsvpFrameRepository {
    suspend fun getFrames(bookId: BookId, chapterIndex: Int, config: RsvpConfig): RsvpFrameSet
    fun clearCache()
}
