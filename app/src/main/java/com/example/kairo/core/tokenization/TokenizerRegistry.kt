package com.example.kairo.core.tokenization

import com.example.kairo.core.language.LanguageTagNormalizer
import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.Token
import com.example.kairo.core.tokenization.cjk.CjkSegmenterConfig
import com.example.kairo.core.tokenization.cjk.CjkTokenizer
import com.example.kairo.core.tokenization.rtl.RtlTokenizer

interface ChapterTokenizer {
    fun tokenize(chapter: Chapter): List<Token>
}

private class DefaultTokenizer : ChapterTokenizer {
    override fun tokenize(chapter: Chapter): List<Token> = Tokenizer().tokenize(chapter)
}

object TokenizerRegistry {
    private val defaultTokenizer = DefaultTokenizer()
    private val jaTokenizer =
        CjkTokenizer(
            CjkSegmenterConfig(
                maxCjkCharsPerToken = 2,
                treatHangulAsWord = false,
            ),
        )
    private val zhTokenizer =
        CjkTokenizer(
            CjkSegmenterConfig(
                maxCjkCharsPerToken = 2,
                treatHangulAsWord = false,
            ),
        )
    private val koTokenizer =
        CjkTokenizer(
            CjkSegmenterConfig(
                maxCjkCharsPerToken = 2,
                treatHangulAsWord = true,
            ),
        )
    private val rtlTokenizer = RtlTokenizer()

    fun resolve(languageTag: String?): ChapterTokenizer {
        val normalized = LanguageTagNormalizer.normalize(languageTag)?.lowercase()
        return when {
            normalized == null -> defaultTokenizer
            normalized.startsWith("ja") -> jaTokenizer
            normalized.startsWith("zh") -> zhTokenizer
            normalized.startsWith("ko") -> koTokenizer
            normalized.startsWith("ar") -> rtlTokenizer
            normalized.startsWith("he") -> rtlTokenizer
            else -> defaultTokenizer
        }
    }
}
