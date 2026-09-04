package com.kairo.reader.core.export

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.SavedAnnotationItem
import com.kairo.reader.core.model.SavedAnnotationKind
import java.util.Locale

object NoteExportDocumentBuilder {
    fun build(
        scope: NoteExportScope,
        annotations: List<SavedAnnotationItem>,
        booksInLibraryOrder: List<Book>,
        generatedAt: Long,
    ): NoteExportDocument {
        val selected = selectNotes(scope, annotations)
        val booksById = booksInLibraryOrder.associateBy { it.id.value }
        val libraryOrder = booksInLibraryOrder.mapIndexed { index, book -> book.id.value to index }.toMap()
        val sourceGroups = selected.groupBy { it.annotation.bookId.value }
        val orderedGroups =
            sourceGroups.entries.sortedWith(
                compareBy<Map.Entry<String, List<SavedAnnotationItem>>> {
                    libraryOrder[it.key] ?: Int.MAX_VALUE
                }.thenBy { (_, items) ->
                    resolveBook(items, booksById).title.lowercase(Locale.ROOT)
                }.thenBy { it.key },
            )
        return NoteExportDocument(
            scope = scope,
            generatedAt = generatedAt,
            sources =
            orderedGroups.map { (_, items) ->
                val book = resolveBook(items, booksById)
                NoteExportSource(
                    title = book.title,
                    authors = book.authors,
                    entries = items.sortedWith(annotationReadingOrder).map { it.toExportEntry(book) },
                )
            },
        )
    }

    private fun selectNotes(
        scope: NoteExportScope,
        annotations: List<SavedAnnotationItem>,
    ): List<SavedAnnotationItem> =
        when (scope) {
            NoteExportScope.All ->
                annotations
                    .filter { it.annotation.kind == SavedAnnotationKind.NOTE }
                    .ifEmpty {
                        throw NoteExportResolutionException(NoteExportResolutionFailure.NO_NOTES)
                    }
            is NoteExportScope.Book ->
                annotations
                    .filter {
                        it.annotation.kind == SavedAnnotationKind.NOTE &&
                            it.annotation.bookId.value == scope.bookId
                    }.ifEmpty {
                        throw NoteExportResolutionException(NoteExportResolutionFailure.NO_NOTES)
                    }
            is NoteExportScope.Single -> {
                val item =
                    annotations.firstOrNull { it.annotation.id == scope.annotationId }
                        ?: throw NoteExportResolutionException(
                            NoteExportResolutionFailure.STALE_SINGLE,
                        )
                if (item.annotation.kind != SavedAnnotationKind.NOTE) {
                    throw NoteExportResolutionException(NoteExportResolutionFailure.WRONG_KIND)
                }
                listOf(item)
            }
        }

    private fun resolveBook(
        items: List<SavedAnnotationItem>,
        booksById: Map<String, Book>,
    ): Book {
        val first = items.first()
        return booksById[first.annotation.bookId.value] ?: first.book
    }

    private fun SavedAnnotationItem.toExportEntry(book: Book): NoteExportEntry {
        val annotation = annotation
        val chapter =
            book.chapters.firstOrNull { it.index == annotation.chapterIndex }
                ?: book.chapters.getOrNull(annotation.chapterIndex)
        return NoteExportEntry(
            chapterTitle = chapter?.title?.trim()?.takeIf(String::isNotEmpty),
            chapterNumber = annotation.chapterIndex + 1,
            note = annotation.note,
            passage = annotation.selectedText,
            highlightColor = annotation.color,
            createdAt = annotation.createdAt,
            updatedAt = annotation.updatedAt,
        )
    }

    private val annotationReadingOrder =
        compareBy<SavedAnnotationItem> { it.annotation.chapterIndex }
            .thenBy { minOf(it.annotation.startTokenIndex, it.annotation.endTokenIndex) }
            .thenBy { it.annotation.createdAt }
            .thenBy { it.annotation.id }
}
