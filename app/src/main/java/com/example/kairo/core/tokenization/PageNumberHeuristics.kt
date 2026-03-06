package com.example.kairo.core.tokenization

internal object PageNumberHeuristics {
    fun shouldStripStandalonePageNumbers(html: String): Boolean {
        if (html.isBlank()) return false
        return PAGE_BREAK_ATTRIBUTE_REGEX.containsMatchIn(html) ||
            PAGE_NUMBER_MARKUP_REGEX.containsMatchIn(html) ||
            PAGE_NUMBER_ARIA_REGEX.containsMatchIn(html)
    }

    private val PAGE_BREAK_ATTRIBUTE_REGEX =
        Regex(
            """\b(?:epub:type|role)\s*=\s*['"][^'"]*\b(?:pagebreak|doc-pagebreak)\b[^'"]*['"]""",
            RegexOption.IGNORE_CASE,
        )

    private val PAGE_NUMBER_MARKUP_REGEX =
        Regex(
            """\b(?:class|id)\s*=\s*['"][^'"]*\b(?:pagenum|page(?:[\s_-]?(?:num|number|no))|pgnum|folio|pagebreak)\b[^'"]*['"]""",
            RegexOption.IGNORE_CASE,
        )

    private val PAGE_NUMBER_ARIA_REGEX =
        Regex(
            """\baria-label\s*=\s*['"][^'"]*\bpage\b[^'"]*['"]""",
            RegexOption.IGNORE_CASE,
        )
}
