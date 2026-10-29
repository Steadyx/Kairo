package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Chapter

internal data class ReaderChapterProgress(val currentNumber: Int, val totalNumber: Int,)

internal fun resolveReaderChapterProgress(
    chapters: List<Chapter>,
    chapterIndex: Int,
): ReaderChapterProgress {
    val linearChapters = chapters.filter { it.isLinear }
    val fallbackCurrent = linearChapters.count { it.index <= chapterIndex }.coerceAtLeast(1)
    val fallbackTotal = linearChapters.size.coerceAtLeast(fallbackCurrent)
    val currentChapter = chapters.getOrNull(chapterIndex) ?: return ReaderChapterProgress(
        currentNumber = fallbackCurrent,
        totalNumber = fallbackTotal,
    )

    val numberedChapterNumbers = linearChapters.mapNotNull { extractReaderChapterNumber(it.title) }
    val currentNumber = extractReaderChapterNumber(currentChapter.title)
    if (currentNumber == null || numberedChapterNumbers.isEmpty()) {
        return ReaderChapterProgress(
            currentNumber = fallbackCurrent,
            totalNumber = fallbackTotal,
        )
    }

    val highestNumber = numberedChapterNumbers.max()
    val totalNumber = highestNumber.coerceAtLeast(currentNumber)
    return ReaderChapterProgress(
        currentNumber = currentNumber,
        totalNumber = totalNumber,
    )
}

private fun extractReaderChapterNumber(title: String?): Int? {
    val sanitizedTitle = sanitizeChapterTitleForDisplay(title) ?: return null
    val match =
        CHAPTER_NUMBER_REGEX.matchEntire(sanitizedTitle.trim())
            ?: return null
    val numberText = match.groupValues[1].ifBlank { match.groupValues[2] }
    return numberText.toIntOrNull()?.takeIf { it > 0 }
}

private val CHAPTER_NUMBER_REGEX =
    Regex("(?i)^(?:chapter|chapitre|cap[ií]tulo|kapitel|section|part|book)?\\s*(\\d+)$|^(\\d+)$")
