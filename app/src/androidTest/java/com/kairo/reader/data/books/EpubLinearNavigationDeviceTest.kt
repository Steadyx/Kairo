package com.kairo.reader.data.books

import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.reader.core.dispatchers.DefaultDispatcherProvider
import com.kairo.reader.core.tokenization.Tokenizer
import com.kairo.reader.data.local.KairoDatabase
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubLinearNavigationDeviceTest {
    @Test
    fun importRetainsLinkedNotesWithoutAppendingThemToReadingOrder() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "epub-linear-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir(): File = directory
        }
        val database = Room.inMemoryDatabaseBuilder(target, KairoDatabase::class.java).build()
        try {
            val source = File(directory, "book.epub")
            ZipOutputStream(source.outputStream()).use { zip ->
                fun entry(name: String, body: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(body.toByteArray())
                    zip.closeEntry()
                }
                entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>""")
                entry(
                    "content.opf",
                    """<package><metadata><title>Linear order</title></metadata><manifest>
                    <item id="one" href="one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="two" href="two.xhtml" media-type="application/xhtml+xml"/>
                    <item id="notes" href="notes.xhtml" media-type="application/xhtml+xml"/>
                    <item id="unused" href="unused.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine><itemref idref="one"/><itemref idref="notes" linear="no"/>
                    <itemref idref="two"/></spine></package>""",
                )
                entry("one.xhtml", """<html><body><p>First chapter <a href="notes.xhtml#note">2</a>.</p></body></html>""")
                entry("two.xhtml", "<html><body><p>Second chapter.</p></body></html>")
                entry(
                    "notes.xhtml",
                    """<html><body><p>First note.</p><p id="note">Second note. <a href="one.xhtml">Back</a></p></body></html>"""
                )
                entry("unused.xhtml", "<html><body><p>Unused manifest text.</p></body></html>")
                entry("unmanifested.xhtml", "<html><body><p>Unmanifested text.</p></body></html>")
            }
            val dispatchers = DefaultDispatcherProvider()
            val repository = BookRepositoryImpl(
                database.bookDao(),
                database.epubNavigationDao(),
                listOf(EpubBookParser(dispatchers)),
                WebArticleExtractor(dispatchers),
                context,
                dispatchers,
            )
            val imported = repository.importBook(Uri.fromFile(source)).book
            assertEquals(3, imported.chapters.size)
            assertEquals(listOf(true, true, false), imported.chapters.map { it.isLinear })
            assertTrue(imported.chapters[0].htmlContent.contains("kairo://chapter/2"))
            assertTrue(imported.chapters[2].htmlContent.contains("kairo://chapter/0"))
            val noteLink = Tokenizer().tokenize(imported.chapters[0]).first { it.text == "2" }
            assertEquals(2, noteLink.linkChapterIndex)
            assertEquals(imported.chapters[2].plainText.indexOf("Second note"), noteLink.linkCharacterOffset)
            assertFalse(imported.chapters.any { "Unused manifest" in it.plainText || "Unmanifested" in it.plainText })
            assertEquals(listOf(true, true, false), repository.getBook(imported.id).chapters.map { it.isLinear })
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }
}
