package com.kairo.reader.data.export

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.reader.core.dispatchers.DefaultDispatcherProvider
import com.kairo.reader.core.export.NoteExportFormat
import com.kairo.reader.core.export.NoteExportScope
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.HighlightColor
import com.kairo.reader.core.model.SavedAnnotation
import com.kairo.reader.core.model.SavedAnnotationItem
import com.kairo.reader.core.model.SavedAnnotationKind
import com.kairo.reader.data.annotations.SavedAnnotationRepository
import com.kairo.reader.data.books.BookImportResult
import com.kairo.reader.data.books.BookRepository
import com.kairo.reader.data.books.TextImportRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteExportServiceTest {
    @Test
    fun preparedDocumentIsStableAndMissingInMemorySnapshotRecoversFromRepositories() = runBlocking {
        val book = book()
        val annotations = MutableStateFlow(listOf(note(book, "Original observation")))
        val books = MutableStateFlow(listOf(book))
        val firstOutput = mutableListOf<String>()
        val firstService = service(annotations, books) { source, _ -> firstOutput += source.readText() }
        val prepared = firstService.prepare(NoteExportScope.Single("note-1"), NoteExportFormat.MARKDOWN)
        annotations.value = listOf(note(book, "Changed after picker opened"))

        firstService.write(
            prepared.requestId,
            prepared.scope,
            prepared.format,
            Uri.parse("content://note-export-test/first"),
        )

        assertTrue(firstOutput.single().contains("Original observation"))
        assertFalse(firstOutput.single().contains("Changed after picker opened"))

        val recoveredOutput = mutableListOf<String>()
        val recreatedService = service(annotations, books) { source, _ -> recoveredOutput += source.readText() }
        recreatedService.write(
            requestId = prepared.requestId,
            scope = prepared.scope,
            format = prepared.format,
            destination = Uri.parse("content://note-export-test/recovered"),
        )
        assertTrue(recoveredOutput.single().contains("Changed after picker opened"))
    }

    @Test
    fun invalidDestinationAndPreparedDescriptorMismatchNeverReachWriter() = runBlocking {
        val book = book()
        val annotations = MutableStateFlow(listOf(note(book, "Scoped note")))
        var writeCalls = 0
        val service =
            service(annotations, MutableStateFlow(listOf(book))) { _, _ -> writeCalls++ }
        val prepared = service.prepare(NoteExportScope.Single("note-1"), NoteExportFormat.MARKDOWN)

        assertFails<IllegalArgumentException> {
            service.write(
                prepared.requestId,
                prepared.scope,
                prepared.format,
                Uri.fromFile(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir),
            )
        }
        assertFails<IllegalArgumentException> {
            service.write(
                prepared.requestId,
                NoteExportScope.All,
                prepared.format,
                Uri.parse("content://note-export-test/mismatch"),
            )
        }
        assertEquals(0, writeCalls)
    }

    private fun service(
        annotations: Flow<List<SavedAnnotationItem>>,
        books: Flow<List<Book>>,
        writer: suspend (java.io.File, Uri) -> Unit,
    ) =
        NoteExportService(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            annotationRepository = FakeAnnotationRepository(annotations),
            bookRepository = FakeBookRepository(books),
            dispatcherProvider = DefaultDispatcherProvider(),
            clock = { 1_788_451_200_000L },
            requestIdFactory = { "prepared-request" },
            destinationWriter = NoteExportDestinationWriter(writer),
        )

    private fun book() =
        Book(
            id = BookId("book-1"),
            title = "Research Source",
            authors = listOf("Researcher"),
            chapters = listOf(Chapter(0, "Findings", "", "")),
        )

    private fun note(
        book: Book,
        text: String,
    ) =
        SavedAnnotationItem(
            annotation =
            SavedAnnotation(
                id = "note-1",
                bookId = book.id,
                chapterIndex = 0,
                startTokenIndex = 1,
                endTokenIndex = 2,
                selectedText = "Quoted evidence",
                note = text,
                color = HighlightColor.GREEN,
                kind = SavedAnnotationKind.NOTE,
                createdAt = 1L,
                updatedAt = 1L,
            ),
            book = book,
            chapterCount = 1,
        )
}

private suspend inline fun <reified T : Throwable> assertFails(crossinline block: suspend () -> Unit) {
    try {
        block()
        fail("Expected ${T::class.java.simpleName}")
    } catch (failure: Throwable) {
        if (failure !is T) throw failure
    }
}

private class FakeAnnotationRepository(private val annotations: Flow<List<SavedAnnotationItem>>,) : SavedAnnotationRepository {
    override fun observeAnnotations() = annotations

    override fun observeForBook(bookId: BookId): Flow<List<SavedAnnotation>> = error("Not used")

    override suspend fun save(annotation: SavedAnnotation) = error("Not used")

    override suspend fun delete(annotationId: String) = error("Not used")

    override suspend fun deleteForBook(bookId: BookId) = error("Not used")
}

private class FakeBookRepository(private val books: Flow<List<Book>>) : BookRepository {
    override fun observeBooks() = books

    override suspend fun importBook(uri: Uri): BookImportResult = error("Not used")

    override suspend fun importUrl(rawUrl: String): BookImportResult = error("Not used")

    override suspend fun importText(request: TextImportRequest): BookImportResult = error("Not used")

    override suspend fun getBook(bookId: BookId): Book = error("Not used")

    override suspend fun getChapter(bookId: BookId, chapterIndex: Int): Chapter = error("Not used")

    override suspend fun updateChapterWordCount(bookId: BookId, chapterIndex: Int, wordCount: Int) = error("Not used")

    override suspend fun getBookLanguageTag(bookId: BookId): String? = error("Not used")
}
