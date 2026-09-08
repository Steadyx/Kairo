package com.kairo.reader.core.tokenization

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.countWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterWordCountsTest {
    @Test
    fun persistedRepairVersionPreventsRepeatedCjkAndEmptyChapterCounts() {
        val old = Chapter(0, null, "", "", wordCount = 7)
        assertTrue(needsChapterWordCountRepair(old, "zh-Hans"))
        assertFalse(needsChapterWordCountRepair(old, "en"))
        assertTrue(needsChapterWordCountRepair(old.copy(wordCount = 0), "en"))
        val repaired = old.copy(wordCountVersion = CHAPTER_WORD_COUNT_VERSION)
        assertFalse(needsChapterWordCountRepair(repaired, "zh-Hans"))
        assertFalse(needsChapterWordCountRepair(repaired.copy(wordCount = 0), "zh-Hans"))
        assertFalse(needsChapterWordCountRepair(repaired.copy(wordCount = 0), "en"))
    }

    @Test
    fun cjkCountsUseTheSameReadingUnitsAsPlayback() {
        listOf(
            "zh-Hans" to "这是一个没有空格但完全可以阅读的中文段落",
            "ja" to "これは日本語の文章です",
            "ko" to "한국어 문장을 읽고 있습니다",
        ).forEach { (language, text) ->
            val chapter = Chapter(index = 0, title = null, htmlContent = "<p>$text</p>", plainText = text)
            val count = countChapterWords(chapter, language)
            assertEquals(countWords(TokenizerRegistry.resolve(language).tokenize(chapter)), count)
            assertTrue(count > 1)
        }
    }
}
