package com.example.kairo.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.nearestWordIndex

internal data class ReaderRenderState(
    val blocks: List<ReaderBlock>,
    val tokens: List<Token>,
    val pages: List<ChapterPage>,
    val wordCountByToken: IntArray?,
    val totalChapterWords: Int,
    val firstWordIndex: Int,
    val imagePaths: List<String>,
    val isCoverChapter: Boolean,
    val safeFocusIndex: Int,
    val isPagedChapter: Boolean,
    val resolvedPageIndex: Int,
    val currentPage: ChapterPage?,
    val fullScreenTitlePageImagePath: String?,
    val headerCarouselImages: List<String>,
    val displayBlocks: List<ReaderBlock>,
    val focusBlockIndex: Int,
    val showHeaderCarousel: Boolean,
    val listHeaderCount: Int,
    val focusListIndex: Int,
    val listStateKey: String,
)

@Composable
internal fun rememberReaderRenderState(
    chapterIndex: Int,
    focusIndex: Int,
    coverImage: ByteArray?,
    chapterData: ChapterData?,
): ReaderRenderState {
    val blocks = chapterData?.blocks.orEmpty()
    val tokens = chapterData?.tokens.orEmpty()
    val pages = chapterData?.pages.orEmpty()
    val wordCountByToken = chapterData?.wordCountByToken
    val totalChapterWords = chapterData?.totalWords ?: 0
    val firstWordIndex = chapterData?.firstWordIndex ?: -1
    val imagePaths = chapterData?.imagePaths.orEmpty()

    val isCoverChapter = chapterIndex == 0 && coverImage != null && coverImage.isNotEmpty()
    val safeFocusIndex =
        remember(tokens, focusIndex) {
            if (tokens.isEmpty()) {
                0
            } else {
                tokens.nearestWordIndex(focusIndex).coerceIn(0, tokens.lastIndex)
            }
        }
    val isPagedChapter = pages.isNotEmpty()
    val resolvedPageIndex =
        remember(pages, safeFocusIndex) {
            if (pages.isEmpty()) {
                -1
            } else {
                val index =
                    pages.indexOfFirst { page ->
                        safeFocusIndex in page.startTokenIndex..page.endTokenIndex
                    }
                if (index >= 0) index else 0
            }
        }
    val currentPage =
        remember(pages, resolvedPageIndex) {
            if (resolvedPageIndex >= 0) pages.getOrNull(resolvedPageIndex) else null
        }

    val fullScreenTitlePageImagePath =
        remember(chapterIndex, isCoverChapter, imagePaths, totalChapterWords) {
            if (imagePaths.isEmpty()) return@remember null

            when {
                // If we already render a metadata cover image, the first extracted image in chapter 0 is often a duplicate
                // cover. Prefer the second image as the title page (do not fall back to the first, to avoid rendering the
                // same cover twice).
                chapterIndex == 0 && isCoverChapter -> imagePaths.getOrNull(1)

                // Some EPUBs store the title page as a single-image chapter (often chapter 1).
                chapterIndex == 1 && imagePaths.size == 1 -> imagePaths.first()

                // If we don't have a metadata cover, chapter 0's first image is usually the title page.
                chapterIndex == 0 && !isCoverChapter -> imagePaths.first()

                // Fallback: if the chapter has no words and only a single image, treat it as a full-page image chapter.
                imagePaths.size == 1 && totalChapterWords == 0 -> imagePaths.first()

                else -> null
            }
        }

    val headerCarouselImages =
        remember(chapterIndex, isCoverChapter, fullScreenTitlePageImagePath, imagePaths) {
            if (imagePaths.isEmpty()) return@remember emptyList()

            when {
                chapterIndex == 0 && isCoverChapter -> {
                    val skip =
                        when {
                            fullScreenTitlePageImagePath == null -> 1
                            imagePaths.size >= 2 -> 2
                            else -> 1
                        }
                    imagePaths.drop(skip)
                }
                fullScreenTitlePageImagePath != null -> imagePaths.drop(1)
                else -> imagePaths
            }
        }

    val excludedInlineImages =
        remember(
            isCoverChapter,
            fullScreenTitlePageImagePath,
            imagePaths,
        ) {
            buildSet {
                if (isCoverChapter) {
                    imagePaths.firstOrNull()?.let { add(it) }
                }
                fullScreenTitlePageImagePath?.let { add(it) }
            }
        }
    val visibleBlocks =
        remember(blocks, excludedInlineImages) {
            if (excludedInlineImages.isEmpty()) {
                blocks
            } else {
                blocks.filterNot { block ->
                    block is ReaderImageBlock && block.imagePath in excludedInlineImages
                }
            }
        }
    val displayBlocks =
        remember(visibleBlocks, currentPage) {
            if (currentPage == null) {
                visibleBlocks
            } else {
                sliceBlocksForPage(
                    blocks = visibleBlocks,
                    pageStart = currentPage.startTokenIndex,
                    pageEnd = currentPage.endTokenIndex,
                )
            }
        }
    val focusBlockIndex =
        remember(focusIndex, displayBlocks) {
            if (displayBlocks.isEmpty()) {
                0
            } else {
                displayBlocks
                    .indexOfFirst { block ->
                        val paragraph =
                            (block as? ReaderParagraphBlock)?.paragraph ?: return@indexOfFirst false
                        val endIndex = paragraph.startIndex + paragraph.tokens.size - 1
                        focusIndex in paragraph.startIndex..endIndex
                    }.coerceAtLeast(0)
            }
        }
    val showHeaderCarousel =
        remember(headerCarouselImages, displayBlocks, resolvedPageIndex, isPagedChapter) {
            headerCarouselImages.isNotEmpty() &&
                displayBlocks.none { it is ReaderImageBlock } &&
                (!isPagedChapter || resolvedPageIndex <= 0)
        }
    val listHeaderCount =
        remember(
            isCoverChapter,
            fullScreenTitlePageImagePath,
            showHeaderCarousel,
            resolvedPageIndex,
            isPagedChapter,
        ) {
            if (isPagedChapter && resolvedPageIndex > 0) {
                0
            } else {
                (if (isCoverChapter) 1 else 0) +
                    (if (fullScreenTitlePageImagePath != null) 1 else 0) +
                    (if (showHeaderCarousel) 1 else 0)
            }
        }
    val focusListIndex = remember(focusBlockIndex, listHeaderCount) {
        focusBlockIndex + listHeaderCount
    }

    val listStateKey =
        remember(chapterIndex, chapterData, resolvedPageIndex, isPagedChapter) {
            if (isPagedChapter && resolvedPageIndex >= 0) {
                "$chapterIndex-${chapterData?.hashCode() ?: 0}-p$resolvedPageIndex"
            } else {
                "$chapterIndex-${chapterData?.hashCode() ?: 0}"
            }
        }

    return ReaderRenderState(
        blocks = blocks,
        tokens = tokens,
        pages = pages,
        wordCountByToken = wordCountByToken,
        totalChapterWords = totalChapterWords,
        firstWordIndex = firstWordIndex,
        imagePaths = imagePaths,
        isCoverChapter = isCoverChapter,
        safeFocusIndex = safeFocusIndex,
        isPagedChapter = isPagedChapter,
        resolvedPageIndex = resolvedPageIndex,
        currentPage = currentPage,
        fullScreenTitlePageImagePath = fullScreenTitlePageImagePath,
        headerCarouselImages = headerCarouselImages,
        displayBlocks = displayBlocks,
        focusBlockIndex = focusBlockIndex,
        showHeaderCarousel = showHeaderCarousel,
        listHeaderCount = listHeaderCount,
        focusListIndex = focusListIndex,
        listStateKey = listStateKey,
    )
}
