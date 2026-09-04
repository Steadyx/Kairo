package com.kairo.reader.ui.export

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.export.NoteExportFormat
import com.kairo.reader.core.export.NoteExportScope
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.HighlightColor
import com.kairo.reader.core.model.SavedAnnotation
import com.kairo.reader.core.model.SavedAnnotationItem
import com.kairo.reader.core.model.SavedAnnotationKind
import com.kairo.reader.ui.theme.KairoTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteExportSheetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun singleEntryOffersSingleSourceAndLibraryScopesWithBothFormats() {
        val annotations =
            listOf(
                note("note-1", "book-1", "A Very Long Research Source Title"),
                note("note-2", "book-1", "A Very Long Research Source Title")
            )
        var selectedScope: NoteExportScope? = null
        var selectedFormat: NoteExportFormat? = null
        composeRule.setContent {
            KairoTheme {
                NoteExportSheet(
                    state =
                    NoteExportUiState(
                        sheetOrigin = NoteExportScope.Single("note-1"),
                        sheetScope = NoteExportScope.Single("note-1"),
                    ),
                    annotations = annotations,
                    onSelectScope = { selectedScope = it },
                    onSelectFormat = { selectedFormat = it },
                    onDismiss = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.note_export_scope_single)).assertIsSelected()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.note_export_scope_book)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.note_export_scope_all)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.note_export_format_pdf)).assertIsSelected()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.note_export_format_markdown)).performClick()

        composeRule.runOnIdle {
            assertEquals(NoteExportScope.Book("book-1"), selectedScope)
            assertEquals(NoteExportFormat.MARKDOWN, selectedFormat)
        }
        val bitmap = composeRule.onNodeWithTag(NOTE_EXPORT_SHEET_TAG).captureToImage().asAndroidBitmap()
        val output = File(composeRule.activity.getExternalFilesDir(null), SHEET_SCREENSHOT_FILE)
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    private fun note(
        annotationId: String,
        bookId: String,
        title: String,
    ): SavedAnnotationItem {
        val book =
            Book(
                id = BookId(bookId),
                title = title,
                authors = listOf("Dr Researcher"),
                chapters = listOf(Chapter(0, "Methods", "", "")),
            )
        return SavedAnnotationItem(
            annotation =
            SavedAnnotation(
                id = annotationId,
                bookId = book.id,
                chapterIndex = 0,
                startTokenIndex = 1,
                endTokenIndex = 4,
                selectedText = "A representative quoted passage",
                note = "A useful research note",
                color = HighlightColor.BLUE,
                kind = SavedAnnotationKind.NOTE,
                createdAt = 1L,
                updatedAt = 1L,
            ),
            book = book,
            chapterCount = 1,
        )
    }

    private companion object {
        const val SHEET_SCREENSHOT_FILE = "note-export-sheet.png"
    }
}
