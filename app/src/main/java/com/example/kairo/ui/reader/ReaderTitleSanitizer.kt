package com.example.kairo.ui.reader

internal fun sanitizeChapterTitleForDisplay(title: String?): String? {
    val trimmed = title?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return if (isLikelyFileLabel(trimmed)) null else trimmed
}

private fun isLikelyFileLabel(text: String): Boolean {
    val normalized = normalizeNoiseLabel(text)
    if (normalized.isBlank()) return false
    val compact = normalized.replace(Regex("[\\s_-]+"), "")
    val numberedMatch = FILE_LABEL_WITH_NUMBER_REGEX.matchEntire(compact)
    if (numberedMatch != null) {
        val zeros = numberedMatch.groupValues[2]
        val digits = numberedMatch.groupValues[3]
        if (zeros.isNotEmpty() || digits.length >= 3) return true
    }
    return GENERIC_FILE_LABEL_REGEX.matches(compact)
}

private fun normalizeNoiseLabel(text: String): String {
    val trimmed = text.trim().lowercase()
    if (trimmed.isBlank()) return ""
    return trimmed.substringBeforeLast('.', trimmed)
}

private val FILE_LABEL_WITH_NUMBER_REGEX =
    Regex("(?i)^(part|chapter|section|book)(0*)(\\d{1,6})$")
private val GENERIC_FILE_LABEL_REGEX =
    Regex("(?i)^[a-z]{2,}\\d{3,}$")
