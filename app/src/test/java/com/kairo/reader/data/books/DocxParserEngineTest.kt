package com.kairo.reader.data.books

import com.kairo.reader.core.model.BookId
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxParserEngineTest {
    @Test
    fun parseReadsMetadataHeadingsTablesAndImages() {
        val imageBytes = byteArrayOf(9, 8, 7)
        val archive = fixtureDocx(imageBytes)
        var writtenImage: ByteArray? = null

        val book =
            DocxParserEngine.parse(request(archive)) { name, bytes ->
                assertEquals("picture.png", name)
                writtenImage = bytes
                "kairo_docx_assets/docx-test/images/picture.png"
            }

        assertEquals("Research Notes", book.title)
        assertEquals(listOf("Ada", "Grace"), book.authors)
        assertEquals("en-GB", book.languageTag)
        assertEquals(2, book.chapters.size)
        assertEquals("Introduction", book.chapters.first().title)
        assertTrue(book.chapters.first().plainText.contains("First paragraph"))
        assertTrue(book.chapters.first().plainText.contains("Cell one"))
        assertEquals(1, book.chapters.first().imagePaths.size)
        assertArrayEquals(imageBytes, writtenImage)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseRejectsPackagesWithoutMainDocument() {
        val archive = zip(mapOf("[Content_Types].xml" to "<Types/>".toByteArray()))

        DocxParserEngine.parse(request(archive))
    }

    @Test(expected = IllegalArgumentException::class)
    fun boundedZipReaderRejectsTraversalPaths() {
        val archive = zip(mapOf("../word/document.xml" to "unsafe".toByteArray()))

        BoundedZipReader.read(
            archive,
            ZipReadPolicy(
                maxEntries = 10,
                maxEntryBytes = 100,
                maxTotalUncompressedBytes = 100,
                includeEntry = { true },
            ),
        )
    }

    @Test
    fun parseKeepsVisibleRevisionAndFieldResultsWithoutDeletedTextOrInstructions() {
        val document = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:p>
                <w:r><w:t>Current </w:t></w:r>
                <w:del><w:r><w:delText>DELETED </w:delText></w:r></w:del>
                <w:moveFrom><w:r><w:t>OLD LOCATION </w:t></w:r></w:moveFrom>
                <w:r><w:fldChar w:fldCharType="begin"/></w:r>
                <w:r><w:instrText>HYPERLINK "https://example.com" </w:instrText></w:r>
                <w:r><w:fldChar w:fldCharType="separate"/></w:r>
                <w:r><w:t>Visible link label. </w:t></w:r>
                <w:r><w:fldChar w:fldCharType="end"/></w:r>
                <w:ins><w:r><w:t>Inserted text. </w:t></w:r></w:ins>
                <w:moveTo><w:r><w:t>New location.</w:t></w:r></w:moveTo>
              </w:p></w:body>
            </w:document>
        """.trimIndent()
        val archive = zip(
            mapOf(
                "[Content_Types].xml" to "<Types/>".toByteArray(),
                "word/document.xml" to document.toByteArray(),
            )
        )
        val chapter = DocxParserEngine.parse(request(archive)).chapters.single()
        assertEquals("Current Visible link label. Inserted text. New location.", chapter.plainText)
    }

    private fun request(bytes: ByteArray) =
        BinaryBookParseRequest(
            bookId = BookId("docx-test"),
            bytes = bytes,
            sourceDisplayName = "notes.docx",
            sourceExtension = "docx",
        )

    private fun fixtureDocx(imageBytes: ByteArray): ByteArray =
        zip(
            mapOf(
                "[Content_Types].xml" to "<Types/>".toByteArray(),
                "docProps/core.xml" to
                    """
                    <cp:coreProperties xmlns:cp="core" xmlns:dc="dc">
                      <dc:title>Research Notes</dc:title><dc:creator>Ada; Grace</dc:creator>
                      <dc:language>en-GB</dc:language>
                    </cp:coreProperties>
                    """.trimIndent().toByteArray(),
                "word/styles.xml" to
                    """
                    <w:styles xmlns:w="word">
                      <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="Heading 1"/></w:style>
                    </w:styles>
                    """.trimIndent().toByteArray(),
                "word/_rels/document.xml.rels" to
                    """
                    <Relationships xmlns="rels">
                      <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
                        Target="../word/media/picture.png"/>
                    </Relationships>
                    """.trimIndent().toByteArray(),
                "word/media/picture.png" to imageBytes,
                "word/document.xml" to
                    """
                    <w:document xmlns:w="word" xmlns:r="rels" xmlns:a="drawing">
                      <w:body>
                        <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Introduction</w:t></w:r></w:p>
                        <w:p><w:r><w:t>First paragraph with enough readable words.</w:t></w:r>
                          <w:r><w:drawing><a:blip r:embed="rId5"/></w:drawing></w:r></w:p>
                        <w:tbl><w:tr><w:tc><w:p><w:r><w:t>Cell one</w:t></w:r></w:p></w:tc>
                          <w:tc><w:p><w:r><w:t>Cell two</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
                        <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Results</w:t></w:r></w:p>
                        <w:p><w:r><w:t>The second chapter contains more readable words.</w:t></w:r></w:p>
                      </w:body>
                    </w:document>
                    """.trimIndent().toByteArray(),
            ),
        )

    private fun zip(entries: Map<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
}
