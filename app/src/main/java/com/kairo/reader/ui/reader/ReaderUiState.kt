package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Token

data class ReaderUiState(
    val isLoading: Boolean = false,
    val chapterIndex: Int = 0,
    val focusIndex: Int = 0,
    val pageIndexOverride: Int? = null,
    val chapterData: ChapterData? = null,
    val chapterLoadError: String? = null,
    val bookWordCounts: List<Int> = emptyList(),
    val bookTotalWords: Int = 0,
    val isLinearChapter: Boolean = true,
    val auxiliaryReturn: ReaderReturnPosition? = null,
)

data class ReaderReturnPosition(val chapterIndex: Int, val focusIndex: Int, val pageIndex: Int?, val wordIndex: Int = 0,)

data class ChapterData(
    val tokens: List<Token>,
    val plainText: String,
    val paragraphs: List<Paragraph>,
    val blocks: List<ReaderBlock>,
    val firstWordIndex: Int,
    val imagePaths: List<String>,
    val pages: List<ChapterPage>,
    val wordCountByToken: IntArray,
    val totalWords: Int,
)

/**
 * A paragraph is a group of tokens between PARAGRAPH_BREAK tokens.
 * Stores the starting index for mapping back to the original token list.
 */
data class Paragraph(val tokens: List<Token>, val startIndex: Int,)

enum class ChapterPageKind { TEXT, IMAGE, BLANK }

data class ChapterPage(
    val index: Int,
    val startTokenIndex: Int,
    val endTokenIndex: Int,
    val wordCount: Int,
    val kind: ChapterPageKind = ChapterPageKind.TEXT,
    val imagePath: String? = null,
    val imageIndex: Int? = null,
    val focusTokenIndex: Int = startTokenIndex,
)

sealed interface ReaderBlock {
    val key: String
}

data class ReaderParagraphBlock(val paragraph: Paragraph,) : ReaderBlock {
    override val key: String = "paragraph_${paragraph.startIndex}"
}

data class ReaderImageSize(val widthPx: Float? = null, val heightPx: Float? = null,)

data class ReaderImageBlock(
    val imagePath: String,
    val index: Int,
    val anchorTokenIndex: Int? = null,
    val imageSize: ReaderImageSize? = null,
) : ReaderBlock {
    override val key: String = "image_${index}_$imagePath"
}
