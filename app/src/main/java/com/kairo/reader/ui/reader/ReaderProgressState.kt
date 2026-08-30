package com.kairo.reader.ui.reader

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.kairo.reader.R
import com.kairo.reader.core.model.estimateMinutesForWords
import com.kairo.reader.ui.format.formatShortDurationMinutes
import kotlin.math.roundToInt

internal data class ReaderProgressState(
    val progressPercent: Int,
    val progressFraction: Float,
    val pageLabel: String?,
    val etaLabel: String?,
    val hasProgressMeta: Boolean,
)

internal data class ReaderProgressInput(
    val safeFocusIndex: Int,
    val totalChapterWords: Int,
    val wordCountByToken: IntArray?,
    val resolvedPageIndex: Int,
    val pages: List<ChapterPage>,
    val currentPage: ChapterPage?,
    val estimatedWpm: Int,
    val bookWordCounts: List<Int>,
    val chapterIndex: Int,
    val chapterCount: Int,
)

private data class ChapterWordProgress(val currentWordIndex: Int, val progressPercent: Int,)

@Composable
internal fun rememberReaderProgressState(
    input: ReaderProgressInput,
): ReaderProgressState {
    val resources = LocalResources.current
    val metaSeparator = stringResource(R.string.meta_separator)
    val chapterProgress =
        remember(input.safeFocusIndex, input.totalChapterWords, input.wordCountByToken) {
            calculateChapterWordProgress(input)
        }
    val progressFraction by remember(chapterProgress.progressPercent) {
        derivedStateOf {
            (chapterProgress.progressPercent / PERCENT_SCALE_FLOAT).coerceIn(0f, 1f)
        }
    }
    val pageLabel =
        if (input.resolvedPageIndex >= 0 && input.pages.isNotEmpty()) {
            resources.getString(
                R.string.reader_page_of_total,
                input.resolvedPageIndex + 1,
                input.pages.size,
            )
        } else {
            null
        }
    val wordsReadInPage =
        remember(input.currentPage, input.wordCountByToken, chapterProgress.currentWordIndex) {
            calculateWordsReadInPage(input, chapterProgress.currentWordIndex)
        }
    val remainingPageWords =
        remember(input.currentPage, wordsReadInPage) {
            (input.currentPage?.wordCount ?: 0).minus(wordsReadInPage).coerceAtLeast(0)
        }
    val remainingChapterWords =
        remember(input.totalChapterWords, chapterProgress.currentWordIndex) {
            (input.totalChapterWords - chapterProgress.currentWordIndex).coerceAtLeast(0)
        }
    val adjustedBookWordCounts =
        remember(input.bookWordCounts, input.chapterIndex, input.totalChapterWords, input.chapterCount) {
            adjustedBookWordCounts(input)
        }
    val wordsBeforeChapter =
        remember(adjustedBookWordCounts, input.chapterIndex) {
            adjustedBookWordCounts.take(input.chapterIndex).sum()
        }
    val totalBookWords =
        remember(adjustedBookWordCounts) {
            adjustedBookWordCounts.sum()
        }
    val wordsReadOverall =
        remember(wordsBeforeChapter, chapterProgress.currentWordIndex) {
            wordsBeforeChapter + chapterProgress.currentWordIndex
        }
    val remainingBookWords =
        remember(totalBookWords, wordsReadOverall) {
            (totalBookWords - wordsReadOverall).coerceAtLeast(0)
        }
    val etaLabel =
        buildEtaLabel(
            resources = resources,
            estimatedWpm = input.estimatedWpm,
            remainingPageWords = remainingPageWords,
            remainingChapterWords = remainingChapterWords,
            remainingBookWords = remainingBookWords,
            separator = metaSeparator,
        )
    val hasProgressMeta = pageLabel != null || etaLabel != null

    return ReaderProgressState(
        progressPercent = chapterProgress.progressPercent,
        progressFraction = progressFraction,
        pageLabel = pageLabel,
        etaLabel = etaLabel,
        hasProgressMeta = hasProgressMeta,
    )
}

private fun calculateChapterWordProgress(input: ReaderProgressInput): ChapterWordProgress {
    val wordCounts = input.wordCountByToken
    if (input.totalChapterWords <= 0 || wordCounts == null || wordCounts.isEmpty()) {
        return ChapterWordProgress(currentWordIndex = 0, progressPercent = 0)
    }
    val currentWordIndex = wordCounts.getOrNull(input.safeFocusIndex)?.coerceAtLeast(0) ?: 0
    val progressPercent =
        ((currentWordIndex.toFloat() / input.totalChapterWords.toFloat()) * PERCENT_SCALE_FLOAT)
            .roundToInt()
            .coerceIn(0, PERCENT_SCALE_INT)
    return ChapterWordProgress(currentWordIndex, progressPercent)
}

private fun calculateWordsReadInPage(
    input: ReaderProgressInput,
    currentWordIndex: Int,
): Int {
    val currentPage = input.currentPage ?: return 0
    val wordCounts = input.wordCountByToken
    if (wordCounts == null || wordCounts.isEmpty()) return 0
    val startWordIndex =
        if (currentPage.startTokenIndex > 0) {
            wordCounts.getOrNull(currentPage.startTokenIndex - 1) ?: 0
        } else {
            0
        }
    return (currentWordIndex - startWordIndex).coerceAtLeast(0)
}

private fun adjustedBookWordCounts(input: ReaderProgressInput): List<Int> {
    if (input.bookWordCounts.isEmpty() && input.totalChapterWords <= 0) return emptyList()
    val counts =
        if (input.bookWordCounts.isEmpty()) {
            MutableList(input.chapterCount.coerceAtLeast(1)) { 0 }
        } else {
            input.bookWordCounts.toMutableList()
        }
    if (input.chapterIndex in counts.indices && input.totalChapterWords > 0) {
        counts[input.chapterIndex] = input.totalChapterWords
    }
    return counts
}

private fun buildEtaLabel(
    resources: Resources,
    estimatedWpm: Int,
    remainingPageWords: Int,
    remainingChapterWords: Int,
    remainingBookWords: Int,
    separator: String,
): String? {
    if (estimatedWpm <= 0) return null
    val parts =
        listOfNotNull(
            etaPart(resources, R.string.reader_eta_page, remainingPageWords, estimatedWpm),
            etaPart(resources, R.string.reader_eta_chapter, remainingChapterWords, estimatedWpm),
            etaPart(resources, R.string.reader_eta_book, remainingBookWords, estimatedWpm),
        )
    return parts.takeIf { it.isNotEmpty() }?.let {
        resources.getString(R.string.reader_eta_prefix, it.joinToString(separator))
    }
}

private fun etaPart(
    resources: Resources,
    labelRes: Int,
    remainingWords: Int,
    estimatedWpm: Int,
): String? {
    if (remainingWords <= 0) return null
    val minutes = estimateMinutesForWords(remainingWords, estimatedWpm)
    return resources.getString(labelRes, formatShortDurationMinutes(resources, minutes))
}

private const val PERCENT_SCALE_INT = 100
private const val PERCENT_SCALE_FLOAT = 100f
