package com.example.kairo.ui.reader

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
    onFocusChange: (Int) -> Unit,
    onChapterChange: (Int, Int?) -> Unit,
): ReaderNavigationState {
    val canGoPrevPage =
        if (isPagedChapter && pages.isNotEmpty()) {
            resolvedPageIndex > 0 || chapterIndex > 0
        } else {
            chapterIndex > 0
        }
    val canGoNextPage =
        if (isPagedChapter && pages.isNotEmpty()) {
            resolvedPageIndex < pages.lastIndex || chapterIndex < lastChapterIndex
        } else {
            chapterIndex < lastChapterIndex
        }
    val onPrevPage = {
        if (pages.isNotEmpty()) {
            if (resolvedPageIndex > 0) {
                onFocusChange(pages[resolvedPageIndex - 1].startTokenIndex)
            } else if (chapterIndex > 0) {
                onChapterChange(chapterIndex - 1, Int.MAX_VALUE)
            }
        } else if (chapterIndex > 0) {
            onChapterChange(chapterIndex - 1, 0)
        }
    }
    val onNextPage = {
        if (pages.isNotEmpty()) {
            if (resolvedPageIndex < pages.lastIndex) {
                onFocusChange(pages[resolvedPageIndex + 1].startTokenIndex)
            } else if (chapterIndex < lastChapterIndex) {
                onChapterChange(chapterIndex + 1, 0)
            }
        } else if (chapterIndex < lastChapterIndex) {
            onChapterChange(chapterIndex + 1, 0)
        }
    }

    return ReaderNavigationState(
        canGoPrevPage = canGoPrevPage,
        canGoNextPage = canGoNextPage,
        onPrevPage = onPrevPage,
        onNextPage = onNextPage,
    )
}
