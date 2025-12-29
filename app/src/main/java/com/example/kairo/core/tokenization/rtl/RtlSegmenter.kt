package com.example.kairo.core.tokenization.rtl

import com.example.kairo.core.model.Token

internal class RtlSegmenter(private val config: RtlSegmenterConfig) {
    fun tokenizeParagraph(paragraph: String): List<Token> =
        segment(paragraph).map { segment ->
            when (segment.type) {
                RtlSegmentType.WORD -> RtlTokenFactory.word(segment.text)
                RtlSegmentType.PUNCTUATION -> RtlTokenFactory.punctuation(segment.text)
            }
        }

    fun tokenizeInlineText(text: String): List<String> =
        segment(text).map { it.text }

    private fun segment(text: String): List<RtlSegment> {
        if (text.isBlank()) return emptyList()

        val segments = mutableListOf<RtlSegment>()
        val wordBuffer = StringBuilder()

        fun flushWord() {
            if (wordBuffer.isNotEmpty()) {
                segments += RtlSegment(wordBuffer.toString(), RtlSegmentType.WORD)
                wordBuffer.clear()
            }
        }

        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            val tokenText = String(Character.toChars(codePoint))
            val nextIndex = index + charCount
            val nextCodePoint = if (nextIndex < text.length) text.codePointAt(nextIndex) else null

            when {
                RtlCharClassifier.isWhitespace(codePoint) -> {
                    flushWord()
                }
                RtlCharClassifier.isPunctuation(codePoint) -> {
                    flushWord()
                    segments += RtlSegment(tokenText, RtlSegmentType.PUNCTUATION)
                }
                RtlCharClassifier.isWordConnector(codePoint) -> {
                    val canJoin =
                        wordBuffer.isNotEmpty() &&
                            nextCodePoint != null &&
                            RtlCharClassifier.isWordChar(nextCodePoint)
                    if (canJoin) {
                        wordBuffer.append(tokenText)
                    } else {
                        flushWord()
                        segments += RtlSegment(tokenText, RtlSegmentType.PUNCTUATION)
                    }
                }
                config.keepDiacriticsWithWord && RtlCharClassifier.isCombiningMark(codePoint) -> {
                    if (wordBuffer.isNotEmpty()) {
                        wordBuffer.append(tokenText)
                    } else {
                        wordBuffer.append(tokenText)
                    }
                }
                RtlCharClassifier.isWordChar(codePoint) -> {
                    wordBuffer.append(tokenText)
                }
                else -> {
                    flushWord()
                    segments += RtlSegment(tokenText, RtlSegmentType.WORD)
                }
            }

            index = nextIndex
        }

        flushWord()
        return segments
    }
}
