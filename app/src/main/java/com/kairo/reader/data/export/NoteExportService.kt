package com.kairo.reader.data.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.kairo.reader.R
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.export.NoteExportBusyException
import com.kairo.reader.core.export.NoteExportDocument
import com.kairo.reader.core.export.NoteExportDocumentBuilder
import com.kairo.reader.core.export.NoteExportFileNames
import com.kairo.reader.core.export.NoteExportFormat
import com.kairo.reader.core.export.NoteExportMarkdownRenderer
import com.kairo.reader.core.export.NoteExportScope
import com.kairo.reader.data.annotations.SavedAnnotationRepository
import com.kairo.reader.data.books.BookRepository
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

data class PreparedNoteExport(
    val requestId: String,
    val scope: NoteExportScope,
    val format: NoteExportFormat,
    val suggestedFileName: String,
)

interface NoteExporter {
    suspend fun prepare(
        scope: NoteExportScope,
        format: NoteExportFormat,
    ): PreparedNoteExport

    suspend fun write(
        requestId: String,
        scope: NoteExportScope,
        format: NoteExportFormat,
        destination: Uri,
    )

    fun discard(requestId: String)
}

internal fun interface NoteExportDestinationWriter {
    suspend fun write(
        source: File,
        destination: Uri,
    )
}

internal class NoteExportService(
    context: Context,
    private val annotationRepository: SavedAnnotationRepository,
    private val bookRepository: BookRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val clock: () -> Long = System::currentTimeMillis,
    private val requestIdFactory: () -> String = { UUID.randomUUID().toString() },
    destinationWriter: NoteExportDestinationWriter? = null,
) : NoteExporter {
    private val appContext = context.applicationContext
    private val localization = AndroidNoteExportLocalization(appContext.resources)
    private val markdownRenderer = NoteExportMarkdownRenderer(localization)
    private val pdfRenderer = NoteExportPdfRenderer(appContext, localization)
    private val operationMutex = Mutex()
    private val destinationWriter =
        destinationWriter ?: NoteExportDestinationWriter { source, destination ->
            FileInputStream(source).use { input ->
                currentCoroutineContext().ensureActive()
                val output =
                    appContext.contentResolver.openOutputStream(destination, WRITE_MODE)
                        ?: error("The document destination could not be opened")
                output.use { target ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        target.write(buffer, 0, count)
                    }
                }
            }
        }
    private val preparedDocumentsLock = Any()
    private val preparedDocuments = mutableMapOf<String, PreparedDocument>()

    override suspend fun prepare(
        scope: NoteExportScope,
        format: NoteExportFormat,
    ): PreparedNoteExport =
        exclusiveOperation {
            val document = resolveDocument(scope)
            val prepared =
                PreparedNoteExport(
                    requestId = requestIdFactory(),
                    scope = scope,
                    format = format,
                    suggestedFileName = suggestedFileName(document, format),
                )
            synchronized(preparedDocumentsLock) {
                preparedDocuments.clear()
                preparedDocuments[prepared.requestId] = PreparedDocument(prepared, document)
            }
            prepared
        }

    override suspend fun write(
        requestId: String,
        scope: NoteExportScope,
        format: NoteExportFormat,
        destination: Uri,
    ) {
        require(destination.scheme == ContentResolver.SCHEME_CONTENT) {
            "Note exports require a content URI destination"
        }
        exclusiveOperation {
            val prepared = synchronized(preparedDocumentsLock) { preparedDocuments.remove(requestId) }
            val document =
                if (prepared == null) {
                    resolveDocument(scope)
                } else {
                    require(prepared.descriptor.scope == scope && prepared.descriptor.format == format) {
                        "Prepared note export does not match the requested export"
                    }
                    prepared.document
                }
            val temporaryFile =
                File.createTempFile(
                    TEMP_FILE_PREFIX,
                    ".${format.extension}",
                    appContext.cacheDir,
                )
            try {
                render(document, format, temporaryFile)
                currentCoroutineContext().ensureActive()
                destinationWriter.write(temporaryFile, destination)
            } finally {
                temporaryFile.delete()
            }
        }
    }

    override fun discard(requestId: String) {
        synchronized(preparedDocumentsLock) { preparedDocuments.remove(requestId) }
    }

    private suspend fun <T> exclusiveOperation(block: suspend () -> T): T =
        withContext(dispatcherProvider.io) {
            if (!operationMutex.tryLock()) throw NoteExportBusyException()
            try {
                block()
            } finally {
                operationMutex.unlock()
            }
        }

    private suspend fun resolveDocument(scope: NoteExportScope): NoteExportDocument {
        val annotations = annotationRepository.observeAnnotations().first()
        val books = bookRepository.observeBooks().first()
        return NoteExportDocumentBuilder.build(
            scope = scope,
            annotations = annotations,
            booksInLibraryOrder = books,
            generatedAt = clock(),
        )
    }

    private fun suggestedFileName(
        document: NoteExportDocument,
        format: NoteExportFormat,
    ): String {
        val sourceTitle = document.sources.singleOrNull()?.title
        val date = SimpleDateFormat(FILE_DATE_PATTERN, Locale.ROOT).format(Date(document.generatedAt))
        return NoteExportFileNames.suggestedFileName(
            scope = document.scope,
            format = format,
            date = date,
            sourceTitle = sourceTitle,
            allNotesTitle = appContext.getString(R.string.note_export_filename_all),
            singleNoteLabel = appContext.getString(R.string.note_export_filename_single),
        )
    }

    private suspend fun render(
        document: NoteExportDocument,
        format: NoteExportFormat,
        target: File,
    ) {
        when (format) {
            NoteExportFormat.PDF -> {
                val job = currentCoroutineContext()
                pdfRenderer.render(document, target) { job.ensureActive() }
            }
            NoteExportFormat.MARKDOWN ->
                FileOutputStream(target).bufferedWriter(Charsets.UTF_8).use { writer ->
                    currentCoroutineContext().ensureActive()
                    writer.write(markdownRenderer.render(document))
                    currentCoroutineContext().ensureActive()
                }
        }
    }

    private companion object {
        const val TEMP_FILE_PREFIX = "note-export-"
        const val FILE_DATE_PATTERN = "yyyy-MM-dd"
        const val WRITE_MODE = "wt"
        const val COPY_BUFFER_SIZE = 8 * 1024
    }

    private data class PreparedDocument(val descriptor: PreparedNoteExport, val document: NoteExportDocument,)
}
