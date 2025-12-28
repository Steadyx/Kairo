package com.example.kairo.core.tokenization

import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.Token

interface ChapterTokenizer {
    fun tokenize(chapter: Chapter): List<Token>
}

private class DefaultTokenizer : ChapterTokenizer {
    override fun tokenize(chapter: Chapter): List<Token> = Tokenizer().tokenize(chapter)
}

object TokenizerRegistry {
    private val defaultTokenizer = DefaultTokenizer()

    fun resolve(languageTag: String?): ChapterTokenizer = defaultTokenizer
}
