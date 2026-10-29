package com.kairo.reader.ui.reader

internal data class ReaderNavigationState(
    val canGoPrevPage: Boolean,
    val canGoNextPage: Boolean,
    val onPrevPage: () -> Unit,
    val onNextPage: () -> Unit,
)

internal fun buildReaderNavigationState(
    pages: List<ChapterPage>,
    isPagedChapter: Boolean,
    resolvedPageIndex: Int,
    chapterIndex: Int,
    lastChapterIndex: Int,
    linearChapterIndices: List<Int> = (0..lastChapterIndex).toList(),
    auxiliaryReturn: ReaderReturnPosition? = null,
    onPageChange: (ChapterPage) -> Unit,
    onChapterChange: (Int, Int?) -> Unit,
): ReaderNavigationState {
    val previousChapter = auxiliaryReturn?.chapterIndex ?: linearChapterIndices.lastOrNull { it < chapterIndex }
    val nextChapter = linearChapterIndices.firstOrNull { it > chapterIndex }
    val canGoPrevPage =
        if (isPagedChapter && pages.isNotEmpty()) {
            resolvedPageIndex > 0 || previousChapter != null
        } else {
            previousChapter != null
        }
    val canGoNextPage =
        if (isPagedChapter && pages.isNotEmpty()) {
            resolvedPageIndex < pages.lastIndex || nextChapter != null
        } else {
            nextChapter != null
        }
    val onPrevPage = {
        if (pages.isNotEmpty()) {
            if (resolvedPageIndex > 0) {
                onPageChange(pages[resolvedPageIndex - 1])
            } else if (previousChapter != null) {
                onChapterChange(previousChapter, auxiliaryReturn?.focusIndex ?: Int.MAX_VALUE)
            }
        } else if (previousChapter != null) {
            onChapterChange(previousChapter, auxiliaryReturn?.focusIndex ?: Int.MAX_VALUE)
        }
    }
    val onNextPage = {
        if (pages.isNotEmpty()) {
            if (resolvedPageIndex < pages.lastIndex) {
                onPageChange(pages[resolvedPageIndex + 1])
            } else if (nextChapter != null) {
                onChapterChange(nextChapter, 0)
            }
        } else if (nextChapter != null) {
            onChapterChange(nextChapter, 0)
        }
    }

    return ReaderNavigationState(
        canGoPrevPage = canGoPrevPage,
        canGoNextPage = canGoNextPage,
        onPrevPage = onPrevPage,
        onNextPage = onNextPage,
    )
}
