package com.example.kairo.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun sanitizeSrcPreservesPlusCharacters() {
        val raw = "Images/Chapter+1%2B2.jpg"

        val cleaned: String = parser.callPrivate("sanitizeSrc", raw)

        assertEquals("Images/Chapter+1+2.jpg", cleaned)
    }

    @Test
    fun normalizeContainerPathHandlesEncodedAndSlashedPaths() {
        val resolved: String = parser.callPrivate("normalizeContainerPath", "/OEBPS/content%2Eopf")

        assertEquals("OEBPS/content.opf", resolved)
    }

    @Test
    fun normalizeContainerPathPreservesPlusCharacters() {
        val resolved: String = parser.callPrivate("normalizeContainerPath", "/OEBPS/Book+One.opf")

        assertEquals("OEBPS/Book+One.opf", resolved)
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

        val opfData: Any = invokeParseOpfData(xml)
        val manifest =
            opfData.javaClass.getDeclaredField("manifest").apply { isAccessible = true }
                .get(opfData) as Map<*, *>
        val spineItems =
            opfData.javaClass.getDeclaredField("spineItems").apply { isAccessible = true }
                .get(opfData) as List<*>

        assertTrue(manifest.containsKey("c1"))
        assertEquals(2, spineItems.size)
    }

    @Test
    fun parseOpfFileIgnoresItemTagsOutsideManifestSection() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <metadata>
                <meta name="cover" content="noise"/>
                <custom:item xmlns:custom="urn:test" id="noise" href="noise.xhtml" media-type="application/xhtml+xml"/>
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

        val opfData: Any = invokeParseOpfData(xml)
        val manifest =
            opfData.javaClass.getDeclaredField("manifest").apply { isAccessible = true }
                .get(opfData) as Map<*, *>

        assertEquals(2, manifest.size)
        assertEquals("chapter1.xhtml", manifest["c1"])
        assertEquals("chapter2.xhtml", manifest["c2"])
    }

    @Test
    fun parseOpfFileUsesLenientFallbackOnMalformedXml() {
        val malformedXml =
            """
            <package>
              <metadata><dc:title xmlns:dc="http://purl.org/dc/elements/1.1/">Broken</dc:title></metadata>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml">
                <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml">
              </manifest>
              <spine>
                <itemref idref="c2">
                <itemref idref="c1">
              </spine>
            """.trimIndent()

        val opfData: Any = invokeParseOpfData(malformedXml)
        val manifest =
            opfData.javaClass.getDeclaredField("manifest").apply { isAccessible = true }
                .get(opfData) as Map<*, *>
        val spineItems =
            opfData.javaClass.getDeclaredField("spineItems").apply { isAccessible = true }
                .get(opfData) as List<*>

        assertEquals("chapter1.xhtml", manifest["c1"])
        assertEquals("chapter2.xhtml", manifest["c2"])
        assertEquals(2, spineItems.size)
    }

    @Test
    fun parseOpfFileWithResultReportsLenientFallbackUsage() {
        val malformedXml =
            """
            <package>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml">
              </manifest>
            """.trimIndent()

        val usedLenientFallback = invokeParseOpfFileWithResultUsedLenient(malformedXml)

        assertTrue(usedLenientFallback)
    }

    @Test
    fun parseOpfFileLenientExtractsManifestItemsOnlyFromManifestSection() {
        val xml =
            """
            <package>
              <metadata>
                <item id="noise" href="noise.xhtml" media-type="application/xhtml+xml" />
              </metadata>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="c1" />
              </spine>
            </package>
            """.trimIndent()

        val opfData: Any = parser.callPrivate("parseOpfFileLenient", xml)
        val manifest =
            opfData.javaClass.getDeclaredField("manifest").apply { isAccessible = true }
                .get(opfData) as Map<*, *>

        assertEquals(1, manifest.size)
        assertEquals("chapter1.xhtml", manifest["c1"])
        assertNull(manifest["noise"])
    }

    @Test
    fun parseOpfFileLenientSupportsNamespacedItemAndItemrefTags() {
        val xml =
            """
            <opf:package xmlns:opf="http://www.idpf.org/2007/opf">
              <opf:manifest>
                <opf:item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml" />
              </opf:manifest>
              <opf:spine>
                <opf:itemref idref="c1" />
              </opf:spine>
            </opf:package>
            """.trimIndent()

        val opfData: Any = parser.callPrivate("parseOpfFileLenient", xml)
        val manifest =
            opfData.javaClass.getDeclaredField("manifest").apply { isAccessible = true }
                .get(opfData) as Map<*, *>
        val spineItems =
            opfData.javaClass.getDeclaredField("spineItems").apply { isAccessible = true }
                .get(opfData) as List<*>

        assertEquals("chapter1.xhtml", manifest["c1"])
        assertEquals(1, spineItems.size)
    }

    @Test
    fun parseOpfFileLenientDoesNotSynthesizeSpineForNonPackageXml() {
        val xml =
            """
            <root>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml" />
              </manifest>
            </root>
            """.trimIndent()

        val opfData: Any = parser.callPrivate("parseOpfFileLenient", xml)
        val spineItems =
            opfData.javaClass.getDeclaredField("spineItems").apply { isAccessible = true }
                .get(opfData) as List<*>

        assertTrue(spineItems.isEmpty())
    }

    @Test
    fun parseContainerXmlPrefersOebpsPackageMediaType() {
        val containerXml =
            """
            <container version="1.0"
              xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="alt/book.opf" media-type="text/plain"/>
                <rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """.trimIndent()

        val resolved = invokeParseContainerXmlWithResult(containerXml).first

        assertEquals("OPS/package.opf", resolved)
    }

    @Test
    fun parseContainerXmlWithResultReportsLenientFallbackUsage() {
        val malformedXml = "<container><rootfiles><rootfile full-path='OPS/content.opf'"

        val (resolvedPath, usedLenientFallback) = invokeParseContainerXmlWithResult(malformedXml)

        assertEquals("OPS/content.opf", resolvedPath)
        assertTrue(usedLenientFallback)
    }

    @Test
    fun selectBestOpfPrefersCandidateWithReadableSpineAndManifest() {
        val invalidOpf =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Invalid</dc:title>
              </metadata>
            </package>
            """.trimIndent()
        val validOpf =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="c1" />
              </spine>
            </package>
            """.trimIndent()

        val path =
            invokeSelectBestOpfPath(
                containerCandidates = listOf("OPS/invalid.opf", "OEBPS/content.opf"),
                zipEntryNamesLower = setOf("ops/invalid.opf", "oebps/content.opf"),
                zipTextEntries =
                    mapOf(
                        "ops/invalid.opf" to invalidOpf.toByteArray(),
                        "oebps/content.opf" to validOpf.toByteArray(),
                    ),
            )

        assertEquals("oebps/content.opf", path)
    }

    @Test
    fun resolveOpfPathPrefersContentOpfFallback() {
        val entries =
            linkedSetOf(
                "oebps/package.opf",
                "opf/content.opf",
                "meta-inf/container.xml",
            )

        val resolved = invokeResolveOpfPath(null, entries)

        assertEquals("opf/content.opf", resolved)
    }

    @Test
    fun buildFallbackChaptersUsesNaturalFilenameOrdering() {
        val entries =
            linkedMapOf(
                "oebps/chapter10.xhtml" to "<html><body><p>Ten</p></body></html>".toByteArray(),
                "oebps/chapter2.xhtml" to "<html><body><p>Two</p></body></html>".toByteArray(),
                "oebps/chapter1.xhtml" to "<html><body><p>One</p></body></html>".toByteArray(),
            )

        val parsed = invokeBuildFallbackChapters(entries, emptyMap())
        val orderedPaths = parsed.map { parsedChapterPath(it) }

        assertEquals(
            listOf(
                "oebps/chapter1.xhtml",
                "oebps/chapter2.xhtml",
                "oebps/chapter10.xhtml",
            ),
            orderedPaths,
        )
    }

    @Test
    fun buildFallbackChaptersPrefersProvidedManifestOrder() {
        val entries =
            linkedMapOf(
                "oebps/chapter10.xhtml" to "<html><body><p>Ten</p></body></html>".toByteArray(),
                "oebps/chapter2.xhtml" to "<html><body><p>Two</p></body></html>".toByteArray(),
                "oebps/chapter1.xhtml" to "<html><body><p>One</p></body></html>".toByteArray(),
            )

        val parsed =
            invokeBuildFallbackChapters(
                zipTextEntries = entries,
                imageRelativePathByEpubPathLower = emptyMap(),
                preferredChapterPathsLower = listOf(
                    "oebps/chapter2.xhtml",
                    "oebps/chapter10.xhtml",
                    "oebps/chapter1.xhtml",
                ),
            )
        val orderedPaths = parsed.map { parsedChapterPath(it) }

        assertEquals(
            listOf(
                "oebps/chapter2.xhtml",
                "oebps/chapter10.xhtml",
                "oebps/chapter1.xhtml",
            ),
            orderedPaths,
        )
    }

    @Test
    fun buildFallbackChaptersKeepsTocPathWhenContentLooksLikeRegularChapter() {
        val entries =
            linkedMapOf(
                "oebps/toc.xhtml" to "<html><body><p>Once upon a time we begin the story.</p></body></html>".toByteArray(),
                "oebps/chapter1.xhtml" to "<html><body><p>Chapter one prose.</p></body></html>".toByteArray(),
            )

        val parsed =
            invokeBuildFallbackChapters(
                zipTextEntries = entries,
                imageRelativePathByEpubPathLower = emptyMap(),
            )
        val orderedPaths = parsed.map { parsedChapterPath(it) }

        assertEquals(2, orderedPaths.size)
        assertTrue(orderedPaths.contains("oebps/toc.xhtml"))
        assertTrue(orderedPaths.contains("oebps/chapter1.xhtml"))
    }

    @Test
    fun buildFallbackChaptersFiltersNavigationLikeTocWhenOtherChaptersExist() {
        val tocLinks =
            (1..14).joinToString(separator = "") { index ->
                "<a href=\"chapter$index.xhtml\">Chapter $index</a><br/>"
            }
        val entries =
            linkedMapOf(
                "oebps/toc.xhtml" to "<html><body><h1>Table of Contents</h1>$tocLinks</body></html>".toByteArray(),
                "oebps/chapter1.xhtml" to "<html><body><p>Chapter one prose.</p></body></html>".toByteArray(),
            )

        val parsed =
            invokeBuildFallbackChapters(
                zipTextEntries = entries,
                imageRelativePathByEpubPathLower = emptyMap(),
            )
        val orderedPaths = parsed.map { parsedChapterPath(it) }

        assertEquals(listOf("oebps/chapter1.xhtml"), orderedPaths)
    }

    @Test
    fun buildFallbackChaptersSuppressesNavigationFilteringWhenTooAggressive() {
        val tocLinks =
            (1..18).joinToString(separator = "") { index ->
                "<a href=\"chapter$index.xhtml\">Chapter $index</a><br/>"
            }
        val entries =
            linkedMapOf(
                "oebps/toc.xhtml" to "<html><body><h1>Table of Contents</h1>$tocLinks</body></html>".toByteArray(),
                "oebps/toc2.xhtml" to "<html><body><h1>Contents</h1>$tocLinks</body></html>".toByteArray(),
                "oebps/chapter1.xhtml" to "<html><body><p>Chapter one prose.</p></body></html>".toByteArray(),
            )

        val result =
            invokeBuildFallbackChaptersWithResult(
                zipTextEntries = entries,
                imageRelativePathByEpubPathLower = emptyMap(),
                preferredChapterPathsLower = emptyList(),
            )
        val parsed = result.chapters
        val orderedPaths = parsed.map { parsedChapterPath(it) }

        assertEquals(3, orderedPaths.size)
        assertTrue(orderedPaths.contains("oebps/chapter1.xhtml"))
        assertTrue(orderedPaths.contains("oebps/toc.xhtml"))
        assertTrue(orderedPaths.contains("oebps/toc2.xhtml"))
        assertTrue(result.navigationFilterSuppressed)
        assertEquals(0, result.navigationFilteredCount)
    }

    @Test
    fun buildFallbackChaptersKeepsTextHeavyLinkedChapter() {
        val links =
            (1..24).joinToString(separator = "") { index ->
                "<a href=\"note$index.xhtml\">$index</a> "
            }
        val words = (1..260).joinToString(separator = " ") { index -> "word$index" }
        val entries =
            linkedMapOf(
                "oebps/cover.xhtml" to "<html><body><img src=\"cover.jpg\"/></body></html>".toByteArray(),
                "oebps/chapter1.xhtml" to "<html><body>$links<p>$words</p></body></html>".toByteArray(),
            )

        val result =
            invokeBuildFallbackChaptersWithResult(
                zipTextEntries = entries,
                imageRelativePathByEpubPathLower = emptyMap(),
                preferredChapterPathsLower = emptyList(),
            )
        val orderedPaths = result.chapters.map { parsedChapterPath(it) }

        assertTrue(orderedPaths.contains("oebps/chapter1.xhtml"))
    }

    @Test
    fun buildFallbackChaptersIncludesPreferredXmlChapterPaths() {
        val entries =
            linkedMapOf(
                "oebps/chapter1.xml" to "<html><body><p>XML chapter content.</p></body></html>".toByteArray(),
                "oebps/chapter2.xhtml" to "<html><body><p>XHTML chapter content.</p></body></html>".toByteArray(),
            )

        val parsed =
            invokeBuildFallbackChapters(
                zipTextEntries = entries,
                imageRelativePathByEpubPathLower = emptyMap(),
                preferredChapterPathsLower = listOf("oebps/chapter1.xml"),
            )
        val orderedPaths = parsed.map { parsedChapterPath(it) }

        assertEquals("oebps/chapter1.xml", orderedPaths.first())
    }

    @Test
    fun resolveChapterPathsForReadingOrderPrefersSpineOnlyWhenPresent() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml" />
                <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml" />
                <item id="c3" href="text/ch3.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="c2" />
                <itemref idref="c1" />
              </spine>
            </package>
            """.trimIndent()

        val opfData: Any = invokeParseOpfData(xml)
        val available =
            linkedSetOf(
                "oebps/text/ch1.xhtml",
                "oebps/text/ch2.xhtml",
                "oebps/text/ch3.xhtml",
            )

        val resolved =
            invokeResolveChapterPathsForReadingOrder(
                opfData = opfData,
                opfDir = "oebps",
                availableEntriesLower = available,
                availableTextEntriesLower = available,
            )

        assertEquals(
            listOf(
                "oebps/text/ch2.xhtml",
                "oebps/text/ch1.xhtml",
            ),
            resolved,
        )
    }

    @Test
    fun resolveChapterPathsForReadingOrderPreservesDuplicateSpineItems() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml" />
                <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="c1" />
                <itemref idref="c2" />
                <itemref idref="c1" />
              </spine>
            </package>
            """.trimIndent()

        val opfData: Any = invokeParseOpfData(xml)
        val available =
            linkedSetOf(
                "oebps/text/ch1.xhtml",
                "oebps/text/ch2.xhtml",
            )

        val resolved =
            invokeResolveChapterPathsForReadingOrder(
                opfData = opfData,
                opfDir = "oebps",
                availableEntriesLower = available,
                availableTextEntriesLower = available,
            )

        assertEquals(
            listOf(
                "oebps/text/ch1.xhtml",
                "oebps/text/ch2.xhtml",
                "oebps/text/ch1.xhtml",
            ),
            resolved,
        )
    }

    @Test
    fun resolveChapterPathsForReadingOrderAppendsManifestRemainderWhenSpineIsPartial() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml" />
                <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml" />
                <item id="c3" href="text/ch3.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="c2" />
                <itemref idref="missing" />
              </spine>
            </package>
            """.trimIndent()

        val opfData: Any = invokeParseOpfData(xml)
        val available =
            linkedSetOf(
                "oebps/text/ch1.xhtml",
                "oebps/text/ch2.xhtml",
                "oebps/text/ch3.xhtml",
            )

        val resolved =
            invokeResolveChapterPathsForReadingOrder(
                opfData = opfData,
                opfDir = "oebps",
                availableEntriesLower = available,
                availableTextEntriesLower = available,
            )

        assertEquals(
            listOf(
                "oebps/text/ch2.xhtml",
                "oebps/text/ch1.xhtml",
                "oebps/text/ch3.xhtml",
            ),
            resolved,
        )
    }

    @Test
    fun resolveChapterPathsForReadingOrderSkipsNavDocuments() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav" />
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml" />
                <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="nav" />
                <itemref idref="c1" />
                <itemref idref="c2" />
              </spine>
            </package>
            """.trimIndent()

        val opfData: Any = invokeParseOpfData(xml)
        val available =
            linkedSetOf(
                "oebps/nav.xhtml",
                "oebps/text/ch1.xhtml",
                "oebps/text/ch2.xhtml",
            )

        val resolved =
            invokeResolveChapterPathsForReadingOrder(
                opfData = opfData,
                opfDir = "oebps",
                availableEntriesLower = available,
                availableTextEntriesLower = available,
            )

        assertEquals(
            listOf(
                "oebps/text/ch1.xhtml",
                "oebps/text/ch2.xhtml",
            ),
            resolved,
        )
    }

    @Test
    fun resolveChapterPathsForReadingOrderKeepsTocWhenNotMarkedNav() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="tocdoc" href="toc.xhtml" media-type="application/xhtml+xml" />
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="tocdoc" />
                <itemref idref="c1" />
              </spine>
            </package>
            """.trimIndent()

        val opfData: Any = invokeParseOpfData(xml)
        val available =
            linkedSetOf(
                "oebps/toc.xhtml",
                "oebps/text/ch1.xhtml",
            )

        val resolved =
            invokeResolveChapterPathsForReadingOrder(
                opfData = opfData,
                opfDir = "oebps",
                availableEntriesLower = available,
                availableTextEntriesLower = available,
            )

        assertEquals(
            listOf(
                "oebps/toc.xhtml",
                "oebps/text/ch1.xhtml",
            ),
            resolved,
        )
    }

    @Test
    fun resolveChapterPathsForReadingOrderFallsBackToXmlEntries() {
        val xml = "<package xmlns=\"http://www.idpf.org/2007/opf\"></package>"
        val opfData: Any = invokeParseOpfData(xml)
        val available =
            linkedSetOf(
                "oebps/chapter1.xml",
                "oebps/chapter2.xml",
                "meta-inf/container.xml",
            )

        val resolved =
            invokeResolveChapterPathsForReadingOrder(
                opfData = opfData,
                opfDir = "oebps",
                availableEntriesLower = available,
                availableTextEntriesLower = available,
            )

        assertEquals(
            listOf(
                "oebps/chapter1.xml",
                "oebps/chapter2.xml",
            ),
            resolved,
        )
    }

    @Test
    fun resolveZipEntryKeyDoesNotFallbackToRootForRelativeHref() {
        val resolved =
            invokeResolveZipEntryKey(
                baseDir = "oebps",
                rawHref = "text/chapter1.xhtml",
                availableEntriesLower = setOf("text/chapter1.xhtml"),
            )

        assertNull(resolved)
    }

    @Test
    fun resolveZipEntryKeyFallsBackToRootForAbsoluteHref() {
        val resolved =
            invokeResolveZipEntryKey(
                baseDir = "oebps",
                rawHref = "/text/chapter1.xhtml",
                availableEntriesLower = setOf("text/chapter1.xhtml"),
            )

        assertEquals("text/chapter1.xhtml", resolved)
    }

    @Test
    fun selectFallbackCoverPathPrefersCoverNamedImage() {
        val candidates =
            linkedSetOf(
                "images/illustration.jpg",
                "images/front-matter.png",
                "images/cover-art.webp",
            )

        val selected = invokeSelectFallbackCoverPath(candidates)

        assertEquals("images/cover-art.webp", selected)
    }

    @Test
    fun rewriteHtmlAnchorHrefsPreservesFragmentOnInternalLinks() {
        val html = "<p><a href=\"chapter2.xhtml#section-3\">Jump</a></p>"

        val rewritten =
            invokeRewriteHtmlAnchorHrefs(
                html = html,
                baseDir = "oebps",
                chapterIndexByPathLower = mapOf("oebps/chapter2.xhtml" to 5),
                currentChapterPath = "oebps/chapter1.xhtml",
            )

        assertTrue(rewritten.contains("kairo://chapter/5#section-3"))
    }

    @Test
    fun rewriteHtmlImageSrcsPreservesSvgFragmentAndRewritesSrcset() {
        val html =
            """
            <picture>
              <source srcset="images/cover-small.jpg 1x, images/cover-large.jpg 2x" />
              <img src="images/cover.svg#icon-main"/>
            </picture>
            """.trimIndent()
        val images =
            mapOf(
                "oebps/images/cover-small.jpg" to "kairo_epub_assets/book/images/small.jpg",
                "oebps/images/cover-large.jpg" to "kairo_epub_assets/book/images/large.jpg",
                "oebps/images/cover.svg" to "kairo_epub_assets/book/images/cover.svg",
            )

        val rewritten: String = parser.callPrivate("rewriteHtmlImageSrcs", html, "oebps", images)

        assertTrue(rewritten.contains("kairo_epub_assets/book/images/cover.svg#icon-main"))
        assertTrue(rewritten.contains("kairo_epub_assets/book/images/small.jpg 1x"))
        assertTrue(rewritten.contains("kairo_epub_assets/book/images/large.jpg 2x"))
    }

    @Test
    fun extractImageSrcsIncludesNoscriptAndSrcsetCandidates() {
        val html =
            """
            <picture>
              <source srcset="images/a.jpg 1x, images/b.jpg 2x" />
            </picture>
            <noscript><img src="images/fallback.jpg" /></noscript>
            """.trimIndent()

        val srcs: List<String> = parser.callPrivate("extractImageSrcs", html)

        assertTrue(srcs.contains("images/a.jpg"))
        assertTrue(srcs.contains("images/b.jpg"))
        assertTrue(srcs.contains("images/fallback.jpg"))
    }

    @Test
    fun stripNoiseTitleBlocksOnlyRemovesLeadingNoiseLabels() {
        val html =
            """
            <h1>chapter-0001.xhtml</h1>
            <p>Real chapter opening.</p>
            <h2>section-0002.xhtml</h2>
            """.trimIndent()

        val stripped: String = parser.callPrivate("stripNoiseTitleBlocks", html)

        assertFalse(stripped.contains("<h1>chapter-0001.xhtml</h1>"))
        assertTrue(stripped.contains("<p>Real chapter opening.</p>"))
        assertTrue(stripped.contains("<h2>section-0002.xhtml</h2>"))
    }

    private fun invokeResolveOpfPath(
        rawPath: String?,
        availableEntriesLower: Set<String>,
    ): String? {
        val method =
            EpubBookParser::class.java.getDeclaredMethod(
                "resolveOpfPath",
                String::class.java,
                Set::class.java,
            )
        method.isAccessible = true
        return method.invoke(parser, rawPath, availableEntriesLower) as String?
    }

    private fun invokeSelectBestOpfPath(
        containerCandidates: List<String>,
        zipEntryNamesLower: Set<String>,
        zipTextEntries: Map<String, ByteArray>,
    ): String? {
        val method =
            EpubBookParser::class.java.getDeclaredMethod(
                "selectBestOpf",
                List::class.java,
                Set::class.java,
                Map::class.java,
            )
        method.isAccessible = true
        val selection = method.invoke(parser, containerCandidates, zipEntryNamesLower, zipTextEntries)
        val pathField = selection.javaClass.getDeclaredField("path").apply { isAccessible = true }
        return pathField.get(selection) as String?
    }

    private fun invokeRewriteHtmlAnchorHrefs(
        html: String,
        baseDir: String,
        chapterIndexByPathLower: Map<String, Int>,
        currentChapterPath: String?,
    ): String {
        val method =
            EpubBookParser::class.java.getDeclaredMethod(
                "rewriteHtmlAnchorHrefs",
                String::class.java,
                String::class.java,
                Map::class.java,
                String::class.java,
            )
        method.isAccessible = true
        return method.invoke(
            parser,
            html,
            baseDir,
            chapterIndexByPathLower,
            currentChapterPath,
        ) as String
    }

    private fun invokeBuildFallbackChapters(
        zipTextEntries: Map<String, ByteArray>,
        imageRelativePathByEpubPathLower: Map<String, String>,
    ): List<Any> {
        return invokeBuildFallbackChapters(
            zipTextEntries = zipTextEntries,
            imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
            preferredChapterPathsLower = emptyList(),
        )
    }

    private fun invokeBuildFallbackChapters(
        zipTextEntries: Map<String, ByteArray>,
        imageRelativePathByEpubPathLower: Map<String, String>,
        preferredChapterPathsLower: List<String>,
    ): List<Any> {
        return invokeBuildFallbackChaptersWithResult(
            zipTextEntries = zipTextEntries,
            imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
            preferredChapterPathsLower = preferredChapterPathsLower,
        ).chapters
    }

    private data class BuildFallbackResultSnapshot(
        val chapters: List<Any>,
        val navigationFilteredCount: Int,
        val navigationFilterSuppressed: Boolean,
    )

    private fun invokeBuildFallbackChaptersWithResult(
        zipTextEntries: Map<String, ByteArray>,
        imageRelativePathByEpubPathLower: Map<String, String>,
        preferredChapterPathsLower: List<String>,
    ): BuildFallbackResultSnapshot {
        val method =
            EpubBookParser::class.java.getDeclaredMethod(
                "buildFallbackChaptersWithResult",
                Map::class.java,
                Map::class.java,
                List::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(
            parser,
            zipTextEntries,
            imageRelativePathByEpubPathLower,
            preferredChapterPathsLower,
        )
        val resultClass = result.javaClass
        val chaptersRaw =
            resultClass.getDeclaredField("chapters").apply { isAccessible = true }.get(result) as? List<*>
                ?: emptyList<Any>()
        val chapters = chaptersRaw.filterNotNull()
        val filteredCount =
            resultClass
                .getDeclaredField("navigationFilteredCount")
                .apply { isAccessible = true }
                .getInt(result)
        val suppressed =
            resultClass
                .getDeclaredField("navigationFilterSuppressed")
                .apply { isAccessible = true }
                .getBoolean(result)
        return BuildFallbackResultSnapshot(chapters, filteredCount, suppressed)
    }

    private fun parsedChapterPath(parsedChapter: Any): String {
        val field = parsedChapter.javaClass.getDeclaredField("pathLower")
        field.isAccessible = true
        return field.get(parsedChapter) as String
    }

    private fun invokeResolveChapterPathsForReadingOrder(
        opfData: Any,
        opfDir: String,
        availableEntriesLower: Set<String>,
        availableTextEntriesLower: Set<String>,
    ): List<String> {
        val method =
            EpubBookParser::class.java.declaredMethods.first {
                it.name == "resolveChapterOrder" && it.parameterTypes.size == 4
            }
        method.isAccessible = true
        val result = method.invoke(
            parser,
            opfData,
            opfDir,
            availableEntriesLower,
            availableTextEntriesLower,
        )
        val pathsField = result.javaClass.getDeclaredField("paths").apply { isAccessible = true }
        val pathsRaw = pathsField.get(result) as? List<*> ?: emptyList<Any>()
        return pathsRaw.mapNotNull { it as? String }
    }

    private fun invokeResolveZipEntryKey(
        baseDir: String,
        rawHref: String,
        availableEntriesLower: Set<String>,
    ): String? {
        val method =
            EpubBookParser::class.java.getDeclaredMethod(
                "resolveZipEntryKey",
                String::class.java,
                String::class.java,
                Set::class.java,
            )
        method.isAccessible = true
        return method.invoke(parser, baseDir, rawHref, availableEntriesLower) as String?
    }

    private fun invokeParseOpfFileWithResultUsedLenient(xml: String): Boolean {
        val method =
            EpubBookParser::class.java.getDeclaredMethod(
                "parseOpfFileWithResult",
                String::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(parser, xml)
        val field = result.javaClass.getDeclaredField("usedLenientFallback")
        field.isAccessible = true
        return field.getBoolean(result)
    }

    private fun invokeParseOpfData(xml: String): Any {
        val method =
            EpubBookParser::class.java.getDeclaredMethod(
                "parseOpfFileWithResult",
                String::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(parser, xml)
        val field = result.javaClass.getDeclaredField("opfData")
        field.isAccessible = true
        return field.get(result)
    }

    private fun invokeParseContainerXmlWithResult(xml: String): Pair<String, Boolean> {
        val method =
            EpubBookParser::class.java.getDeclaredMethod(
                "parseContainerXmlWithResult",
                String::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(parser, xml)
        val resultClass = result.javaClass
        val pathField = resultClass.getDeclaredField("path").apply { isAccessible = true }
        val fallbackField =
            resultClass.getDeclaredField("usedLenientFallback").apply { isAccessible = true }
        return (pathField.get(result) as String) to fallbackField.getBoolean(result)
    }

    private fun invokeSelectFallbackCoverPath(
        imagePathsLower: Collection<String>,
    ): String? {
        val method =
            EpubBookParser::class.java.getDeclaredMethod(
                "selectFallbackCoverPath",
                Collection::class.java,
            )
        method.isAccessible = true
        return method.invoke(parser, imagePathsLower) as String?
    }
}
