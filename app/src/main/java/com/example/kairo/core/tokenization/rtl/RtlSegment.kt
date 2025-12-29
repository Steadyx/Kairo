package com.example.kairo.core.tokenization.rtl

internal enum class RtlSegmentType {
    WORD,
    PUNCTUATION,
}

internal data class RtlSegment(
    val text: String,
    val type: RtlSegmentType,
)
