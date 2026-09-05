package com.kairo.reader.core.model

data class RsvpFrame(
    val tokens: List<Token>,
    val durationMs: Long,
    // Index into the original (non-expanded) token list for position tracking
    // Used when syncing RSVP position back to the reader view
    val originalTokenIndex: Int = 0,
    // Tagged source-character offset, paired with originalTokenIndex for stable chunk resume.
    val resumeCursor: Int = RsvpResumeCursor.fromCharacterOffset(0),
    // First original token index after this frame's consumed reading unit.
    val nextOriginalTokenIndex: Int = originalTokenIndex + 1,
    // Original-token range represented visually by this frame. The start can precede
    // originalTokenIndex when opening punctuation belongs to the displayed word, while the end can
    // differ from the next frame's cursor when a long source word is split into RSVP chunks.
    val displayOriginalStartIndex: Int = originalTokenIndex,
    val displayOriginalEndExclusive: Int = nextOriginalTokenIndex,
    // Character offsets within the first and last original tokens disambiguate repeated split
    // fragments such as "ha-ha-ha". A null end means the complete final token.
    val displayOriginalStartCharacterOffset: Int = 0,
    val displayOriginalEndCharacterOffset: Int? = null,
)
