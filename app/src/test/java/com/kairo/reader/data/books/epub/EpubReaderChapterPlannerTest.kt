package com.kairo.reader.data.books.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubReaderChapterPlannerTest {
    @Test
    fun navigationDocumentOutsideSpineBecomesLeadingReaderPage() {
        val plan =
            EpubReaderChapterPlanner.create(
                readingOrderPaths =
                listOf(
                    "oebps/text/chapter1.xhtml",
                    "oebps/text/chapter2.xhtml",
                ),
                spinePaths =
                setOf(
                    "oebps/text/chapter1.xhtml",
                    "oebps/text/chapter2.xhtml",
                ),
                navigationPath = "oebps/nav.xhtml",
            )

        assertEquals(
            listOf(
                "oebps/nav.xhtml",
                "oebps/text/chapter1.xhtml",
                "oebps/text/chapter2.xhtml",
            ),
            plan.paths,
        )
        assertEquals(
            setOf(
                "oebps/nav.xhtml",
                "oebps/text/chapter1.xhtml",
                "oebps/text/chapter2.xhtml",
            ),
            plan.preservedNavigationPaths,
        )
    }

    @Test
    fun navigationDocumentAlreadyInSpineKeepsAuthoredPosition() {
        val readingOrder =
            listOf(
                "oebps/cover.xhtml",
                "oebps/nav.xhtml",
                "oebps/text/chapter1.xhtml",
            )

        val plan =
            EpubReaderChapterPlanner.create(
                readingOrderPaths = readingOrder,
                spinePaths = readingOrder.toSet(),
                navigationPath = "oebps/nav.xhtml",
            )

        assertEquals(readingOrder, plan.paths)
    }

    @Test
    fun epubTwoNcxRemainsMetadataOnly() {
        val readingOrder = listOf("oebps/text/chapter1.xhtml")

        val plan =
            EpubReaderChapterPlanner.create(
                readingOrderPaths = readingOrder,
                spinePaths = readingOrder.toSet(),
                navigationPath = "oebps/toc.ncx",
            )

        assertEquals(readingOrder, plan.paths)
        assertEquals(readingOrder.toSet(), plan.preservedNavigationPaths)
    }
}
