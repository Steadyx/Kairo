package com.kairo.reader.data.books

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.data.books.epub.EpubNavigationReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTableOfContentsResolverTest {
    private val contentRewriter = EpubContentRewriter()
    private val resolver = EpubTableOfContentsResolver(contentRewriter)

    @Test
    fun resolvesMultipleAuthoredEntriesInsideOneSpineDocument() {
        val content =
            contentRewriter.extractPlainTextWithAnchors(
                """
                <html><body>
                  <section id="chapter-one"><h1>The Arrival</h1><p>First chapter.</p></section>
                  <section id="chapter-two"><h1>The Crossing</h1><p>Second chapter.</p></section>
                </body></html>
                """.trimIndent(),
            )
        val chapter =
            ParsedChapter(
                pathLower = "oebps/text/story.xhtml",
                baseDir = "oebps/text",
                chapter =
                Chapter(
                    index = 0,
                    title = "Story",
                    htmlContent = "",
                    plainText = content.text,
                ),
                anchorOffsets = content.anchorOffsets,
            )
        val references =
            listOf(
                EpubNavigationReference("The Arrival", depth = 0, href = "text/story.xhtml#chapter-one"),
                EpubNavigationReference("The Crossing", depth = 0, href = "text/story.xhtml#chapter-two"),
                EpubNavigationReference("Part Two", depth = 0, href = null),
            )

        val entries =
            resolver.resolve(
                references = references,
                navigationPathLower = "oebps/nav.xhtml",
                chapters = listOf(chapter),
            )

        assertEquals(listOf("The Arrival", "The Crossing", "Part Two"), entries.map { it.label })
        assertEquals(0, entries[0].target?.chapterIndex)
        assertEquals(0, entries[0].target?.characterOffset)
        val secondOffset = requireNotNull(entries[1].target).characterOffset
        assertTrue(secondOffset > 0)
        assertTrue(content.text.substring(secondOffset).startsWith("The Crossing"))
        assertNull(entries[2].target)
    }
}
