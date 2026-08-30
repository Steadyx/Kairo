package com.kairo.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedAnnotationEditTest {
    @Test
    fun noteEditPreservesPassageMetadataAndTrimsText() {
        val annotation = savedAnnotation(kind = SavedAnnotationKind.NOTE, note = "Original note")

        val edited =
            annotation.withEdit(
                request =
                EditSavedAnnotationRequest(
                    annotationId = annotation.id,
                    note = "  Updated note  ",
                    color = HighlightColor.PINK,
                ),
                updatedAt = EDITED_AT,
            )

        assertEquals(
            annotation.copy(
                note = "Updated note",
                color = HighlightColor.PINK,
                updatedAt = EDITED_AT,
            ),
            edited,
        )
    }

    @Test
    fun highlightEditChangesColourWithoutCreatingANote() {
        val annotation = savedAnnotation(kind = SavedAnnotationKind.HIGHLIGHT, note = "")

        val edited =
            annotation.withEdit(
                request =
                EditSavedAnnotationRequest(
                    annotationId = annotation.id,
                    note = "Ignored text",
                    color = HighlightColor.BLUE,
                ),
                updatedAt = EDITED_AT,
            )

        assertEquals(
            annotation.copy(color = HighlightColor.BLUE, updatedAt = EDITED_AT),
            edited,
        )
    }

    private fun savedAnnotation(
        kind: SavedAnnotationKind,
        note: String,
    ): SavedAnnotation =
        SavedAnnotation(
            id = "annotation-1",
            bookId = BookId("book-1"),
            chapterIndex = 2,
            startTokenIndex = 10,
            endTokenIndex = 14,
            selectedText = "A passage worth saving",
            note = note,
            color = HighlightColor.YELLOW,
            kind = kind,
            createdAt = CREATED_AT,
            updatedAt = CREATED_AT,
        )

    private companion object {
        const val CREATED_AT = 100L
        const val EDITED_AT = 200L
    }
}
