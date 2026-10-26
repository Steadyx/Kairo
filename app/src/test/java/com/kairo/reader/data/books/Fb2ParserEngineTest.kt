package com.kairo.reader.data.books

import com.kairo.reader.core.model.BookId
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.encoding.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fb2ParserEngineTest {
    @Test
    fun parsePreservesImageOnlySectionsBetweenTextChapters() {
        val xml = """
            <FictionBook xmlns:l="http://www.w3.org/1999/xlink"><body>
              <section><p>Before.</p></section>
              <section><image l:href="#figure.png"/></section>
              <section><p>After.</p></section>
            </body><binary id="figure.png" content-type="image/png">AQID</binary></FictionBook>
        """.trimIndent()
        val book = Fb2ParserEngine.parse(request("figures.fb2", "fb2", xml.toByteArray())) { name, _ ->
            "kairo_fb2_assets/test/images/$name"
        }

        assertEquals(3, book.chapters.size)
        assertEquals("Before.", book.chapters[0].plainText)
        assertEquals("", book.chapters[1].plainText)
        assertEquals(listOf("kairo_fb2_assets/test/images/figure.png"), book.chapters[1].imagePaths)
        assertEquals("After.", book.chapters[2].plainText)
        assertEquals(listOf(0, 1, 2), book.chapters.map { it.index })
    }

    @Test
    fun parseRetainsImageOnlyBookWhenTheImageWasResolved() {
        val xml = """
            <FictionBook xmlns:l="http://www.w3.org/1999/xlink"><body>
              <section><image l:href="#figure.png"/></section>
            </body><binary id="figure.png" content-type="image/png">AQID</binary></FictionBook>
        """.trimIndent()
        val book = Fb2ParserEngine.parse(request("figures.fb2", "fb2", xml.toByteArray())) { name, _ ->
            "kairo_fb2_assets/test/images/$name"
        }

        assertEquals(listOf("kairo_fb2_assets/test/images/figure.png"), book.chapters.single().imagePaths)
    }

    @Test
    fun parsePreservesBodyContentAroundSectionsInSourceOrder() {
        val xml = """
            <FictionBook><body>
            <title><p>Body title</p></title><epigraph><p>Opening epigraph.</p></epigraph>
            <section><title><p>First section</p></title><p>First text.</p></section>
            <epigraph><p>Middle epigraph.</p></epigraph>
            <section><title><p>Second section</p></title><p>Second text.</p></section>
            <p>Closing text.</p>
            </body></FictionBook>
        """.trimIndent()
        val book = Fb2ParserEngine.parse(request("intro.fb2", "fb2", xml.toByteArray()))
        val text = book.chapters.joinToString(" ") { it.plainText }.replace(Regex("\\s+"), " ")
        assertEquals(
            "Body title Opening epigraph. First section First text. Middle epigraph. Second section Second text. Closing text.",
            text,
        )
        assertEquals(book.chapters.indices.toList(), book.chapters.map { it.index })
    }

    @Test
    fun parseReadsMetadataSectionsAndEmbeddedImages() {
        val imageBytes = byteArrayOf(1, 2, 3, 4)
        val xml = fixtureXml(Base64.Default.encode(imageBytes))
        val writtenImages = mutableMapOf<String, ByteArray>()

        val book =
            Fb2ParserEngine.parse(request("novel.fb2", "fb2", xml.toByteArray())) { name, bytes ->
                writtenImages[name] = bytes
                "kairo_fb2_assets/test/images/$name"
            }

        assertEquals("A Structured Novel", book.title)
        assertEquals(listOf("Ada Lovelace"), book.authors)
        assertEquals("en", book.languageTag)
        assertEquals(2, book.chapters.size)
        assertEquals("First Chapter", book.chapters.first().title)
        assertTrue(book.chapters.first().plainText.contains("Opening paragraph."))
        assertEquals(1, book.chapters.first().imagePaths.size)
        assertArrayEquals(imageBytes, book.coverImage)
        assertArrayEquals(imageBytes, writtenImages.values.single())
    }

    @Test
    fun parseReadsUnpaddedEmbeddedImages() {
        val imageBytes = byteArrayOf(1, 2)
        val encoded = Base64.Default.encode(imageBytes).trimEnd('=')

        val book = Fb2ParserEngine.parse(request("novel.fb2", "fb2", fixtureXml(encoded).toByteArray()))

        assertArrayEquals(imageBytes, book.coverImage)
    }

    @Test
    fun parseReadsWhitespaceWrappedEmbeddedImages() {
        val imageBytes = ByteArray(24) { it.toByte() }
        val encoded = Base64.Default.encode(imageBytes).chunked(8).joinToString("\n            ")

        val book = Fb2ParserEngine.parse(request("novel.fb2", "fb2", fixtureXml(encoded).toByteArray()))

        assertArrayEquals(imageBytes, book.coverImage)
    }

    @Test
    fun parseIgnoresMalformedEmbeddedImages() {
        val writtenImages = mutableMapOf<String, ByteArray>()

        val book =
            Fb2ParserEngine.parse(request("novel.fb2", "fb2", fixtureXml("not-base64!").toByteArray())) { name, bytes ->
                writtenImages[name] = bytes
                "kairo_fb2_assets/test/images/$name"
            }

        assertNull(book.coverImage)
        assertTrue(book.chapters.first().imagePaths.isEmpty())
        assertTrue(writtenImages.isEmpty())
    }

    @Test
    fun parseReadsSingleFb2FromZip() {
        val archive = zip("book.fb2", fixtureXml("").toByteArray())

        val book = Fb2ParserEngine.parse(request("book.fb2.zip", "fb2.zip", archive))

        assertEquals("A Structured Novel", book.title)
        assertEquals(2, book.chapters.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseRejectsZipWithMultipleFb2Documents() {
        val archive =
            ByteArrayOutputStream().use { output ->
                ZipOutputStream(output).use { zip ->
                    listOf("one.fb2", "two.fb2").forEach { name ->
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(fixtureXml("").toByteArray())
                        zip.closeEntry()
                    }
                }
                output.toByteArray()
            }

        Fb2ParserEngine.parse(request("books.fb2.zip", "fb2.zip", archive))
    }

    @Test(expected = Exception::class)
    fun parseRejectsDoctypeDeclarations() {
        val xml =
            """
            <?xml version="1.0"?>
            <!DOCTYPE FictionBook [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description><title-info><book-title>&xxe;</book-title></title-info></description>
              <body><section><p>Readable words are present in this malicious file.</p></section></body>
            </FictionBook>
            """.trimIndent()

        Fb2ParserEngine.parse(request("unsafe.fb2", "fb2", xml.toByteArray()))
    }

    private fun request(
        displayName: String,
        extension: String,
        bytes: ByteArray,
    ) =
        BinaryBookParseRequest(
            bookId = BookId("test"),
            bytes = bytes,
            sourceDisplayName = displayName,
            sourceExtension = extension,
        )

    private fun zip(
        name: String,
        bytes: ByteArray,
    ): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            output.toByteArray()
        }

    private fun fixtureXml(encodedImage: String): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"
            xmlns:l="http://www.w3.org/1999/xlink">
          <description>
            <title-info>
              <genre>fiction</genre>
              <author><first-name>Ada</first-name><last-name>Lovelace</last-name></author>
              <book-title>A Structured Novel</book-title>
              <lang>en</lang>
              <coverpage><image l:href="#cover.png"/></coverpage>
            </title-info>
          </description>
          <body>
            <section>
              <title><p>First Chapter</p></title>
              <p>Opening <emphasis>paragraph</emphasis>.</p>
              <image l:href="#cover.png" alt="Cover"/>
            </section>
            <section><title><p>Second Chapter</p></title><p>More readable words follow here.</p></section>
          </body>
          <binary id="cover.png" content-type="image/png">$encodedImage</binary>
        </FictionBook>
        """.trimIndent()
}
