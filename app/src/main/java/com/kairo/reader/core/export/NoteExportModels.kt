package com.kairo.reader.core.export

import com.kairo.reader.core.model.HighlightColor

enum class NoteExportFormat(val extension: String,) {
    PDF("pdf"),
    MARKDOWN("md"),
}

sealed interface NoteExportScope {
    data object All : NoteExportScope

    data class Book(val bookId: String,) : NoteExportScope

    data class Single(val annotationId: String,) : NoteExportScope
}

data class NoteExportDocument(val scope: NoteExportScope, val generatedAt: Long, val sources: List<NoteExportSource>,) {
    val noteCount: Int
        get() = sources.sumOf { it.entries.size }
}

data class NoteExportSource(val title: String, val authors: List<String>, val entries: List<NoteExportEntry>,)

data class NoteExportEntry(
    val chapterTitle: String?,
    val chapterNumber: Int,
    val note: String,
    val passage: String,
    val highlightColor: HighlightColor,
    val createdAt: Long,
    val updatedAt: Long,
)

interface NoteExportLocalization {
    val authorLabel: String
    val unknownAuthor: String
    val noteLabel: String
    val passageLabel: String
    val createdLabel: String
    val updatedLabel: String

    fun documentTitle(scope: NoteExportScope): String

    fun formatDate(timestamp: Long): String

    fun exportedOn(formattedDate: String): String

    fun contentsSummary(
        noteCount: Int,
        sourceCount: Int,
    ): String

    fun chapterFallback(chapterNumber: Int): String

    fun continued(label: String): String

    fun pageNumber(pageNumber: Int): String
}

enum class NoteExportResolutionFailure {
    NO_NOTES,
    STALE_SINGLE,
    WRONG_KIND,
}

class NoteExportResolutionException(val failure: NoteExportResolutionFailure,) : IllegalStateException()

class NoteExportBusyException : IllegalStateException()
