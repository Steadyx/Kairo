package com.kairo.reader.data.books

/** Readability does not depend on spaces, language, or a minimum chapter length. */
internal fun hasReadableImportText(text: String): Boolean {
    val normalized = text.trim()
    if (normalized in UNREADABLE_IMPORT_PLACEHOLDERS) return false
    return normalized.codePoints().anyMatch(Character::isLetterOrDigit)
}

private val UNREADABLE_IMPORT_PLACEHOLDERS = setOf(
    "No readable content found.",
    "No readable content found in this EPUB.",
)
