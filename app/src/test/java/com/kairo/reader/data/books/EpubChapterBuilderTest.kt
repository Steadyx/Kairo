package com.kairo.reader.data.books

import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.tokenization.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class EpubChapterBuilderTest : EpubParserTestBase() {
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
    fun buildFallbackChaptersPreservesAuthoredChapterHeading() {
        val entries =
            linkedMapOf(
                "oebps/chapter1.xhtml" to
                    """
                    <html>
                        <body>
                            <div class="chapter">
                                <h1>Chapter 1</h1>
                                <p>Opening line.</p>
                            </div>
                        </body>
                    </html>
                    """.trimIndent().toByteArray(),
            )

        val chapter = parsedChapter(invokeBuildFallbackChapters(entries, emptyMap()).single())

        assertEquals("Chapter 1", chapter.title)
        assertEquals("Chapter 1\n\nOpening line.", chapter.plainText)
        assertTrue(chapter.htmlContent.contains("Chapter 1"))
    }

    @Test
    fun buildFallbackChaptersPreservesHeadingThatMatchesMetadataTitle() {
        val entries =
            linkedMapOf(
                "oebps/preface.xhtml" to
                    """
                    <html>
                        <head><title>Preface</title></head>
                        <body>
                            <h2>Preface</h2>
                            <p>Before the story.</p>
                        </body>
                    </html>
                    """.trimIndent().toByteArray(),
            )

        val chapter = parsedChapter(invokeBuildFallbackChapters(entries, emptyMap()).single())

        assertEquals("Preface", chapter.title)
        assertEquals("Preface\n\nBefore the story.", chapter.plainText)
        assertTrue(chapter.htmlContent.contains("Preface</h2>"))
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
                "oebps/toc.xhtml" to
                    "<html><body><p>Once upon a time we begin the story.</p></body></html>"
                        .toByteArray(),
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
    fun buildFallbackChaptersKeepsPublisherNavigationDocumentForReader() {
        val tocLinks =
            (1..14).joinToString(separator = "") { index ->
                "<a href=\"chapter$index.xhtml\">Chapter $index</a><br/>"
            }
        val entries =
            linkedMapOf(
                "oebps/nav.xhtml" to
                    "<html><body><nav><h1>Contents</h1>$tocLinks</nav></body></html>".toByteArray(),
                "oebps/chapter1.xhtml" to
                    "<html><body><p>Chapter one prose.</p></body></html>".toByteArray(),
            )

        val result =
            invokeBuildFallbackChaptersWithResult(
                zipTextEntries = entries,
                imageRelativePathByEpubPathLower = emptyMap(),
                preferredChapterPathsLower =
                listOf(
                    "oebps/nav.xhtml",
                    "oebps/chapter1.xhtml",
                ),
                preservedNavigationPathsLower = setOf("oebps/nav.xhtml"),
            )

        assertEquals(
            listOf(
                "oebps/nav.xhtml",
                "oebps/chapter1.xhtml",
            ),
            result.chapters.map(::parsedChapterPath),
        )
        assertEquals(0, result.navigationFilteredCount)
    }

    @Test
    fun canonicalNavigationOverrideDrivesReaderTextAndRetainsRewritableLinks() {
        val navigationDocument =
            """
            <html><body>
              <nav epub:type="toc">
                <h1>Contents</h1>
                <ol><li><a href="chapter1.xhtml">Chapter One</a></li></ol>
              </nav>
              <nav epub:type="page-list">
                <ol><li><a href="chapter1.xhtml#page-1">1</a></li></ol>
              </nav>
            </body></html>
            """.trimIndent()
        val canonicalNavigation =
            requireNotNull(EpubNavigationParser().parse(navigationDocument, isNcx = false).readerHtml)
        val entries =
            linkedMapOf(
                "oebps/nav.xhtml" to navigationDocument.toByteArray(),
                "oebps/chapter1.xhtml" to
                    "<html><body><h1>Chapter One</h1><p>Story.</p></body></html>".toByteArray(),
            )

        val result =
            invokeBuildFallbackChaptersWithResult(
                zipTextEntries = entries,
                imageRelativePathByEpubPathLower = emptyMap(),
                preferredChapterPathsLower = entries.keys.toList(),
                preservedNavigationPathsLower = setOf("oebps/nav.xhtml"),
                htmlOverridesByPathLower = mapOf("oebps/nav.xhtml" to canonicalNavigation),
            )
        val navigationChapter = parsedChapter(result.chapters.first())
        val rewrittenHtml =
            invokeRewriteHtmlAnchorHrefs(
                html = navigationChapter.htmlContent,
                baseDir = "oebps",
                chapterIndexByPathLower =
                result.chapters.associate { parsed ->
                    parsedChapterPath(parsed) to parsedChapter(parsed).index
                },
                currentChapterPath = "oebps/nav.xhtml",
            )
        val linkedTokens =
            Tokenizer().tokenize(navigationChapter.copy(htmlContent = rewrittenHtml))

        assertEquals("Contents\n\nChapter One", navigationChapter.plainText)
        assertTrue(navigationChapter.htmlContent.contains(EpubReaderNavigationContent.MARKER))
        assertFalse(navigationChapter.htmlContent.contains("page-1"))
        assertTrue(rewrittenHtml.contains("kairo://chapter/1"))
        assertTrue(
            linkedTokens
                .filter { token -> token.type == TokenType.WORD && token.text in setOf("Chapter", "One") }
                .all { token -> token.linkChapterIndex == 1 },
        )
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
}
