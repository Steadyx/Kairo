package com.example.kairo.core.model

data class RsvpFrame(
    val tokens: List<Token>,
    val durationMs: Long,
    // Index into the original (non-expanded) token list for position tracking
    // Used when syncing RSVP position back to the reader view
    val originalTokenIndex: Int = 0,
    // Cursor into the expanded RSVP token stream for exact frame/chunk resume
    val resumeCursor: Int = originalTokenIndex,
)
