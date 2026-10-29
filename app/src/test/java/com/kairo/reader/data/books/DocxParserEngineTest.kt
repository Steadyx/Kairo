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
    fun parseKeepsImageOnlyContentBeforeNextChapter() {
        val document = """
            <w:document xmlns:w="word" xmlns:r="rels" xmlns:a="drawing"><w:body>
              <w:p><w:r><w:drawing><a:blip r:embed="rId1"/></w:drawing></w:r></w:p>
              <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Next chapter</w:t></w:r></w:p>
              <w:p><w:r><w:t>Text follows.</w:t></w:r></w:p>
            </w:body></w:document>
        """.trimIndent()
        val book = DocxParserEngine.parse(request(imageDocx(document))) { name, _ -> "kairo_docx_assets/test/images/$name" }

        assertEquals(2, book.chapters.size)
        assertEquals("", book.chapters.first().plainText)
        assertEquals(listOf("kairo_docx_assets/test/images/figure.png"), book.chapters.first().imagePaths)
        assertTrue(book.chapters[1].plainText.contains("Text follows."))
    }

    @Test
    fun parseKeepsImageOnlyDocument() {
        val document = """
            <w:document xmlns:w="word" xmlns:r="rels" xmlns:a="drawing"><w:body>
              <w:p><w:r><w:drawing><a:blip r:embed="rId1"/></w:drawing></w:r></w:p>
            </w:body></w:document>
        """.trimIndent()
        val book = DocxParserEngine.parse(request(imageDocx(document))) { name, _ -> "kairo_docx_assets/test/images/$name" }

        assertEquals(listOf("kairo_docx_assets/test/images/figure.png"), book.chapters.single().imagePaths)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseRejectsDocumentWithoutTextOrResolvedImages() {
        val document = "<w:document xmlns:w='word'><w:body><w:p/></w:body></w:document>"
        val archive = zip(mapOf("[Content_Types].xml" to "<Types/>".toByteArray(), "word/document.xml" to document.toByteArray()))
        DocxParserEngine.parse(request(archive))
    }

    @Test
    fun parsePreservesContentControlsAndNestedTablesInOrder() {
        val document = """
            <w:document xmlns:w="word"><w:body>
              <w:p><w:r><w:t>Before.</w:t></w:r></w:p>
              <w:sdt><w:sdtPr><w:alias w:val="not content"/></w:sdtPr><w:sdtContent>
                <w:p><w:r><w:t>Control.</w:t></w:r></w:p>
                <w:customXml><w:p><w:r><w:t>Custom.</w:t></w:r></w:p></w:customXml>
                <w:tbl><w:tr><w:tc>
                  <w:p><w:r><w:t>Outer before.</w:t></w:r></w:p>
                  <w:tbl><w:tr><w:tc><w:sdt><w:sdtContent>
                    <w:p><w:r><w:t>Nested.</w:t></w:r></w:p>
                  </w:sdtContent></w:sdt></w:tc></w:tr></w:tbl>
                  <w:p><w:r><w:t>Outer after.</w:t></w:r></w:p>
                </w:tc></w:tr></w:tbl>
              </w:sdtContent></w:sdt>
              <w:del><w:p><w:r><w:t>Deleted.</w:t></w:r></w:p></w:del>
              <w:p><w:r><w:t>After.</w:t></w:r></w:p>
            </w:body></w:document>
        """.trimIndent()
        val archive = zip(mapOf("[Content_Types].xml" to "<Types/>".toByteArray(), "word/document.xml" to document.toByteArray()))
        val chapter = DocxParserEngine.parse(request(archive)).chapters.single()

        assertEquals(
            "Before. Control. Custom. Outer before. Nested. Outer after. After.",
            chapter.plainText.replace(Regex("\\s+"), " ").trim(),
        )
        assertTrue(chapter.htmlContent.contains("<table><tr><td><p>Nested.</p></td></tr></table>"))
    }

    @Test
    fun parseAcceptsDocumentWhoseOnlyTextIsInsideContentControl() {
        val document = """
            <w:document xmlns:w="word"><w:body><w:sdt><w:sdtContent>
              <w:p><w:r><w:t>Controlled content.</w:t></w:r></w:p>
            </w:sdtContent></w:sdt></w:body></w:document>
        """.trimIndent()
        val archive = zip(mapOf("[Content_Types].xml" to "<Types/>".toByteArray(), "word/document.xml" to document.toByteArray()))

        assertEquals("Controlled content.", DocxParserEngine.parse(request(archive)).chapters.single().plainText)
    }

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

    private fun imageDocx(document: String): ByteArray =
        zip(
            mapOf(
                "[Content_Types].xml" to "<Types/>".toByteArray(),
                "word/document.xml" to document.toByteArray(),
                "word/_rels/document.xml.rels" to
                    """
                    <Relationships><Relationship Id="rId1" Type="http://example.com/image" Target="media/figure.png"/></Relationships>
                    """.trimIndent().toByteArray(),
                "word/media/figure.png" to byteArrayOf(1, 2, 3),
            ),
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
