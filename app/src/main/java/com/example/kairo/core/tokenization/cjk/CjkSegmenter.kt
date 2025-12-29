package com.example.kairo.core.tokenization.cjk

import com.example.kairo.core.model.Token

internal class CjkSegmenter(private val config: CjkSegmenterConfig) {
    fun tokenizeParagraph(paragraph: String): List<Token> =
        segment(paragraph).map { segment ->
            when (segment.type) {
                CjkSegmentType.WORD -> CjkTokenFactory.word(segment.text, segment.isLatin)
                CjkSegmentType.PUNCTUATION -> CjkTokenFactory.punctuation(segment.text)
            }
        }

    fun tokenizeInlineText(text: String): List<String> =
        segment(text).map { it.text }

    private fun segment(text: String): List<CjkSegment> {
        if (text.isBlank()) return emptyList()

        val segments = mutableListOf<CjkSegment>()
        val latinBuffer = StringBuilder()
        val cjkBuffer = StringBuilder()
        val maxCjkChars = config.maxCjkCharsPerToken.coerceAtLeast(1)
        var cjkCount = 0

        fun flushLatin() {
            if (latinBuffer.isNotEmpty()) {
                segments +=
                    CjkSegment(
                        text = latinBuffer.toString(),
                        type = CjkSegmentType.WORD,
                        isLatin = true,
                    )
                latinBuffer.clear()
            }
        }

        fun flushCjk(force: Boolean) {
            if (cjkBuffer.isEmpty()) return
            if (!force && cjkCount < maxCjkChars) return
            segments +=
                CjkSegment(
                    text = cjkBuffer.toString(),
                    type = CjkSegmentType.WORD,
                )
            cjkBuffer.clear()
            cjkCount = 0
        }

        fun flushAll() {
            flushLatin()
            flushCjk(force = true)
        }

        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            val tokenText = String(Character.toChars(codePoint))
            val nextIndex = index + charCount
            val nextCodePoint = if (nextIndex < text.length) text.codePointAt(nextIndex) else null

            when {
                CjkCharClassifier.isWhitespace(codePoint) -> {
                    flushAll()
                }
                config.treatHangulAsWord && CjkCharClassifier.isHangul(codePoint) -> {
                    flushCjk(force = true)
                    latinBuffer.append(tokenText)
                }
                CjkCharClassifier.isCjk(codePoint) -> {
                    flushLatin()
                    cjkBuffer.append(tokenText)
                    cjkCount += 1
                    if (cjkCount >= maxCjkChars) {
                        flushCjk(force = true)
                    }
                }
                CjkCharClassifier.isWordConnector(codePoint) -> {
                    val canJoin =
                        latinBuffer.isNotEmpty() &&
                            nextCodePoint != null &&
                            CjkCharClassifier.isLatinLike(nextCodePoint)
                    if (canJoin) {
                        latinBuffer.append(tokenText)
                    } else {
                        flushAll()
                        segments += CjkSegment(tokenText, CjkSegmentType.PUNCTUATION)
                    }
                }
                CjkCharClassifier.isPunctuation(codePoint) -> {
                    flushAll()
                    segments += CjkSegment(tokenText, CjkSegmentType.PUNCTUATION)
                }
                CjkCharClassifier.isCombiningMark(codePoint) -> {
                    when {
                        latinBuffer.isNotEmpty() -> latinBuffer.append(tokenText)
                        cjkBuffer.isNotEmpty() -> cjkBuffer.append(tokenText)
                        else -> segments += CjkSegment(tokenText, CjkSegmentType.WORD)
                    }
                }
                CjkCharClassifier.isLatinLike(codePoint) -> {
                    flushCjk(force = true)
                    latinBuffer.append(tokenText)
                }
                else -> {
                    flushAll()
                    segments += CjkSegment(tokenText, CjkSegmentType.WORD)
                }
            }

            index = nextIndex
        }

        flushAll()
        return segments
    }
}
