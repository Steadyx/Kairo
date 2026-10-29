package com.kairo.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderScreenNavigationTest {
    @Test
    fun sequentialNavigationSkipsAuxiliaryChapters() {
        var chapterChange: Int? = null
        val navigation = buildReaderNavigationState(
            pages = emptyList(),
            isPagedChapter = false,
            resolvedPageIndex = -1,
            chapterIndex = 0,
            lastChapterIndex = 3,
            linearChapterIndices = listOf(0, 2),
            onPageChange = {},
            onChapterChange = { chapter, _ -> chapterChange = chapter },
        )
        navigation.onNextPage()
        assertEquals(2, chapterChange)
        val last = buildReaderNavigationState(
            pages = emptyList(),
            isPagedChapter = false,
            resolvedPageIndex = -1,
            chapterIndex = 2,
            lastChapterIndex = 3,
            linearChapterIndices = listOf(0, 2),
            onPageChange = {},
            onChapterChange = { chapter, _ -> chapterChange = chapter },
        )
        assertEquals(false, last.canGoNextPage)
        last.onNextPage()
        assertEquals(2, chapterChange)
    }

    @Test
    fun previousFromLinkedNoteReturnsToSourceFocus() {
        var destination: Pair<Int, Int?>? = null
        val navigation = buildReaderNavigationState(
            pages = emptyList(),
            isPagedChapter = false,
            resolvedPageIndex = -1,
            chapterIndex = 5,
            lastChapterIndex = 5,
            linearChapterIndices = listOf(0, 1, 2, 3),
            auxiliaryReturn = ReaderReturnPosition(1, 42, null),
            onPageChange = {},
            onChapterChange = { chapter, focus -> destination = chapter to focus },
        )
        navigation.onPrevPage()
        assertEquals(1 to 42, destination)
    }

    @Test
    fun nextPage_advancesWithinCurrentChapterBeforeChangingChapter() {
        var pageChanged: ChapterPage? = null
        var chapterChange: Pair<Int, Int?>? = null
        val pages =
            listOf(
                ChapterPage(index = 0, startTokenIndex = 0, endTokenIndex = 9, wordCount = 10),
                ChapterPage(index = 1, startTokenIndex = 10, endTokenIndex = 19, wordCount = 10),
            )

        val navigation =
            buildReaderNavigationState(
                pages = pages,
                isPagedChapter = true,
                resolvedPageIndex = 0,
                chapterIndex = 2,
                lastChapterIndex = 4,
                onPageChange = { pageChanged = it },
                onChapterChange = { chapter, focus -> chapterChange = chapter to focus },
            )

        navigation.onNextPage()

        assertEquals(pages[1], pageChanged)
        assertEquals(null, chapterChange)
    }

    @Test
    fun previousPage_movesToPriorVisualPageWithinCurrentChapter() {
        var pageChanged: ChapterPage? = null
        var chapterChange: Pair<Int, Int?>? = null
        val pages =
            listOf(
                ChapterPage(index = 0, startTokenIndex = 0, endTokenIndex = 9, wordCount = 10),
                ChapterPage(
                    index = 1,
                    startTokenIndex = 9,
                    endTokenIndex = 9,
                    wordCount = 0,
                    kind = ChapterPageKind.IMAGE,
                    imagePath = "image.jpg",
                    imageIndex = 0,
                    focusTokenIndex = 9,
                ),
                ChapterPage(index = 2, startTokenIndex = 10, endTokenIndex = 19, wordCount = 10),
            )

        val navigation =
            buildReaderNavigationState(
                pages = pages,
                isPagedChapter = true,
                resolvedPageIndex = 2,
                chapterIndex = 2,
                lastChapterIndex = 4,
                onPageChange = { pageChanged = it },
                onChapterChange = { chapter, focus -> chapterChange = chapter to focus },
            )

        navigation.onPrevPage()

        assertEquals(pages[1], pageChanged)
        assertEquals(null, chapterChange)
    }

    @Test
    fun nextPage_changesChapterAtEndOfPagedChapter() {
        var chapterChange: Pair<Int, Int?>? = null
        val pages =
            listOf(
                ChapterPage(index = 0, startTokenIndex = 0, endTokenIndex = 9, wordCount = 10),
                ChapterPage(index = 1, startTokenIndex = 10, endTokenIndex = 19, wordCount = 10),
            )

        val navigation =
            buildReaderNavigationState(
                pages = pages,
                isPagedChapter = true,
                resolvedPageIndex = 1,
                chapterIndex = 2,
                lastChapterIndex = 4,
                onPageChange = {},
                onChapterChange = { chapter, focus -> chapterChange = chapter to focus },
            )

        navigation.onNextPage()

        assertEquals(3 to 0, chapterChange)
    }

    @Test
    fun previousPage_changesToPreviousChapterEndAtStartOfPagedChapter() {
        var chapterChange: Pair<Int, Int?>? = null
        val pages =
            listOf(
                ChapterPage(index = 0, startTokenIndex = 0, endTokenIndex = 9, wordCount = 10),
                ChapterPage(index = 1, startTokenIndex = 10, endTokenIndex = 19, wordCount = 10),
            )

        val navigation =
            buildReaderNavigationState(
                pages = pages,
                isPagedChapter = true,
                resolvedPageIndex = 0,
                chapterIndex = 2,
                lastChapterIndex = 4,
                onPageChange = {},
                onChapterChange = { chapter, focus -> chapterChange = chapter to focus },
            )

        navigation.onPrevPage()

        assertEquals(1 to Int.MAX_VALUE, chapterChange)
    }

    @Test
    fun previousPage_changesToPreviousChapterEndWhenCurrentChapterHasNoPages() {
        var chapterChange: Pair<Int, Int?>? = null

        val navigation =
            buildReaderNavigationState(
                pages = emptyList(),
                isPagedChapter = false,
                resolvedPageIndex = -1,
                chapterIndex = 2,
                lastChapterIndex = 4,
                onPageChange = {},
                onChapterChange = { chapter, focus -> chapterChange = chapter to focus },
            )

        navigation.onPrevPage()

        assertEquals(1 to Int.MAX_VALUE, chapterChange)
    }
}
