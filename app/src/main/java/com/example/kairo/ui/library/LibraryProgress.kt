package com.example.kairo.ui.library

import com.example.kairo.core.model.Book
import com.example.kairo.core.model.ReadingPosition
import com.example.kairo.core.model.countWords
import com.example.kairo.core.model.countWordsThroughToken
import com.example.kairo.core.model.estimateMinutesForWords
import com.example.kairo.core.model.nearestWordIndex
import com.example.kairo.data.books.BookRepository
import com.example.kairo.data.token.TokenRepository
import kotlin.math.roundToInt

data class LibraryBookProgress(
    val percentComplete: Int,
    val remainingMinutes: Int?,
)

suspend fun buildLibraryProgress(
    books: List<Book>,
    positions: List<ReadingPosition>,
    estimatedWpm: Int,
    bookRepository: BookRepository,
    tokenRepository: TokenRepository,
): Map<String, LibraryBookProgress> {
    if (books.isEmpty()) return emptyMap()
    val positionsByBook = positions.associateBy { it.bookId.value }
    val progress = mutableMapOf<String, LibraryBookProgress>()

    for (book in books) {
        var chapterWordCounts =
            book.chapters.map { chapter ->
                when {
                    chapter.wordCount > 0 -> chapter.wordCount
                    chapter.plainText.isNotBlank() -> countWords(chapter.plainText)
                    else -> 0
                }
            }
        val missingIndices = chapterWordCounts.withIndex().filter { it.value == 0 }.map { it.index }
        if (missingIndices.isNotEmpty() && book.chapters.isNotEmpty()) {
            val updated = chapterWordCounts.toMutableList()
            for (index in missingIndices) {
                val resolved =
                    runCatching { bookRepository.getChapter(book.id, index) }.getOrNull()
                        ?: continue
                val count = countWords(resolved.plainText)
                if (count > 0) {
                    updated[index] = count
                    bookRepository.updateChapterWordCount(book.id, index, count)
                }
            }
            chapterWordCounts = updated
        }
        var totalWords = chapterWordCounts.sum().coerceAtLeast(0)
        val position = positionsByBook[book.id.value]

        val wordsRead =
            if (position == null || totalWords == 0 || book.chapters.isEmpty()) {
                0
            } else {
                val chapterIndex = position.chapterIndex.coerceIn(0, book.chapters.lastIndex)
                val chapter = book.chapters.getOrNull(chapterIndex)
                val chapterWordsRead =
                    if (chapter != null) {
                        val tokens =
                            if (chapter.plainText.isBlank()) {
                                tokenRepository.getTokens(book.id, chapterIndex, null)
                            } else {
                                tokenRepository.getTokens(book.id, chapterIndex, chapter)
                            }
                        if (tokens.isNotEmpty()) {
                            val chapterWordTotal = countWords(tokens)
                            if (chapterIndex in chapterWordCounts.indices && chapterWordTotal > 0) {
                                val updated = chapterWordCounts.toMutableList()
                                updated[chapterIndex] = chapterWordTotal
                                chapterWordCounts = updated
                                totalWords = updated.sum().coerceAtLeast(0)
                            }
                            val safeIndex =
                                tokens.nearestWordIndex(position.tokenIndex).coerceIn(
                                    0,
                                    tokens.lastIndex
                                )
                            countWordsThroughToken(tokens, safeIndex)
                        } else {
                            0
                        }
                    } else {
                        0
                    }

                chapterWordCounts.take(chapterIndex).sum() + chapterWordsRead
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
