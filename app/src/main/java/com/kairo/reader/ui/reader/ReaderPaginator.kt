package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.isPhysicalPageBreakToken
import com.kairo.reader.core.model.isSentenceEndingPunctuation
import com.kairo.reader.core.model.shouldKeepPhysicalPageBreak
import kotlin.math.roundToInt

private const val PAGE_MIN_WORD_FRACTION = 0.82f
private const val PAGE_MAX_WORD_FRACTION = 1.25f
private const val PAGE_TARGET_FRACTION = 0.94f
private const val PAGE_EXTRA_WORD_FRACTION = 0.2f
private const val PAGE_EXTRA_WORDS_MIN = 10
private const val PAGE_EXTRA_WORDS_MAX = 60
private const val PAGE_TRAILING_PAGE_MIN_FRACTION = 0.5f
private const val PAGE_TRAILING_PAGE_MERGED_MAX_FRACTION = 1.6f
internal const val DEFAULT_WORDS_PER_PAGE = 300
private const val PAGINATION_VIEWPORT_BASE_DP = 760f
private const val PAGINATION_FONT_BASE_SP = 18f
private const val PAGINATION_WORDS_MIN = 170
private const val PAGINATION_WORDS_MAX = 460

internal fun buildChapterPages(
    tokens: List<Token>,
    wordsPerPage: Int,
): List<ChapterPage> {
    if (tokens.isEmpty() || wordsPerPage <= 0) return emptyList()
    val limits = paginationLimits(wordsPerPage)
    val pages = mutableListOf<ChapterPage>()
    var cursor = 0

    while (cursor < tokens.size) {
        val firstWordIndex = nextWordTokenIndex(tokens, cursor) ?: break
        var pageStartTokenIndex = firstWordIndex
        while (pageStartTokenIndex > cursor && tokens[pageStartTokenIndex - 1].type == TokenType.PUNCTUATION) {
            pageStartTokenIndex -= 1
        }
        val scan = scanPage(tokens, pageStartTokenIndex, limits)
        if (scan.wordCount <= 0) {
            cursor = pageStartTokenIndex + 1
            continue
        }
        val boundary = choosePageBoundary(tokens, scan, limits)
        val chosenWordIndex = boundary?.endWordIndex ?: scan.endWordTokenIndex
        val chosenWordCount = boundary?.wordCount ?: scan.wordCount
        val endTokenIndex = extendTrailingPunctuation(tokens, chosenWordIndex)
        pages.add(
            ChapterPage(
                index = pages.size,
                startTokenIndex = pageStartTokenIndex,
                endTokenIndex = endTokenIndex,
                wordCount = chosenWordCount,
                focusTokenIndex = firstWordIndex,
            ),
        )
        cursor = scan.nextCursorAfterHardBreak ?: (endTokenIndex + 1)
    }

    return mergeTrailingSparsePage(tokens, pages, wordsPerPage)
}

private fun paginationLimits(wordsPerPage: Int): PaginationLimits {
    val minWords = (wordsPerPage * PAGE_MIN_WORD_FRACTION).roundToInt().coerceAtLeast(1)
    val maxWords = (wordsPerPage * PAGE_MAX_WORD_FRACTION).roundToInt().coerceAtLeast(minWords)
    val targetWords = wordsPerPage.coerceAtLeast(1)
    return PaginationLimits(
        minWords = minWords,
        maxWords = maxWords,
        targetWords = targetWords,
        minTargetWords = (targetWords * PAGE_TARGET_FRACTION).roundToInt(),
        maxExtraWords =
        (wordsPerPage * PAGE_EXTRA_WORD_FRACTION)
            .roundToInt()
            .coerceIn(PAGE_EXTRA_WORDS_MIN, PAGE_EXTRA_WORDS_MAX),
    )
}

private fun scanPage(
    tokens: List<Token>,
    startIndex: Int,
    limits: PaginationLimits,
): PageScan {
    var scan = PageScan(endWordTokenIndex = startIndex)
    var index = startIndex
    while (index < tokens.size && scan.nextCursorAfterHardBreak == null) {
        scan = scan.accept(tokens, index, limits)
        val hasNearBoundary = scan.preferredBoundary?.wordCount?.let { it >= limits.minTargetWords } == true
        val targetReached = scan.wordCount >= limits.targetWords && hasNearBoundary
        if (scan.wordCount >= limits.maxWords || targetReached) break
        index += 1
    }
    return scan
}

private fun PageScan.accept(
    tokens: List<Token>,
    index: Int,
    limits: PaginationLimits,
): PageScan {
    val token = tokens[index]
    return when (token.type) {
        TokenType.WORD -> copy(wordCount = wordCount + 1, endWordTokenIndex = index)
        TokenType.PAGE_BREAK ->
            if (wordCount > 0 && isHardReaderPageBreak(tokens, index)) {
                copy(nextCursorAfterHardBreak = index + 1)
            } else {
                this
            }
        TokenType.PARAGRAPH_BREAK -> recordBoundary(limits)
        TokenType.PUNCTUATION -> acceptPunctuation(token, limits)
    }
}

private fun PageScan.acceptPunctuation(
    token: Token,
    limits: PaginationLimits,
): PageScan {
    val nextParenDepth =
        when {
            isOpeningBracket(token) -> parenDepth + 1
            isClosingBracket(token) -> (parenDepth - 1).coerceAtLeast(0)
            else -> parenDepth
        }
    val updated = copy(parenDepth = nextParenDepth)
    return if (nextParenDepth == 0 && isSentenceEnding(token)) updated.recordBoundary(limits) else updated
}

private fun PageScan.recordBoundary(limits: PaginationLimits): PageScan {
    if (wordCount <= 0 || parenDepth != 0) return this
    val boundary = PageBoundary(endWordTokenIndex, wordCount)
    return copy(
        fallbackBoundary = boundary,
        preferredBoundary = boundary.takeIf { wordCount >= limits.minWords } ?: preferredBoundary,
    )
}

private fun choosePageBoundary(
    tokens: List<Token>,
    scan: PageScan,
    limits: PaginationLimits,
): PageBoundary? {
    if (scan.nextCursorAfterHardBreak != null) {
        return PageBoundary(scan.endWordTokenIndex, scan.wordCount)
    }
    val preferred =
        scan.preferredBoundary?.takeIf {
            it.wordCount >= limits.minTargetWords || scan.wordCount >= limits.maxWords
        }
    if (preferred != null || limits.maxExtraWords <= 0) return preferred ?: scan.fallbackBoundary
    val forward =
        findForwardBoundary(
            tokens = tokens,
            startIndex = scan.endWordTokenIndex + 1,
            initialWordCount = scan.wordCount,
            maxExtraWords = limits.maxExtraWords,
            startingParenDepth = scan.parenDepth,
        )
    return forward?.let { PageBoundary(it.endWordIndex, it.wordCount) } ?: scan.fallbackBoundary
}

private data class PaginationLimits(
    val minWords: Int,
    val maxWords: Int,
    val targetWords: Int,
    val minTargetWords: Int,
    val maxExtraWords: Int,
)

private data class PageScan(
    val wordCount: Int = 0,
    val endWordTokenIndex: Int,
    val fallbackBoundary: PageBoundary? = null,
    val preferredBoundary: PageBoundary? = null,
    val parenDepth: Int = 0,
    val nextCursorAfterHardBreak: Int? = null,
)

private fun nextWordTokenIndex(
    tokens: List<Token>,
    startIndex: Int,
): Int? {
    for (i in startIndex until tokens.size) {
        if (tokens[i].type == TokenType.WORD) return i
    }
    return null
}

private fun extendTrailingPunctuation(
    tokens: List<Token>,
    endWordIndex: Int,
): Int {
    var endIndex = endWordIndex
    var i = endWordIndex + 1
    while (i < tokens.size) {
        val token = tokens[i]
        if (token.type == TokenType.PUNCTUATION && token.text !in LEADING_PUNCTUATION) {
            endIndex = i
            i += 1
            continue
        }
        if (token.type == TokenType.WORD ||
            token.type == TokenType.PARAGRAPH_BREAK ||
            token.type == TokenType.PAGE_BREAK
        ) {
            break
        }
        break
    }
    return endIndex
}

private fun isSentenceEnding(token: Token): Boolean {
    if (token.type != TokenType.PUNCTUATION) return false
    return token.text.singleOrNull()?.let(::isSentenceEndingPunctuation) == true
}

private fun isHardReaderPageBreak(
    tokens: List<Token>,
    index: Int,
): Boolean {
    val token = tokens.getOrNull(index) ?: return false
    return !isPhysicalPageBreakToken(token) || shouldKeepPhysicalPageBreak(tokens, index)
}

private val LEADING_PUNCTUATION = setOf("(", "[", "{", "“", "‘", "«", "‹", "「", "『", "（", "【")
private val OPENING_BRACKETS = setOf("(", "[", "{")
private val CLOSING_BRACKETS = setOf(")", "]", "}")

private data class PageBoundary(val endWordIndex: Int, val wordCount: Int,)

private fun isOpeningBracket(token: Token): Boolean =
    token.type == TokenType.PUNCTUATION && token.text in OPENING_BRACKETS

private fun isClosingBracket(token: Token): Boolean =
    token.type == TokenType.PUNCTUATION && token.text in CLOSING_BRACKETS

private fun mergeTrailingSparsePage(
    tokens: List<Token>,
    pages: List<ChapterPage>,
    wordsPerPage: Int,
): List<ChapterPage> {
    if (pages.size < 2) return pages
    val lastPage = pages.last()
    val sparseThreshold =
        (wordsPerPage * PAGE_TRAILING_PAGE_MIN_FRACTION)
            .roundToInt()
            .coerceAtLeast(1)
    if (lastPage.wordCount >= sparseThreshold) return pages

    val previousIndex = pages.lastIndex - 1
    val previousPage = pages[previousIndex]
    if (hasHardPageBreakBetween(tokens, previousPage.endTokenIndex, lastPage.startTokenIndex)) {
        return pages
    }

    val mergedMaxWords =
        (wordsPerPage * PAGE_TRAILING_PAGE_MERGED_MAX_FRACTION)
            .roundToInt()
            .coerceAtLeast(wordsPerPage)
    val mergedWordCount = previousPage.wordCount + lastPage.wordCount
    if (mergedWordCount > mergedMaxWords) return pages

    val mergedPages = pages.toMutableList()
    mergedPages[previousIndex] =
        previousPage.copy(
            endTokenIndex = lastPage.endTokenIndex,
            wordCount = mergedWordCount,
        )
    mergedPages.removeAt(mergedPages.lastIndex)
    return mergedPages.mapIndexed { index, page ->
        if (page.index == index) page else page.copy(index = index)
    }
}

private fun hasHardPageBreakBetween(
    tokens: List<Token>,
    previousEndTokenIndex: Int,
    nextStartTokenIndex: Int,
): Boolean {
    val from = (previousEndTokenIndex + 1).coerceAtLeast(0)
    val until = nextStartTokenIndex.coerceAtMost(tokens.size)
    if (from >= until) return false
    for (i in from until until) {
        if (tokens[i].type == TokenType.PAGE_BREAK) return true
    }
    return false
}

private data class ForwardBoundary(val endWordIndex: Int, val wordCount: Int,)

private fun findForwardBoundary(
    tokens: List<Token>,
    startIndex: Int,
    initialWordCount: Int,
    maxExtraWords: Int,
    startingParenDepth: Int,
): ForwardBoundary? {
    var wordCount = initialWordCount
    var lastWordIndex = if (startIndex > 0) startIndex - 1 else -1
    var parenDepth = startingParenDepth

    var i = startIndex
    while (i < tokens.size) {
        val token = tokens[i]
        when (token.type) {
            TokenType.WORD -> {
                wordCount += 1
                lastWordIndex = i
                if (wordCount - initialWordCount > maxExtraWords) return null
            }
            TokenType.PAGE_BREAK -> return null
            TokenType.PARAGRAPH_BREAK -> {
                if (lastWordIndex >= 0 && wordCount > initialWordCount && parenDepth == 0) {
                    return ForwardBoundary(lastWordIndex, wordCount)
                }
            }
            TokenType.PUNCTUATION -> {
                if (isOpeningBracket(token)) {
                    parenDepth += 1
                } else if (isClosingBracket(token)) {
                    parenDepth = (parenDepth - 1).coerceAtLeast(0)
                }
                if (lastWordIndex >= 0 &&
                    wordCount > initialWordCount &&
                    parenDepth == 0 &&
                    isSentenceEnding(token)
                ) {
                    return ForwardBoundary(lastWordIndex, wordCount)
                }
            }
        }
        i += 1
    }

    return null
}

internal fun estimateWordsPerPage(
    fontSizeSp: Float,
    viewportHeightDp: Int,
): Int {
    val safeFontSp = fontSizeSp.coerceIn(PAGINATION_FONT_SIZE_MIN_SP, PAGINATION_FONT_SIZE_MAX_SP)
    val safeViewportDp = viewportHeightDp.coerceAtLeast(PAGINATION_VIEWPORT_MIN_DP)
    val viewportFactor = safeViewportDp / PAGINATION_VIEWPORT_BASE_DP
    val fontFactor = PAGINATION_FONT_BASE_SP / safeFontSp
    return (DEFAULT_WORDS_PER_PAGE * viewportFactor * fontFactor)
        .roundToInt()
        .coerceIn(PAGINATION_WORDS_MIN, PAGINATION_WORDS_MAX)
}

private const val PAGINATION_FONT_SIZE_MIN_SP = 12f
private const val PAGINATION_FONT_SIZE_MAX_SP = 36f
private const val PAGINATION_VIEWPORT_MIN_DP = 480

internal fun rePageChapterData(
    chapterData: ChapterData,
    wordsPerPage: Int,
): ChapterData = chapterData.copy(pages = buildChapterPages(chapterData.tokens, wordsPerPage))
