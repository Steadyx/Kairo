package com.example.kairo.core.language

import com.example.kairo.core.model.Book
import java.util.Locale

object LanguageTagNormalizer {
    fun normalize(tag: String?): String? {
        val cleaned = tag?.trim()?.replace('_', '-')?.takeIf { it.isNotBlank() } ?: return null
        val locale = Locale.forLanguageTag(cleaned)
        val normalized = locale.toLanguageTag()
        return normalized.takeIf { it.isNotBlank() && it != "und" }
    }
}

object LanguageDetector {
    fun detectLanguageTag(text: String): String? {
        val sample = text.take(MAX_SAMPLE_CHARS)
        if (containsRange(sample, 0x3040, 0x30FF)) return "ja" // Hiragana/Katakana
        if (containsRange(sample, 0xAC00, 0xD7AF)) return "ko" // Hangul
        if (containsRange(sample, 0x0600, 0x06FF) || containsRange(sample, 0x0750, 0x077F)) {
            return "ar"
        }
        if (containsRange(sample, 0x0590, 0x05FF)) return "he"
        if (containsRange(sample, 0x4E00, 0x9FFF)) return "zh-Hans" // CJK Unified Ideographs
        return null
    }

    private fun containsRange(text: String, start: Int, end: Int): Boolean {
        for (char in text) {
            val code = char.code
            if (code in start..end) return true
        }
        return false
    }

    private const val MAX_SAMPLE_CHARS = 2000
}

object BookLanguageResolver {
    fun resolve(book: Book): String? {
        val normalized = LanguageTagNormalizer.normalize(book.languageTag)
        if (normalized != null) return normalized
        val sample = sampleText(book) ?: return null
        return LanguageDetector.detectLanguageTag(sample)
    }

    private fun sampleText(book: Book): String? {
        val chapter = book.chapters.firstOrNull { it.plainText.isNotBlank() } ?: return null
        return chapter.plainText
    }
}
