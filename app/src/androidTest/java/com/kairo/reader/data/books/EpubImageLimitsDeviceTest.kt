package com.kairo.reader.data.books

import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.reader.core.dispatchers.DefaultDispatcherProvider
import com.kairo.reader.core.model.BookId
import com.kairo.reader.data.local.KairoDatabase
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubImageLimitsDeviceTest {
    @Test
    fun imageBudgetRejectsPartialBookAndKeepsNormalImages() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "epub-images-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir(): File = directory
        }
        val database = Room.inMemoryDatabaseBuilder(target, KairoDatabase::class.java).build()
        try {
            val dispatchers = DefaultDispatcherProvider()
            val parser = EpubBookParser(dispatchers)
            val normal = createEpub(directory, "normal", listOf(1, 1))
            val book = runBlocking { parser.parse(context, Uri.fromFile(normal), BookId("normal")) }
            assertEquals(2, book.chapters.single().imagePaths.size)
            assertTrue(book.chapters.single().imagePaths.all { File(directory, it).isFile })

            // Each image stays below the individual limit, but together exceed the 25 MiB budget.
            val oversized = createEpub(directory, "oversized", List(5) { 5 * 1024 * 1024 } + 1)
            val repository = BookRepositoryImpl(
                database.bookDao(),
                database.epubNavigationDao(),
                listOf(parser),
                WebArticleExtractor(dispatchers),
                context,
                dispatchers,
            )
            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.importBook(Uri.fromFile(oversized)) }
            }
            assertTrue(error.message.orEmpty().contains("images exceed the total import limit"))
            runBlocking { assertEquals(null, database.bookDao().peekBook()) }
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }

    private fun createEpub(directory: File, name: String, sizes: List<Int>): File {
        val source = File(directory, "$name.epub")
        ZipOutputStream(source.outputStream()).use { zip ->
            fun entry(path: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
            entry(
                "META-INF/container.xml",
                """<container><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>""".toByteArray()
            )
            entry(
                "content.opf",
                """<package><metadata><title>Illustrated book</title></metadata><manifest>
                <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                </manifest><spine><itemref idref="chapter"/></spine></package>""".toByteArray(),
            )
            val images = sizes.indices.joinToString("") { "<img src=\"image$it.png\"/>" }
            entry("chapter.xhtml", "<html><body><p>Readable text.</p>$images</body></html>".toByteArray())
            sizes.forEachIndexed { index, size ->
                zip.putNextEntry(ZipEntry("image$index.png"))
                val block = ByteArray(8192)
                var remaining = size
                while (remaining > 0) {
                    val count = minOf(remaining, block.size)
                    zip.write(block, 0, count)
                    remaining -= count
                }
                zip.closeEntry()
            }
        }
        return source
    }
}
