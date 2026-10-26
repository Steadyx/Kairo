@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.ui.library

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.countWords
import com.kairo.reader.core.model.estimateMinutesForWords
import com.kairo.reader.core.rsvp.RsvpConfigResolver
import com.kairo.reader.core.rsvp.RsvpEstimatedReadingPace
import kotlin.math.roundToInt

data class LibraryBookProgress(val percentComplete: Int, val remainingMinutes: Int?,)

internal fun buildLibraryEstimatedWpmByBookId(
    books: List<Book>,
    config: RsvpConfig,
    fallbackEstimatedWpm: Int,
    estimateWpm: (RsvpConfig, String?) -> Int = { resolvedConfig, languageTag ->
        RsvpEstimatedReadingPace.estimateWpm(
            config = resolvedConfig,
            fallbackEstimatedWpm = fallbackEstimatedWpm,
            languageTag = languageTag,
        )
    },
): Map<String, Int> {
    if (books.isEmpty()) return emptyMap()
    val wpmByLanguage = mutableMapOf<String?, Int>()
    return books.associate { book ->
        val languageTag = book.languageTag
        val estimatedWpm =
            wpmByLanguage.getOrPut(languageTag) {
                estimateWpm(
                    RsvpConfigResolver.resolve(config, languageTag),
                    languageTag,
                )
            }
        book.id.value to estimatedWpm
    }
}

suspend fun buildLibraryProgress(
    books: List<Book>,
    positions: List<ReadingPosition>,
    estimatedWpmByBookId: Map<String, Int>,
): Map<String, LibraryBookProgress> {
    if (books.isEmpty()) return emptyMap()
    val positionsByBook = positions.associateBy { it.bookId.value }
    val progress = mutableMapOf<String, LibraryBookProgress>()

    for (book in books) {
        val chapterWordCounts =
            book.chapters.map { chapter ->
                when {
                    !chapter.isLinear -> 0
                    chapter.wordCount > 0 -> chapter.wordCount
                    chapter.plainText.isNotBlank() -> countWords(chapter.plainText)
                    else -> 0
                }
            }
        val totalWords = chapterWordCounts.sum().coerceAtLeast(0)
        val position = positionsByBook[book.id.value]
        // A legacy position in an auxiliary note does not identify the linear reading position.
        if (position != null && book.chapters.getOrNull(position.chapterIndex)?.isLinear == false) continue

        val wordsRead =
            if (position == null || totalWords == 0 || book.chapters.isEmpty()) {
                0
            } else {
                val chapterIndex = position.chapterIndex.coerceIn(0, book.chapters.lastIndex)
                val baseWords = chapterWordCounts.take(chapterIndex).sum()
                val chapterWordCount = chapterWordCounts.getOrNull(chapterIndex) ?: 0
                val chapterWordIndex = if (book.chapters[chapterIndex].isLinear) position.wordIndex.coerceAtLeast(0) else 0
                val cappedChapterWordIndex =
                    if (chapterWordCount > 0) {
                        chapterWordIndex.coerceAtMost(chapterWordCount)
                    } else {
                        chapterWordIndex
                    }
                baseWords + cappedChapterWordIndex
            }

        val percentComplete =
            if (totalWords == 0) {
                0
            } else {
                ((wordsRead.toDouble() / totalWords.toDouble()) * PERCENT_SCALE)
                    .roundToInt()
                    .coerceIn(0, PERCENT_MAX)
            }

        val remainingMinutes =
            if (totalWords > 0) {
                val estimatedWpm = estimatedWpmByBookId[book.id.value] ?: 0
                val remainingWords = (totalWords - wordsRead).coerceAtLeast(0)
                if (estimatedWpm <= 0 || remainingWords == 0) {
                    null
                } else {
                    estimateMinutesForWords(remainingWords, estimatedWpm)
                }
            } else {
                null
            }

        progress[book.id.value] =
            LibraryBookProgress(
                percentComplete = percentComplete,
                remainingMinutes = remainingMinutes,
            )
    }

    return progress
}

private const val PERCENT_SCALE = 100.0
private const val PERCENT_MAX = 100
