package com.example.kairo.data.books

import java.util.Locale

internal sealed interface EpubMarkupToken

internal data class EpubStartTagToken(
    val name: String,
    val attributes: Map<String, String>,
    val selfClosing: Boolean,
) : EpubMarkupToken

internal data class EpubEndTagToken(
    val name: String,
) : EpubMarkupToken

internal data class EpubTextToken(
    val text: String,
) : EpubMarkupToken

internal class EpubMarkupTokenizer {
    companion object {
        private val ATTRIBUTE_REGEX =
            Regex("""([A-Za-z_:][A-Za-z0-9_:\-\.]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?""")
    }

    fun tokenize(input: String): List<EpubMarkupToken> {
        val out = mutableListOf<EpubMarkupToken>()
        var index = 0

        while (index < input.length) {
            val openIndex = input.indexOf('<', index)
            if (openIndex == -1) {
                if (index < input.length) {
                    out.add(EpubTextToken(input.substring(index)))
                }
                break
            }

            if (openIndex > index) {
                out.add(EpubTextToken(input.substring(index, openIndex)))
            }

            if (input.startsWith("<!--", openIndex)) {
                val closeComment = input.indexOf("-->", openIndex + 4)
                index = if (closeComment == -1) input.length else closeComment + 3
                continue
            }

            val closeIndex = findTagClose(input, openIndex + 1)
            if (closeIndex == -1) {
                out.add(EpubTextToken(input.substring(openIndex)))
                break
            }

            val rawTag = input.substring(openIndex + 1, closeIndex)
            val token = parseTagToken(rawTag)
            if (token != null) {
                out.add(token)
            }

            index = closeIndex + 1
        }

        return out
    }

    private fun parseTagToken(rawTag: String): EpubMarkupToken? {
        val trimmed = rawTag.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("!") || trimmed.startsWith("?")) return null

        if (trimmed.startsWith("/")) {
            val name = parseTagName(trimmed.substring(1).trimStart()) ?: return null
            return EpubEndTagToken(name)
        }

        var body = trimmed
        val selfClosing = body.endsWith("/")
        if (selfClosing) {
            body = body.dropLast(1).trimEnd()
        }

        val name = parseTagName(body) ?: return null
        val attributeSection = body.substring(name.length)
        val attributes = parseAttributes(attributeSection)
        return EpubStartTagToken(
            name = name,
            attributes = attributes,
            selfClosing = selfClosing,
        )
    }

    private fun parseTagName(input: String): String? {
        if (input.isBlank()) return null
        var end = 0
        while (end < input.length && isTagNameChar(input[end])) {
            end += 1
        }
        if (end <= 0) return null
        return input.substring(0, end).lowercase(Locale.ROOT)
    }

    private fun isTagNameChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == ':' || char == '_' || char == '-' || char == '.'
    }

    private fun parseAttributes(input: String): Map<String, String> {
        if (input.isBlank()) return emptyMap()

        val attributes = LinkedHashMap<String, String>()
        ATTRIBUTE_REGEX.findAll(input).forEach { match ->
            val name =
                match.groupValues[1]
                    .trim()
                    .lowercase(Locale.ROOT)
            if (name.isBlank()) return@forEach

            val value =
                when {
                    match.groupValues[2].isNotEmpty() -> match.groupValues[2]
                    match.groupValues[3].isNotEmpty() -> match.groupValues[3]
                    match.groupValues[4].isNotEmpty() -> match.groupValues[4]
                    else -> ""
                }

            attributes[name] = value
        }
        return attributes
    }

    private fun findTagClose(
        input: String,
        startIndex: Int,
    ): Int {
        var quote: Char? = null
        var index = startIndex
        while (index < input.length) {
            val char = input[index]
            if (quote != null) {
                if (char == quote) {
                    quote = null
                }
                index += 1
                continue
            }

            if (char == '"' || char == '\'') {
                quote = char
                index += 1
                continue
            }

            if (char == '>') {
                return index
            }

            index += 1
        }
        return -1
    }
}
