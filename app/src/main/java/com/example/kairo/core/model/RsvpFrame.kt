package com.example.kairo.core.model

data class RsvpFrame(
    val tokens: List<Token>,
    val durationMs: Long,
    // Index into the original (non-expanded) token list for position tracking
    // Used when syncing RSVP position back to the reader view
    val originalTokenIndex: Int = 0,
)
