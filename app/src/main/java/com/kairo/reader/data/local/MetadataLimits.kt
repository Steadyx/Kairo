package com.kairo.reader.data.local

import com.kairo.reader.core.model.Book

internal const val MAX_METADATA_TITLE_CHARS = 4096
internal const val MAX_METADATA_AUTHORS_CHARS = 16384
internal const val MAX_METADATA_LANGUAGE_CHARS = 128

internal fun String.boundedMetadata(limit: Int): String =
    if (length <= limit) this else substring(0, offsetByCodePoints(0, minOf(codePointCount(0, length), limit)))

internal fun List<String>.boundedAuthors(): List<String> {
    val converters = Converters()
    return converters.toAuthors(converters.fromAuthors(this).boundedMetadata(MAX_METADATA_AUTHORS_CHARS))
}

internal fun Book.withBoundedMetadata(): Book =
    copy(
        title = title.boundedMetadata(MAX_METADATA_TITLE_CHARS),
        authors = authors.boundedAuthors(),
        languageTag = languageTag?.boundedMetadata(MAX_METADATA_LANGUAGE_CHARS),
        chapters = chapters.map { it.copy(title = it.title?.boundedMetadata(MAX_METADATA_TITLE_CHARS)) },
        tableOfContents = tableOfContents.map { it.copy(label = it.label.boundedMetadata(MAX_METADATA_TITLE_CHARS)) },
    )
