package com.example.kairo.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.kairo.core.model.estimateMinutesForWords
import com.example.kairo.core.model.formatDurationMinutes
import kotlin.math.roundToInt

internal data class ReaderProgressState(
    val progressPercent: Int,
    val progressFraction: Float,
    val pageLabel: String?,
    val etaLabel: String?,
    val hasProgressMeta: Boolean,
)

@Composable
internal fun rememberReaderProgressState(
    safeFocusIndex: Int,
    totalChapterWords: Int,
    wordCountByToken: IntArray?,
    resolvedPageIndex: Int,
    pages: List<ChapterPage>,
    currentPage: ChapterPage?,
    estimatedWpm: Int,
    bookWordCounts: List<Int>,
    chapterIndex: Int,
    chapterCount: Int,
): ReaderProgressState {
    val progressPercent =
        remember(safeFocusIndex, totalChapterWords, wordCountByToken) {
            if (totalChapterWords <= 0 || wordCountByToken == null || wordCountByToken.isEmpty()) {
                0
            } else {
                val currentWordIndex = wordCountByToken[safeFocusIndex].coerceAtLeast(0)
                ((currentWordIndex.toFloat() / totalChapterWords.toFloat()) * 100f)
                    .roundToInt()
                    .coerceIn(0, 100)
            }
        }
    val progressFraction by remember(progressPercent) {
        derivedStateOf { (progressPercent / 100f).coerceIn(0f, 1f) }
    }
    val currentWordIndex =
        remember(safeFocusIndex, wordCountByToken) {
            if (wordCountByToken == null || wordCountByToken.isEmpty()) {
                0
            } else {
                wordCountByToken.getOrNull(safeFocusIndex) ?: 0
            }
        }
    val pageLabel =
        remember(resolvedPageIndex, pages) {
            if (resolvedPageIndex >= 0 && pages.isNotEmpty()) {
                "Page ${resolvedPageIndex + 1} of ${pages.size}"
            } else {
                null
            }
        }
    val wordsReadInPage =
        remember(currentPage, wordCountByToken, currentWordIndex) {
            if (currentPage == null || wordCountByToken == null || wordCountByToken.isEmpty()) {
                0
            } else {
                val startWordIndex =
                    if (currentPage.startTokenIndex > 0) {
                        wordCountByToken.getOrNull(currentPage.startTokenIndex - 1) ?: 0
                    } else {
                        0
                    }
                (currentWordIndex - startWordIndex).coerceAtLeast(0)
            }
        }
    val remainingPageWords =
        remember(currentPage, wordsReadInPage) {
            (currentPage?.wordCount ?: 0).minus(wordsReadInPage).coerceAtLeast(0)
        }
    val remainingChapterWords =
        remember(totalChapterWords, currentWordIndex) {
            (totalChapterWords - currentWordIndex).coerceAtLeast(0)
        }
    val adjustedBookWordCounts =
        remember(bookWordCounts, chapterIndex, totalChapterWords, chapterCount) {
            if (bookWordCounts.isEmpty()) {
                if (totalChapterWords > 0) {
                    val fallback =
                        MutableList(chapterCount.coerceAtLeast(1)) { 0 }
                    if (chapterIndex in fallback.indices) {
                        fallback[chapterIndex] = totalChapterWords
                    }
                    fallback
                } else {
                    emptyList()
                }
            } else {
                val updated = bookWordCounts.toMutableList()
                if (chapterIndex in updated.indices && totalChapterWords > 0) {
                    updated[chapterIndex] = totalChapterWords
                }
                updated.toList()
            }
        }
    val wordsBeforeChapter =
        remember(adjustedBookWordCounts, chapterIndex) {
            adjustedBookWordCounts.take(chapterIndex).sum()
        }
    val totalBookWords =
        remember(adjustedBookWordCounts) {
            adjustedBookWordCounts.sum()
        }
    val wordsReadOverall =
        remember(wordsBeforeChapter, currentWordIndex) {
            wordsBeforeChapter + currentWordIndex
        }
    val remainingBookWords =
        remember(totalBookWords, wordsReadOverall) {
            (totalBookWords - wordsReadOverall).coerceAtLeast(0)
        }
    val etaLabel =
        remember(
            estimatedWpm,
            remainingPageWords,
            remainingChapterWords,
            remainingBookWords,
        ) {
            if (estimatedWpm <= 0) return@remember null
            val parts = mutableListOf<String>()
            if (remainingPageWords > 0) {
                parts += "page ~${formatDurationMinutes(estimateMinutesForWords(remainingPageWords, estimatedWpm))}"
            }
            if (remainingChapterWords > 0) {
                parts += "chapter ~${formatDurationMinutes(estimateMinutesForWords(remainingChapterWords, estimatedWpm))}"
            }
            if (remainingBookWords > 0) {
                parts += "book ~${formatDurationMinutes(estimateMinutesForWords(remainingBookWords, estimatedWpm))}"
            }
            if (parts.isEmpty()) null else "ETA: ${parts.joinToString(" • ")}"
        }
    val hasProgressMeta = pageLabel != null || etaLabel != null

    return ReaderProgressState(
        progressPercent = progressPercent,
        progressFraction = progressFraction,
        pageLabel = pageLabel,
        etaLabel = etaLabel,
        hasProgressMeta = hasProgressMeta,
    )
}
