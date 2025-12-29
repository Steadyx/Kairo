package com.example.kairo.core.tokenization.rtl

internal object RtlParagraphSplitter {
    fun split(text: String): List<String> =
        text
            .split(Regex("\\n\\s*\\n"))
            .mapNotNull { raw ->
                when {
                    raw.contains(RtlTextNormalizer.FORM_FEED_MARKER) ->
                        RtlTextNormalizer.FORM_FEED_MARKER
                    raw.contains(FORM_FEED) -> FORM_FEED
                    else -> raw.trim().takeIf { it.isNotEmpty() }
                }
            }

    fun isPageBreakParagraph(paragraph: String): Boolean {
        if (paragraph == RtlTextNormalizer.FORM_FEED_MARKER) return true
        if (paragraph == FORM_FEED) return true
        if (paragraph.isBlank()) return false
        return PAGE_BREAK_REGEX.matches(paragraph)
    }

    private val PAGE_BREAK_REGEX =
        Regex(
            """^\s*(?:(?:\*\s*){3,}|(?:-\s*){3,}|(?:_\s*){3,}|(?:~\s*){3,}|(?:\u2014\s*){2,}|(?:\u2013\s*){2,}|(?:\u2022\s*){3,}|(?:\u00B7\s*){3,})\s*$""",
        )

    private const val FORM_FEED = "\u000C"
}
