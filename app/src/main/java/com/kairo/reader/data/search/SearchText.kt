package com.kairo.reader.data.search

import com.kairo.reader.core.model.LibrarySearchResult
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.text.TokenTextPositionResolver

fun normalizeLibrarySearchQuery(query: String): String =
    query
        .take(LibrarySearchConstraints.MAX_RAW_QUERY_LENGTH)
        .trim()
        .take(LibrarySearchConstraints.MAX_QUERY_LENGTH)

internal fun String.toSqlLikePattern(): String =
    "%" +
        lowercase()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_") +
        "%"

internal fun findSearchMatchOffsets(
    text: String,
    query: String,
    limit: Int,
): List<Int> {
    if (query.isEmpty() || limit <= 0) return emptyList()
    val offsets = mutableListOf<Int>()
    var searchFrom = 0
    while (offsets.size < limit) {
        val match = text.indexOf(query, startIndex = searchFrom, ignoreCase = true)
        if (match < 0) break
        offsets += match
        searchFrom = match + query.length
    }
    return offsets
}

internal fun buildSearchSnippet(
    text: String,
    matchOffset: Int,
    matchLength: Int,
    contextCharacters: Int,
): String {
    val boundedMatchOffset = matchOffset.coerceIn(0, text.length)
    val boundedMatchEnd =
        (boundedMatchOffset.toLong() + matchLength.coerceAtLeast(0))
            .coerceAtMost(text.length.toLong())
            .toInt()
    val boundedContextCharacters = contextCharacters.coerceAtLeast(0)
    val start =
        text.offsetByCodePoints(
            boundedMatchOffset,
            -minOf(boundedContextCharacters, text.codePointCount(0, boundedMatchOffset)),
        )
    val end =
        text.offsetByCodePoints(
            boundedMatchEnd,
            minOf(boundedContextCharacters, text.codePointCount(boundedMatchEnd, text.length)),
        )
    val body = text.substring(start, end).replace(Regex("\\s+"), " ").trim()
    return buildString {
        if (start > 0) append(ELLIPSIS)
        append(body)
        if (end < text.length) append(ELLIPSIS)
    }
}

internal fun resolveSearchTokenRange(
    plainText: String,
    tokens: List<Token>,
    matchOffset: Int,
    matchLength: Int,
): IntRange {
    val start =
        TokenTextPositionResolver.resolveTokenIndex(
            plainText = plainText,
            tokens = tokens,
            characterOffset = matchOffset,
        )
    val end =
        TokenTextPositionResolver.resolveTokenIndex(
            plainText = plainText,
            tokens = tokens,
            characterOffset = (matchOffset + matchLength - 1).coerceAtLeast(matchOffset),
        )
    return minOf(start, end)..maxOf(start, end)
}

internal fun resolveSearchResultTokenRange(
    result: LibrarySearchResult,
    plainText: String,
    tokens: List<Token>,
): IntRange =
    result.matchStartCodePointOffset
        ?.takeIf { result.matchLengthCodePoints > 0 }
        ?.let { startCodePointOffset ->
            val startUtf16Offset =
                codePointOffsetToUtf16Offset(plainText, startCodePointOffset)
            val endCodePointOffset =
                (startCodePointOffset.toLong() + result.matchLengthCodePoints.toLong())
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            val endUtf16Offset =
                codePointOffsetToUtf16Offset(plainText, endCodePointOffset)
            resolveSearchTokenRange(
                plainText = plainText,
                tokens = tokens,
                matchOffset = startUtf16Offset,
                matchLength = (endUtf16Offset - startUtf16Offset).coerceAtLeast(1),
            )
        } ?: result.tokenRange

internal fun codePointOffsetToUtf16Offset(
    text: String,
    codePointOffset: Int,
): Int {
    val codePointCount = text.codePointCount(0, text.length)
    return text.offsetByCodePoints(0, codePointOffset.coerceIn(0, codePointCount))
}

internal fun fairMergeSearchResults(
    groups: List<List<com.kairo.reader.core.model.LibrarySearchResult>>,
    limit: Int,
): List<com.kairo.reader.core.model.LibrarySearchResult> {
    if (limit <= 0) return emptyList()
    val cursors = IntArray(groups.size)
    return buildList {
        while (size < limit) {
            var added = false
            groups.forEachIndexed { groupIndex, group ->
                if (size >= limit) return@forEachIndexed
                val cursor = cursors[groupIndex]
                if (cursor < group.size) {
                    add(group[cursor])
                    cursors[groupIndex] = cursor + 1
                    added = true
                }
            }
            if (!added) break
        }
    }
}

private const val ELLIPSIS = "…"
