package com.example.kairo.core.tokenization

import com.example.kairo.core.model.ChapterLink
import com.example.kairo.core.model.Token

internal object LinkPositionMapper {
    fun apply(
        tokens: MutableList<Token>,
        links: List<ChapterLink>,
        plainText: String,
    ) {
        if (links.isEmpty() || tokens.isEmpty()) return
        val sortedLinks =
            if (links.size <= 1) {
                links
            } else {
                links.sortedBy { it.startChar }
            }
        var linkIndex = 0
        var currentLink: ChapterLink? = sortedLinks.getOrNull(linkIndex) ?: return
        var searchFrom = 0

        tokens.forEachIndexed { index, token ->
            val range = locateTokenRange(plainText, token.text, searchFrom)
            val tokenStart = range?.start ?: searchFrom.coerceIn(0, plainText.length)
            val tokenEnd = range?.endExclusive ?: (tokenStart + token.text.length).coerceAtMost(plainText.length)

            while (currentLink != null && tokenStart >= currentLink.endChar) {
                linkIndex += 1
                currentLink = sortedLinks.getOrNull(linkIndex)
            }

            val linkChapterIndex =
                if (currentLink != null &&
                    tokenStart < currentLink.endChar &&
                    tokenEnd > currentLink.startChar
                ) {
                    currentLink.targetChapterIndex
                } else {
                    null
                }

            searchFrom = tokenEnd.coerceAtLeast(tokenStart)
            if (linkChapterIndex != null && token.linkChapterIndex == null) {
                tokens[index] = token.copy(linkChapterIndex = linkChapterIndex)
            }
        }
    }

    private fun locateTokenRange(
        plainText: String,
        tokenText: String,
        searchFrom: Int,
    ): TokenRange? {
        if (tokenText.isEmpty()) {
            val safeIndex = searchFrom.coerceIn(0, plainText.length)
            return TokenRange(safeIndex, safeIndex)
        }
        val safeSearchFrom = searchFrom.coerceIn(0, plainText.length)
        val variants = buildSearchVariants(tokenText)
        val candidateStarts =
            buildSet {
                add(safeSearchFrom)
                val nextNonWhitespace = nextNonWhitespaceIndex(plainText, safeSearchFrom)
                if (nextNonWhitespace >= 0) {
                    add(nextNonWhitespace)
                }
            }

        candidateStarts.forEach { start ->
            variants.forEach { variant ->
                if (plainText.regionMatches(start, variant, 0, variant.length)) {
                    return TokenRange(start, start + variant.length)
                }
            }
        }

        var bestStart = Int.MAX_VALUE
        var bestLength = 0
        variants.forEach { variant ->
            val found = plainText.indexOf(variant, safeSearchFrom)
            if (found != -1 && found < bestStart) {
                bestStart = found
                bestLength = variant.length
            }
        }
        return if (bestStart == Int.MAX_VALUE) {
            null
        } else {
            TokenRange(bestStart, bestStart + bestLength)
        }
    }

    private fun buildSearchVariants(tokenText: String): List<String> =
        buildSet {
            add(tokenText)
            when (tokenText) {
                "\u201C", "\u201D" -> add("\"")
                "\u2018", "\u2019" -> add("'")
                "\u2026" -> add("...")
                "\u2014" -> add("--")
                "\u2013" -> add("-")
            }
        }.toList()

    private fun nextNonWhitespaceIndex(
        text: String,
        startIndex: Int,
    ): Int {
        var index = startIndex.coerceAtLeast(0)
        while (index < text.length) {
            if (!text[index].isWhitespace()) return index
            index += 1
        }
        return -1
    }

    private data class TokenRange(val start: Int, val endExclusive: Int)
}
