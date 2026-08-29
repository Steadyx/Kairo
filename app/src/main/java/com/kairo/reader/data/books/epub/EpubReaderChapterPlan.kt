package com.kairo.reader.data.books.epub

internal data class EpubReaderChapterPlan(val paths: List<String>, val preservedNavigationPaths: Set<String>,)

internal object EpubReaderChapterPlanner {
    fun create(
        readingOrderPaths: List<String>,
        spinePaths: Set<String>,
        navigationPath: String?,
    ): EpubReaderChapterPlan {
        val navigationDocumentPath =
            navigationPath
                ?.takeIf(EpubChapterOrdering::isHtmlEntry)
        val paths =
            if (navigationDocumentPath == null || navigationDocumentPath in readingOrderPaths) {
                readingOrderPaths
            } else {
                listOf(navigationDocumentPath) + readingOrderPaths
            }
        val preservedNavigationPaths =
            navigationDocumentPath?.let { spinePaths + it } ?: spinePaths

        return EpubReaderChapterPlan(
            paths = paths,
            preservedNavigationPaths = preservedNavigationPaths,
        )
    }
}
