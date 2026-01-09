package com.example.kairo.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubBookParserTest {
    private val parser = EpubBookParser(TestDispatchers)

    @Test
    fun extractPlainTextKeepsInlinePageBreakContent() {
        val html =
            "<p>Indiana and" +
                "<span class=\"right_1\" epub:type=\"pagebreak\" id=\"page_250\" title=\"250\"/>" +
                " Leo took up the rear.</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("Indiana and Leo took up the rear."))
    }

    @Test
    fun extractPlainTextKeepsClosedPageBreakSpanContent() {
        val html =
            "<p>Indiana and" +
                "<span epub:type=\"pagebreak\" id=\"page_250\" title=\"250\">250</span>" +
                " Leo took up the rear.</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("Indiana and Leo took up the rear."))
    }

    @Test
    fun extractPlainTextKeepsContentAfterRolePageBreak() {
        val html =
            "<p>Start" +
                "<span role=\"doc-pagebreak\" id=\"page_10\" title=\"10\"/>" +
                " end.</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("Start end."))
    }

    @Test
    fun extractPlainTextDecodesEntitiesAndPreservesParagraphs() {
        val html = "<p>Hello&nbsp;world &amp; friends.</p><p>Next&nbsp;para.</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("Hello world & friends."))
        assertTrue(text.contains("Next para."))
        assertTrue(text.contains("friends.\n\nNext"))
    }

    @Test
    fun extractPlainTextDecodesNamedEntities() {
        val html = "<p>&ldquo;Hello&rdquo; &mdash; a test&hellip;</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("\u201CHello\u201D \u2014 a test\u2026"))
    }

    @Test
    fun sanitizeSrcDecodesUrlEncodingAndEntities() {
        val raw = "Images/Some%20Image%20&amp;%20Cover.jpg"

        val cleaned: String = parser.callPrivate("sanitizeSrc", raw)

        assertEquals("Images/Some Image & Cover.jpg", cleaned)
    }

    @Test
    fun normalizeContainerPathHandlesEncodedAndSlashedPaths() {
        val resolved: String = parser.callPrivate("normalizeContainerPath", "/OEBPS/content%2Eopf")

        assertEquals("OEBPS/content.opf", resolved)
    }

    @Test
    fun decodeTextEntryRespectsXmlEncoding() {
        val xml =
            "<?xml version=\"1.0\" encoding=\"UTF-16LE\"?><html><body>Hi</body></html>"
                .toByteArray(Charsets.UTF_16LE)

        val decoded: String = parser.callPrivate("decodeTextEntry", xml)

        assertTrue(decoded.contains("Hi"))
    }

    @Test
    fun parseOpfFileHandlesNamespacedManifestAndSpine() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Sample</dc:title>
              </metadata>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml" />
                <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="c1" />
                <itemref idref="c2" />
              </spine>
            </package>
            """.trimIndent()

        val opfData: Any = parser.callPrivate("parseOpfFile", xml)
        val manifest =
            opfData.javaClass.getDeclaredField("manifest").apply { isAccessible = true }
                .get(opfData) as Map<*, *>
        val spineItems =
            opfData.javaClass.getDeclaredField("spineItems").apply { isAccessible = true }
                .get(opfData) as List<*>

        assertTrue(manifest.containsKey("c1"))
        assertEquals(2, spineItems.size)
    }
}
