package com.kairo.reader.data.books

import android.net.Uri
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.reader.core.dispatchers.DefaultDispatcherProvider
import com.kairo.reader.data.local.BookEntity
import com.kairo.reader.data.local.ChapterEntity
import com.kairo.reader.data.local.KairoDatabase
import com.kairo.reader.data.search.LibrarySearchRepositoryImpl
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeChapterDeviceTest {
    @Test
    fun importedLargeTextCanBeReadBackWithoutLosingContent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, KairoDatabase::class.java).build()
        val source = File.createTempFile("large-chapter-regression-", ".txt", context.cacheDir)
        try {
            val dispatchers = DefaultDispatcherProvider()
            val repository = BookRepositoryImpl(
                database.bookDao(),
                database.epubNavigationDao(),
                listOf(TextFileBookParser(dispatchers)),
                WebArticleExtractor(dispatchers),
                context,
                dispatchers,
            )
            source.writeText("A reader should be able to open the entire imported book.\n\n".repeat(25_000))
            val imported = repository.importBook(Uri.fromFile(source)).book
            val expected = imported.chapters.single()
            Log.i(
                "ReaderRegression",
                "Imported ${source.length()} bytes; chapter row text bytes=" +
                    (expected.htmlContent.toByteArray().size + expected.plainText.toByteArray().size)
            )
            val restored = repository.getChapter(imported.id, expected.index)
            assertEquals(expected.plainText, restored.plainText)
            assertEquals(expected.htmlContent, restored.htmlContent)
        } finally {
            database.close()
            source.delete()
        }
    }

    @Test
    fun existingLargeUnicodeChapterCanBeReadSearchedAndComparedWithoutChangingCoordinates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, KairoDatabase::class.java).build()
        try {
            val prefix = "𐐀é漢🙂 ".repeat(160_000) + "\u0000"
            val plainText = prefix + "needle at the end"
            val chapter = ChapterEntity("large", 7, "Large chapter", "<nav><p>$plainText</p></nav>", plainText)
            database.bookDao().insertBook(
                BookEntity("large", "Large legacy book", emptyList(), "en", null),
                listOf(chapter),
                emptyList(),
            )
            assertEquals(chapter, database.bookDao().getChapter("large", 7))
            assertEquals(listOf(chapter), database.bookDao().getChaptersWithContent("large"))
            assertEquals(plainText, database.epubNavigationDao().getChapterCoordinates("large").single().plainText)
            assertEquals(chapter.htmlContent, database.epubNavigationDao().getChapterHtmlContent("large", 7))
            assertEquals(
                chapter.htmlContent,
                database.epubNavigationDao().getMarkerlessNavigationCandidates("large", "absent-marker", 5_000_000, 1)
                    .single().htmlContent,
            )
            val search = LibrarySearchRepositoryImpl(
                database.searchDao(),
                database.savedAnnotationDao(),
                DefaultDispatcherProvider(),
            ).search("needle at the end", "large").single()
            assertEquals(7, search.chapterIndex)
            assertEquals(prefix.codePointCount(0, prefix.length), search.matchStartCodePointOffset)
            assertNull(database.bookDao().getChapter("large", 8))
        } finally {
            database.close()
        }
    }

    @Test
    fun emptyAndExactChunkBoundaryBodiesRoundTrip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, KairoDatabase::class.java).build()
        try {
            val chapters = listOf(
                ChapterEntity("boundary", 0, null, "", ""),
                ChapterEntity("boundary", 1, null, "a".repeat(128 * 1024), "é".repeat(64 * 1024)),
            )
            database.bookDao().insertBook(
                BookEntity("boundary", "Boundary book", emptyList(), "en", null),
                chapters,
                emptyList(),
            )
            assertEquals(chapters, database.bookDao().getChaptersWithContent("boundary"))
        } finally {
            database.close()
        }
    }
}
