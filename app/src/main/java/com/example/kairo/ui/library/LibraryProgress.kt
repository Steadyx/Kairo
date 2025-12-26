package com.example.kairo.ui.library

import com.example.kairo.core.model.Book
import com.example.kairo.core.model.ReadingPosition
import com.example.kairo.core.model.countWords
import com.example.kairo.core.model.estimateMinutesForWords
import kotlin.math.roundToInt

data class LibraryBookProgress(
    val percentComplete: Int,
    val remainingMinutes: Int?,
)

suspend fun buildLibraryProgress(
    books: List<Book>,
    positions: List<ReadingPosition>,
    estimatedWpm: Int,
): Map<String, LibraryBookProgress> {
    if (books.isEmpty()) return emptyMap()
    val positionsByBook = positions.associateBy { it.bookId.value }
    val progress = mutableMapOf<String, LibraryBookProgress>()

    for (book in books) {
        val chapterWordCounts =
            book.chapters.map { chapter ->
                when {
                    chapter.wordCount > 0 -> chapter.wordCount
                    chapter.plainText.isNotBlank() -> countWords(chapter.plainText)
                    else -> 0
                }
            }
        val totalWords = chapterWordCounts.sum().coerceAtLeast(0)
        val position = positionsByBook[book.id.value]

        val wordsRead =
            if (position == null || totalWords == 0 || book.chapters.isEmpty()) {
                0
            } else {
                val chapterIndex = position.chapterIndex.coerceIn(0, book.chapters.lastIndex)
                val baseWords = chapterWordCounts.take(chapterIndex).sum()
                val chapterWordCount = chapterWordCounts.getOrNull(chapterIndex) ?: 0
                val chapterWordIndex = position.wordIndex.coerceAtLeast(0)
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
                ((wordsRead.toDouble() / totalWords.toDouble()) * 100.0)
                    .roundToInt()
                    .coerceIn(0, 100)
            }

        val remainingMinutes =
            if (estimatedWpm > 0 && totalWords > 0) {
                val remainingWords = (totalWords - wordsRead).coerceAtLeast(0)
                if (remainingWords == 0) {
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
