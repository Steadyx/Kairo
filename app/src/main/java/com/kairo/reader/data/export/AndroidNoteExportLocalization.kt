package com.kairo.reader.data.export

import android.content.res.Resources
import com.kairo.reader.R
import com.kairo.reader.core.export.NoteExportLocalization
import com.kairo.reader.core.export.NoteExportScope
import java.text.DateFormat
import java.util.Date

internal class AndroidNoteExportLocalization(private val resources: Resources,) : NoteExportLocalization {
    override val authorLabel: String
        get() = resources.getString(R.string.note_export_document_authors)
    override val unknownAuthor: String
        get() = resources.getString(R.string.note_export_document_unknown_author)
    override val noteLabel: String
        get() = resources.getString(R.string.note_export_document_note)
    override val passageLabel: String
        get() = resources.getString(R.string.note_export_document_passage)
    override val createdLabel: String
        get() = resources.getString(R.string.note_export_document_created)
    override val updatedLabel: String
        get() = resources.getString(R.string.note_export_document_updated)

    override fun documentTitle(scope: NoteExportScope): String =
        resources.getString(
            when (scope) {
                NoteExportScope.All -> R.string.note_export_document_title_all
                is NoteExportScope.Book -> R.string.note_export_document_title_book
                is NoteExportScope.Single -> R.string.note_export_document_title_single
            },
        )

    override fun formatDate(timestamp: Long): String =
        DateFormat
            .getDateInstance(DateFormat.MEDIUM, resources.configuration.locales[0])
            .format(Date(timestamp))

    override fun exportedOn(formattedDate: String): String =
        resources.getString(R.string.note_export_document_exported, formattedDate)

    override fun contentsSummary(
        noteCount: Int,
        sourceCount: Int,
    ): String {
        val notes =
            resources.getQuantityString(
                R.plurals.note_export_document_note_count,
                noteCount,
                noteCount,
            )
        val sources =
            resources.getQuantityString(
                R.plurals.note_export_document_source_count,
                sourceCount,
                sourceCount,
            )
        return resources.getString(R.string.note_export_document_contents, notes, sources)
    }

    override fun chapterFallback(chapterNumber: Int): String =
        resources.getString(R.string.note_export_document_chapter, chapterNumber)

    override fun continued(label: String): String =
        resources.getString(R.string.note_export_document_continued, label)

    override fun pageNumber(pageNumber: Int): String =
        resources.getString(R.string.note_export_document_page, pageNumber)
}
