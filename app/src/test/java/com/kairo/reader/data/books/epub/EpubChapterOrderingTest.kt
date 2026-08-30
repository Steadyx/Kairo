package com.kairo.reader.data.books.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubChapterOrderingTest {
    @Test
    fun resolveChapterOrderPreservesDuplicateSpineEntries() {
        val opfData =
            OpfData(
                title = "Test",
                authors = emptyList(),
                languageTag = null,
                coverHref = null,
                manifest =
                mapOf(
                    "c1" to "text/ch1.xhtml",
                    "c2" to "text/ch2.xhtml",
                ),
                manifestItems =
                listOf(
                    ManifestItem(
                        id = "c1",
                        href = "text/ch1.xhtml",
                        mediaType = "application/xhtml+xml",
                        properties = emptySet(),
                    ),
                    ManifestItem(
                        id = "c2",
                        href = "text/ch2.xhtml",
                        mediaType = "application/xhtml+xml",
                        properties = emptySet(),
                    ),
                ),
                spineItems =
                listOf(
                    SpineItem("c1"),
                    SpineItem("c2"),
                    SpineItem("c1"),
                ),
            )

        val resolved =
            EpubChapterOrdering.resolveChapterOrder(
                opfData = opfData,
                opfDir = "oebps",
                availableEntriesLower = setOf("oebps/text/ch1.xhtml", "oebps/text/ch2.xhtml"),
                availableTextEntriesLower = setOf("oebps/text/ch1.xhtml", "oebps/text/ch2.xhtml"),
            )

        assertEquals(
            listOf(
                "oebps/text/ch1.xhtml",
                "oebps/text/ch2.xhtml",
                "oebps/text/ch1.xhtml",
            ),
            resolved.paths,
        )
    }

    @Test
    fun resolveChapterOrderIncludesNonLinearContentsButSkipsOtherAuxiliaryDocuments() {
        val opfData =
            OpfData(
                title = "Test",
                authors = emptyList(),
                languageTag = null,
                coverHref = null,
                manifest =
                mapOf(
                    "contents" to "text/contents01.xhtml",
                    "notes" to "text/notes.xhtml",
                    "chapter" to "text/chapter.xhtml",
                ),
                manifestItems =
                listOf(
                    ManifestItem(
                        id = "contents",
                        href = "text/contents01.xhtml",
                        mediaType = "application/xhtml+xml",
                        properties = emptySet(),
                    ),
                    ManifestItem(
                        id = "notes",
                        href = "text/notes.xhtml",
                        mediaType = "application/xhtml+xml",
                        properties = emptySet(),
                    ),
                    ManifestItem(
                        id = "chapter",
                        href = "text/chapter.xhtml",
                        mediaType = "application/xhtml+xml",
                        properties = emptySet(),
                    ),
                ),
                spineItems =
                listOf(
                    SpineItem(idref = "contents", isLinear = false),
                    SpineItem(idref = "notes", isLinear = false),
                    SpineItem(idref = "chapter"),
                ),
            )
        val available =
            setOf(
                "oebps/text/contents01.xhtml",
                "oebps/text/notes.xhtml",
                "oebps/text/chapter.xhtml",
            )

        val resolved =
            EpubChapterOrdering.resolveChapterOrder(
                opfData = opfData,
                opfDir = "oebps",
                availableEntriesLower = available,
                availableTextEntriesLower = available,
            )

        assertEquals(
            listOf(
                "oebps/text/contents01.xhtml",
                "oebps/text/chapter.xhtml",
            ),
            resolved.paths,
        )
        assertEquals(resolved.paths.toSet(), resolved.resolvedSpinePaths)
    }
}
