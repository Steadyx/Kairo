package com.kairo.reader.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.nearestWordIndex

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
    val listItemCount: Int,
    val focusListIndex: Int,
    val listStateKey: String,
)

private data class ReaderChapterSource(
    val blocks: List<ReaderBlock>,
    val tokens: List<Token>,
    val textPages: List<ChapterPage>,
    val wordCountByToken: IntArray?,
    val totalChapterWords: Int,
    val firstWordIndex: Int,
    val imagePaths: List<String>,
    val isCoverChapter: Boolean,
)

private data class ReaderImagePresentation(
    val fullScreenTitlePageImagePath: String?,
    val headerCarouselImages: List<String>,
    val visibleBlocks: List<ReaderBlock>,
)

private data class ReaderPagePresentation(
    val pages: List<ChapterPage>,
    val resolvedPageIndex: Int,
    val currentPage: ChapterPage?,
    val displayBlocks: List<ReaderBlock>,
    val focusBlockIndex: Int,
    val showHeaderCarousel: Boolean,
    val listHeaderCount: Int,
    val listItemCount: Int,
    val focusListIndex: Int,
    val listStateKey: String,
)

@Composable
internal fun rememberReaderRenderState(
    chapterIndex: Int,
    focusIndex: Int,
    pageIndexOverride: Int?,
    coverImage: ByteArray?,
    chapterData: ChapterData?,
): ReaderRenderState {
    val source = remember(chapterIndex, coverImage, chapterData) {
        buildReaderChapterSource(chapterIndex, coverImage, chapterData)
    }
    val safeFocusIndex =
        remember(source.tokens, focusIndex) {
            if (source.tokens.isEmpty()) {
                0
            } else {
                source.tokens.nearestWordIndex(focusIndex).coerceIn(0, source.tokens.lastIndex)
            }
        }
    val images = remember(source) { buildReaderImagePresentation(source) }
    val pagePresentation =
        remember(source, images, safeFocusIndex, pageIndexOverride, focusIndex, chapterIndex, chapterData) {
            buildReaderPagePresentation(
                source = source,
                images = images,
                safeFocusIndex = safeFocusIndex,
                pageIndexOverride = pageIndexOverride,
                focusIndex = focusIndex,
                chapterIndex = chapterIndex,
                chapterDataHash = chapterData?.hashCode() ?: 0,
            )
        }

    return ReaderRenderState(
        blocks = source.blocks,
        tokens = source.tokens,
        pages = pagePresentation.pages,
        wordCountByToken = source.wordCountByToken,
        totalChapterWords = source.totalChapterWords,
        firstWordIndex = source.firstWordIndex,
        imagePaths = source.imagePaths,
        isCoverChapter = source.isCoverChapter,
        safeFocusIndex = safeFocusIndex,
        isPagedChapter = pagePresentation.pages.isNotEmpty(),
        resolvedPageIndex = pagePresentation.resolvedPageIndex,
        currentPage = pagePresentation.currentPage,
        fullScreenTitlePageImagePath = images.fullScreenTitlePageImagePath,
        headerCarouselImages = images.headerCarouselImages,
        displayBlocks = pagePresentation.displayBlocks,
        focusBlockIndex = pagePresentation.focusBlockIndex,
        showHeaderCarousel = pagePresentation.showHeaderCarousel,
        listHeaderCount = pagePresentation.listHeaderCount,
        listItemCount = pagePresentation.listItemCount,
        focusListIndex = pagePresentation.focusListIndex,
        listStateKey = pagePresentation.listStateKey,
    )
}

private fun buildReaderChapterSource(
    chapterIndex: Int,
    coverImage: ByteArray?,
    chapterData: ChapterData?,
): ReaderChapterSource =
    ReaderChapterSource(
        blocks = chapterData?.blocks.orEmpty(),
        tokens = chapterData?.tokens.orEmpty(),
        textPages = chapterData?.pages.orEmpty(),
        wordCountByToken = chapterData?.wordCountByToken,
        totalChapterWords = chapterData?.totalWords ?: 0,
        firstWordIndex = chapterData?.firstWordIndex ?: -1,
        imagePaths = chapterData?.imagePaths.orEmpty(),
        isCoverChapter = chapterIndex == 0 && coverImage != null && coverImage.isNotEmpty(),
    )

private fun buildReaderImagePresentation(
    source: ReaderChapterSource,
): ReaderImagePresentation {
    val titlePage = resolveTitlePageImage(source)
    val inlineImages = source.blocks.filterIsInstance<ReaderImageBlock>().mapTo(mutableSetOf()) { it.imagePath }
    val headerImages = source.imagePaths.filterNot { it == titlePage || it in inlineImages }
    val visibleBlocks =
        source.blocks.filterNot { block ->
            block is ReaderImageBlock && block.imagePath == titlePage
        }
    return ReaderImagePresentation(titlePage, headerImages, visibleBlocks)
}

private fun resolveTitlePageImage(
    source: ReaderChapterSource,
): String? {
    if (source.totalChapterWords != 0 || source.blocks.count { it is ReaderImageBlock } != 1) return null
    return source.imagePaths.singleOrNull()
}

private fun buildReaderPagePresentation(
    source: ReaderChapterSource,
    images: ReaderImagePresentation,
    safeFocusIndex: Int,
    pageIndexOverride: Int?,
    focusIndex: Int,
    chapterIndex: Int,
    chapterDataHash: Int,
): ReaderPagePresentation {
    val pages = buildVisualChapterPages(source.textPages, images.visibleBlocks, source.tokens)
    val resolvedPageIndex = resolveReaderPageIndex(pages, safeFocusIndex, pageIndexOverride)
    val currentPage = pages.getOrNull(resolvedPageIndex)
    val displayBlocks =
        currentPage?.let { sliceBlocksForPage(images.visibleBlocks, it) } ?: images.visibleBlocks
    val focusBlockIndex = findFocusBlockIndex(displayBlocks, focusIndex)
    val showHeaderCarousel =
        images.headerCarouselImages.isNotEmpty() &&
            displayBlocks.none { it is ReaderImageBlock } &&
            (pages.isEmpty() || resolvedPageIndex <= 0)
    val listHeaderCount =
        if (pages.isNotEmpty() && resolvedPageIndex > 0) {
            0
        } else {
            listOf(
                source.isCoverChapter,
                images.fullScreenTitlePageImagePath != null,
                showHeaderCarousel,
            ).count { it }
        }
    return ReaderPagePresentation(
        pages = pages,
        resolvedPageIndex = resolvedPageIndex,
        currentPage = currentPage,
        displayBlocks = displayBlocks,
        focusBlockIndex = focusBlockIndex,
        showHeaderCarousel = showHeaderCarousel,
        listHeaderCount = listHeaderCount,
        listItemCount = listHeaderCount + displayBlocks.size,
        focusListIndex = focusBlockIndex + listHeaderCount,
        listStateKey = buildListStateKey(chapterIndex, chapterDataHash, resolvedPageIndex, pages.isNotEmpty()),
    )
}

private fun resolveReaderPageIndex(
    pages: List<ChapterPage>,
    safeFocusIndex: Int,
    pageIndexOverride: Int?,
): Int {
    if (pages.isEmpty()) return -1
    pageIndexOverride?.coerceIn(0, pages.lastIndex)?.let { return it }
    return pages.indexOfFirst { page ->
        page.kind == ChapterPageKind.TEXT &&
            safeFocusIndex in page.startTokenIndex..page.endTokenIndex
    }.coerceAtLeast(0)
}

private fun findFocusBlockIndex(
    displayBlocks: List<ReaderBlock>,
    focusIndex: Int,
): Int =
    displayBlocks.indexOfFirst { block ->
        val paragraph = (block as? ReaderParagraphBlock)?.paragraph ?: return@indexOfFirst false
        val endIndex = paragraph.startIndex + paragraph.tokens.size - 1
        focusIndex in paragraph.startIndex..endIndex
    }.coerceAtLeast(0)

private fun buildListStateKey(
    chapterIndex: Int,
    chapterDataHash: Int,
    resolvedPageIndex: Int,
    isPagedChapter: Boolean,
): String =
    if (isPagedChapter && resolvedPageIndex >= 0) {
        "$chapterIndex-$chapterDataHash-p$resolvedPageIndex"
    } else {
        "$chapterIndex-$chapterDataHash"
    }
