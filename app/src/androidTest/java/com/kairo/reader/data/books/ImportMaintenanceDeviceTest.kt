package com.kairo.reader.data.books

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.reader.core.dispatchers.DefaultDispatcherProvider
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.tokenization.CHAPTER_WORD_COUNT_VERSION
import com.kairo.reader.core.tokenization.needsChapterWordCountRepair
import com.kairo.reader.data.library.LibraryRepositoryImpl
import com.kairo.reader.data.local.KairoDatabase
import com.kairo.reader.data.local.toEntity
import com.kairo.reader.data.token.TokenRepositoryImpl
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** All files and database records belong to this fixture, never the personal library. */
@RunWith(AndroidJUnit4::class)
class ImportMaintenanceDeviceTest {
    private lateinit var directory: File
    private lateinit var database: KairoDatabase
    private lateinit var repository: BookRepositoryImpl
    private lateinit var fixtureContext: Context

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(target.cacheDir, "import-regression-${UUID.randomUUID()}").apply { mkdirs() }
        fixtureContext = object : ContextWrapper(target) {
            override fun getFilesDir(): File = directory
            override fun getCacheDir(): File = directory
            override fun getApplicationContext(): Context = this
        }
        database = Room.inMemoryDatabaseBuilder(target, KairoDatabase::class.java).build()
        val dispatchers = DefaultDispatcherProvider()
        repository = BookRepositoryImpl(
            database.bookDao(),
            database.epubNavigationDao(),
            listOf(EpubBookParser(dispatchers)),
            WebArticleExtractor(dispatchers),
            fixtureContext,
            dispatchers,
        )
    }

    @After
    fun tearDown() {
        database.close()
        directory.deleteRecursively()
    }

    @Test
    fun cjkImportCountsReadingUnitsAndMetadataDoesNotLoadText() = runBlocking {
        val result = repository.importText(TextImportRequest("这是一个没有空格但完全可以阅读的中文段落"))
        val chapter = result.book.chapters.single()
        assertTrue(chapter.wordCount > 1)
        val summary = repository.getBook(result.book.id).chapters.single()
        assertEquals(CHAPTER_WORD_COUNT_VERSION, summary.wordCountVersion)
        assertFalse(needsChapterWordCountRepair(summary, result.book.languageTag))
        assertTrue(summary.plainText.isEmpty())
        assertTrue(summary.htmlContent.isEmpty())
        assertEquals(chapter.plainText, repository.getChapter(result.book.id, 0).plainText)
        repository.updateChapterWordCount(result.book.id, 0, chapter.wordCount + 1)
        assertEquals(chapter.wordCount + 1, repository.getChapter(result.book.id, 0).wordCount)
    }

    @Test
    fun repairingUnchangedOrZeroCountsStillPersistsTheVersion() = runBlocking {
        val result = repository.importText(TextImportRequest("这是一个可以阅读的中文段落"))
        val id = result.book.id
        database.openHelper.writableDatabase.execSQL("UPDATE chapters SET wordCountVersion = 0")
        val count = repository.getBook(id).chapters.single().wordCount
        repository.updateChapterWordCount(id, 0, count)
        assertEquals(CHAPTER_WORD_COUNT_VERSION, repository.getBook(id).chapters.single().wordCountVersion)
        repository.updateChapterWordCount(id, 0, 0)
        val empty = repository.getBook(id).chapters.single()
        assertEquals(0, empty.wordCount)
        assertFalse(needsChapterWordCountRepair(empty, "zh-Hans"))
    }

    @Test
    fun deletionInvalidatesOnlyTheDeletedBooksCachedContent() = runBlocking {
        val first = repository.importText(TextImportRequest("First book with readable words."))
        val second = repository.importText(TextImportRequest("Second book with different readable words."))
        val dispatchers = DefaultDispatcherProvider()
        val tokens = TokenRepositoryImpl(repository, dispatchers)
        tokens.getTokens(first.book.id, 0)
        val secondTokens = tokens.getTokens(second.book.id, 0)
        val invalidated = mutableListOf<BookId>()
        val library = LibraryRepositoryImpl(
            repository, database, database.bookDao(), database.readingPositionDao(),
            database.bookmarkDao(), database.savedAnnotationDao(), database.readingSessionDao(),
            invalidateBookCaches = { id ->
                invalidated += id
                tokens.invalidateBook(id)
            },
            invalidateAllCaches = { error("Unrelated books must stay cached") },
            appContext = fixtureContext,
            dispatcherProvider = dispatchers,
        )
        library.delete(first.book.id.value)
        assertEquals(listOf(first.book.id), invalidated)
        assertTrue(runCatching { tokens.getTokens(first.book.id, 0) }.isFailure)
        assertTrue(secondTokens === tokens.getTokens(second.book.id, 0))
    }

    @Test
    fun reimportWaitsForDeletedBookAssetsToBeRemoved() = runBlocking {
        val request = TextImportRequest("An imported book with readable words.")
        val original = repository.importText(request)
        val deletionReachedCleanup = CountDownLatch(1)
        val allowCleanup = CountDownLatch(1)
        val blockDeletion = AtomicBoolean(true)
        val dispatchers = DefaultDispatcherProvider()
        val library = LibraryRepositoryImpl(
            repository, database, database.bookDao(), database.readingPositionDao(),
            database.bookmarkDao(), database.savedAnnotationDao(), database.readingSessionDao(),
            invalidateBookCaches = {
                if (blockDeletion.getAndSet(false)) {
                    deletionReachedCleanup.countDown()
                    check(allowCleanup.await(10, TimeUnit.SECONDS))
                }
            },
            invalidateAllCaches = {},
            appContext = fixtureContext,
            dispatcherProvider = dispatchers,
        )
        val deletion = async(Dispatchers.IO) { library.delete(original.book.id.value) }
        try {
            assertTrue(withContext(Dispatchers.IO) { deletionReachedCleanup.await(10, TimeUnit.SECONDS) })
            val reimport = async(Dispatchers.IO) { library.importText(request) }
            delay(200)
            assertFalse("Import must wait until deletion cleanup finishes", reimport.isCompleted)
            allowCleanup.countDown()
            deletion.await()
            assertEquals(original.book.id, reimport.await().book.id)
            assertEquals(original.book.id, repository.getBook(original.book.id).id)
        } finally {
            allowCleanup.countDown()
        }
    }

    @Test
    fun sameTextWithDifferentIllustrationsImportsAsSeparateBooks() = runBlocking {
        fun bookFile(name: String, color: Int): File {
            val file = File(directory, name)
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            val image = ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
            bitmap.recycle()
            ZipOutputStream(file.outputStream()).use { zip ->
                fun entry(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                entry(
                    "META-INF/container.xml",
                    """<container><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>""".toByteArray()
                )
                entry(
                    "content.opf",
                    """
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata><title>Illustrated edition</title></metadata>
                    <manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                    <item id="image" href="image.png" media-type="image/png"/></manifest>
                    <spine><itemref idref="chapter"/></spine></package>
                    """.trimIndent().toByteArray()
                )
                entry("chapter.xhtml", """<html><body><p>The same readable text.</p><img src="image.png"/></body></html>""".toByteArray())
                entry("image.png", image)
            }
            return file
        }
        val first = repository.importBook(Uri.fromFile(bookFile("red.epub", android.graphics.Color.RED)))
        val second = repository.importBook(Uri.fromFile(bookFile("blue.epub", android.graphics.Color.BLUE)))
        assertFalse(second.alreadyImported)
        assertTrue(first.book.id != second.book.id)
        assertTrue(first.book.chapters.single().imagePaths.isNotEmpty())
        assertTrue(second.book.chapters.single().imagePaths.isNotEmpty())
    }

    @Test
    fun reimportRepairsLegacyEpubLinearityWithoutChangingCoordinates() = runBlocking {
        val file = File(directory, "legacy-order.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>""")
            entry(
                "content.opf",
                """
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata><title>Legacy order</title></metadata>
                <manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                <item id="notes" href="notes.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="chapter"/><itemref idref="notes" linear="no"/></spine></package>
                """.trimIndent()
            )
            entry("chapter.xhtml", """<html><body><p>Some readable text <a href="notes.xhtml#note">note</a>.</p></body></html>""")
            entry("notes.xhtml", """<html><body><p id="note">A linked footnote.</p></body></html>""")
        }
        val first = repository.importBook(Uri.fromFile(file)).book
        assertTrue(first.chapters.any { !it.isLinear })
        val legacy = first.chapters.map { chapter ->
            chapter.copy(
                isLinear = true,
                htmlContent = chapter.htmlContent.replace(Regex("\\s+data-kairo-(?:aux|offset)=\"[^\"]*\""), ""),
            ).toEntity(first.id)
        }
        fixtureContext.getSharedPreferences("epub_reading_order_repair", Context.MODE_PRIVATE)
            .edit().remove(first.id.value).commit()
        val extra = Chapter(first.chapters.size, "Unreferenced", "<p>Extra.</p>", "Extra.")
        database.bookDao().insertChapters(legacy + extra.toEntity(first.id))

        val result = repository.importBook(Uri.fromFile(file))
        assertTrue(result.alreadyImported)
        val repaired = database.bookDao().getChaptersWithContent(first.id.value)
        assertEquals(first.chapters.size + 1, repaired.size)
        first.chapters.forEach { chapter ->
            assertEquals(chapter.isLinear, repaired.single { it.index == chapter.index }.isLinear)
        }
        assertFalse(repaired.single { it.index == extra.index }.isLinear)
        assertEquals("Extra.", repaired.single { it.index == extra.index }.plainText)
        assertTrue(repaired.single { it.index == 0 }.htmlContent.contains("data-kairo-aux=\"1\""))
        assertTrue(repaired.single { it.index == 0 }.htmlContent.contains("data-kairo-offset=\""))
    }

    @Test
    fun ambiguousLegacyReimportFailsWithoutChangingTheExistingBook() = runBlocking {
        val file = File(directory, "ambiguous.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>""")
            entry(
                "content.opf",
                """
                <package xmlns="http://www.idpf.org/2007/opf"><metadata><title>Ambiguous</title></metadata>
                <manifest><item id="one" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="one"/></spine></package>
                """.trimIndent()
            )
            entry("chapter.xhtml", "<html><body><p>The same chapter text.</p></body></html>")
        }
        val first = repository.importBook(Uri.fromFile(file)).book
        database.bookDao().insertChapters(listOf(first.chapters.single().copy(index = 1).toEntity(first.id)))
        fixtureContext.getSharedPreferences("epub_reading_order_repair", Context.MODE_PRIVATE)
            .edit().remove(first.id.value).commit()
        val before = database.bookDao().getChaptersWithContent(first.id.value)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.importBook(Uri.fromFile(file)) } }
        assertEquals(before, database.bookDao().getChaptersWithContent(first.id.value))
    }

    @Test
    fun oversizedSpineChapterFailsBeforeAnyBookIsPersisted() {
        val source = File(directory, "partial.epub")
        ZipOutputStream(source.outputStream()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>""")
            entry(
                "content.opf",
                """
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata><title>Partial book</title></metadata>
                <manifest><item id="one" href="one.xhtml" media-type="application/xhtml+xml"/>
                <item id="two" href="two.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="one"/><itemref idref="two"/></spine></package>
                """.trimIndent()
            )
            entry("one.xhtml", "<html><body><p>A readable first chapter with enough words.</p></body></html>")
            entry("two.xhtml", "<html><body><p>${"word ".repeat(1_100_000)}</p></body></html>")
        }
        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.importBook(Uri.fromFile(source)) }
        }
        assertTrue(failure.message.orEmpty().contains("import limit"))
        runBlocking { assertEquals(null, database.bookDao().peekBook()) }
    }

    @Test
    fun oversizedIgnoredAssetFailsBeforeAnyBookIsPersisted() {
        val source = File(directory, "ignored-asset.epub")
        ZipOutputStream(source.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("ignored.bin"))
            val block = ByteArray(8192)
            repeat(4224) { zip.write(block) } // 33 MiB expanded, below 40 KiB compressed.
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("chapter.xhtml"))
            zip.write("<html><body><p>A readable chapter.</p></body></html>".toByteArray())
            zip.closeEntry()
        }
        assertTrue(source.length() < 40 * 1024)
        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.importBook(Uri.fromFile(source)) }
        }
        assertTrue(failure.message.orEmpty().contains("decompression limit"))
        runBlocking { assertEquals(null, database.bookDao().peekBook()) }
    }

    @Test
    fun smallCompressedCoverStillGetsResized() {
        val bitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        val source = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        assertTrue(source.size < 256 * 1024)
        val optimized = requireNotNull(CoverImageOptimizer.optimize(source))
        assertFalse(source.contentEquals(optimized))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(optimized, 0, optimized.size, bounds)
        assertTrue(bounds.outWidth in 1..1080)
        assertTrue(bounds.outHeight in 1..1080)
    }
}
