package com.example.kairo.core.tokenization.cjk

import com.example.kairo.core.model.normalizeWhitespace
import com.example.kairo.core.tokenization.PageNumberHeuristics

internal object CjkTextNormalizer {
    fun normalize(text: String): String {
        if (text.isEmpty()) return text
        val normalized =
            text
                .split(FORM_FEED)
                .joinToString(FORM_FEED) { normalizeWhitespace(it) }
        return normalizeEpubSymbols(normalized)
    }

    fun normalizeInlineText(text: String): String =
        normalizeEpubSymbols(normalizeWhitespace(text))

    fun normalizePageBreakMarkers(input: String): String {
        var text = input
        text = text.replace(FORM_FEED, "\n\n$FORM_FEED_MARKER\n\n")
        return text
    }

    fun shouldStripPageNumbers(html: String): Boolean =
        PageNumberHeuristics.shouldStripStandalonePageNumbers(html)

    fun stripStandalonePageNumbers(text: String): String =
        text.lineSequence()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && isPageNumberText(trimmed)
            }
            .joinToString("\n")

    private fun normalizeEpubSymbols(input: String): String {
        var text = input

        text = text.replace(Regex("[\\u00A0\\u2007\\u202F\\u2009\\u200A\\u200B]"), " ")
        text = text.replace(Regex("\\.{3,}"), "\u2026")
        text = text.replace(Regex("(?<!-)--(?!-)"), "\u2014")
        text =
            text
                .replace("Â°", "°")
                .replace("Âº", "º")
        text =
            text.replace(Regex("(^|[^\\w])[-−–—‐‑‒﹣－]\\s*(\\d)")) { match ->
                "${match.groupValues[1]}-${match.groupValues[2]}"
            }
        text =
            text.replace(Regex("(\\d)\\s*[°º]\\s*([cCfFkK])")) { match ->
                "${match.groupValues[1]}°${match.groupValues[2]}"
            }
        text = text.replace(Regex("(\\d)\\s*([℃℉])"), "$1$2")
        text = text.replace(Regex("(\\d)\\s*%"), "$1%")

        return text
    }

    private fun isPageNumberText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.all { it.isDigit() }) return true
        val romanNumeralPattern = Regex("^[ivxlcdm]+$", RegexOption.IGNORE_CASE)
        return romanNumeralPattern.matches(trimmed)
    }

    private const val FORM_FEED = "\u000C"
    const val FORM_FEED_MARKER = "KAIRO_PAGE_BREAK"
}
