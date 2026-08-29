package com.kairo.reader.data.books

import java.util.Locale

internal sealed interface EpubMarkupToken

internal data class EpubStartTagToken(val name: String, val attributes: Map<String, String>, val selfClosing: Boolean,) : EpubMarkupToken

internal data class EpubEndTagToken(val name: String,) : EpubMarkupToken

internal data class EpubTextToken(val text: String,) : EpubMarkupToken

internal class EpubMarkupTokenization internal constructor(
    val tokens: Sequence<EpubMarkupToken>,
    private val state: EpubMarkupTokenizationState,
) {
    val complete: Boolean
        get() = state.complete && !state.limitExceeded

    val limitExceeded: Boolean
        get() = state.limitExceeded
}

internal class EpubMarkupTokenizationState(var complete: Boolean = true, var limitExceeded: Boolean = false,)

internal class EpubMarkupTokenizer {
    companion object {
        private val ATTRIBUTE_REGEX =
            Regex("""([A-Za-z_:][A-Za-z0-9_:\-\.]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?""")
        private val RAW_TEXT_ELEMENTS = setOf("script", "style")
        private const val COMMENT_OPEN_LENGTH = 4
        private const val COMMENT_CLOSE_LENGTH = 3
        private const val DOUBLE_QUOTED_VALUE_GROUP = 2
        private const val SINGLE_QUOTED_VALUE_GROUP = 3
        private const val UNQUOTED_VALUE_GROUP = 4
        private const val MAX_INPUT_CHARACTERS = 5 * 1024 * 1024
        private const val MAX_TOKENS = 100_000
        private const val MAX_TAG_CHARACTERS = 32_768
        private const val MAX_ATTRIBUTES_PER_TAG = 128
        private const val MAX_TEXT_TOKEN_CHARACTERS = 65_536
    }

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    fun tokenize(input: String): EpubMarkupTokenization {
        val state = EpubMarkupTokenizationState()
        if (input.length > MAX_INPUT_CHARACTERS) {
            state.complete = false
            state.limitExceeded = true
            return EpubMarkupTokenization(emptySequence(), state)
        }

        val tokens =
            sequence {
                var index = 0
                var tokenCount = 0

                suspend fun SequenceScope<EpubMarkupToken>.emitToken(token: EpubMarkupToken): Boolean {
                    if (tokenCount >= MAX_TOKENS) {
                        state.complete = false
                        state.limitExceeded = true
                        return false
                    }
                    tokenCount += 1
                    yield(token)
                    return true
                }

                suspend fun SequenceScope<EpubMarkupToken>.emitText(
                    start: Int,
                    endExclusive: Int,
                ): Boolean {
                    var cursor = start
                    while (cursor < endExclusive) {
                        val chunkEnd = minOf(cursor + MAX_TEXT_TOKEN_CHARACTERS, endExclusive)
                        if (!emitToken(EpubTextToken(input.substring(cursor, chunkEnd)))) return false
                        cursor = chunkEnd
                    }
                    return true
                }

                while (index < input.length && !state.limitExceeded) {
                    val openIndex = input.indexOf('<', index)
                    if (openIndex == -1) {
                        emitText(index, input.length)
                        break
                    }

                    if (openIndex > index && !emitText(index, openIndex)) break

                    if (input.startsWith("<!--", openIndex)) {
                        val closeComment = input.indexOf("-->", openIndex + COMMENT_OPEN_LENGTH)
                        if (closeComment == -1) {
                            state.complete = false
                            break
                        }
                        index = closeComment + COMMENT_CLOSE_LENGTH
                        continue
                    }

                    val closeIndex = findTagClose(input, openIndex + 1)
                    if (closeIndex == -1) {
                        state.complete = false
                        if (input.length - openIndex - 1 > MAX_TAG_CHARACTERS) {
                            state.limitExceeded = true
                            break
                        }
                        emitText(openIndex, input.length)
                        break
                    }
                    if (closeIndex - openIndex - 1 > MAX_TAG_CHARACTERS) {
                        state.complete = false
                        state.limitExceeded = true
                        break
                    }

                    val parsedTag = parseTagToken(input.substring(openIndex + 1, closeIndex))
                    if (parsedTag.limitExceeded) {
                        state.complete = false
                        state.limitExceeded = true
                        break
                    }
                    val token = parsedTag.token
                    if (token != null && !emitToken(token)) break
                    index = closeIndex + 1

                    if (token is EpubStartTagToken &&
                        !token.selfClosing &&
                        token.name in RAW_TEXT_ELEMENTS
                    ) {
                        val rawCloseOpen = findRawTextClose(input, token.name, index)
                        if (rawCloseOpen == -1) {
                            emitText(index, input.length)
                            state.complete = false
                            break
                        }
                        if (!emitText(index, rawCloseOpen)) break
                        val rawCloseEnd = findTagClose(input, rawCloseOpen + 2)
                        if (rawCloseEnd == -1 || rawCloseEnd - rawCloseOpen - 1 > MAX_TAG_CHARACTERS) {
                            state.complete = false
                            if (rawCloseEnd != -1 ||
                                input.length - rawCloseOpen - 1 > MAX_TAG_CHARACTERS
                            ) {
                                state.limitExceeded = true
                            }
                            break
                        }
                        if (!emitToken(EpubEndTagToken(token.name))) break
                        index = rawCloseEnd + 1
                    }
                }
            }

        return EpubMarkupTokenization(tokens, state)
    }

    private fun parseTagToken(rawTag: String): ParsedTagToken {
        val trimmed = rawTag.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("?")) {
            return ParsedTagToken()
        }

        if (trimmed.startsWith("/")) {
            val name = parseTagName(trimmed.substring(1).trimStart()) ?: return ParsedTagToken()
            return ParsedTagToken(token = EpubEndTagToken(name))
        }

        var body = trimmed
        val selfClosing = body.endsWith("/")
        if (selfClosing) {
            body = body.dropLast(1).trimEnd()
        }

        val name = parseTagName(body) ?: return ParsedTagToken()
        val parsedAttributes = parseAttributes(body.substring(name.length))
        if (parsedAttributes.limitExceeded) return ParsedTagToken(limitExceeded = true)
        return ParsedTagToken(
            token =
            EpubStartTagToken(
                name = name,
                attributes = parsedAttributes.attributes,
                selfClosing = selfClosing,
            ),
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

    private fun parseAttributes(input: String): ParsedAttributes {
        if (input.isBlank()) return ParsedAttributes()

        val attributes = LinkedHashMap<String, String>()
        var attributeCount = 0
        for (match in ATTRIBUTE_REGEX.findAll(input)) {
            if (attributeCount >= MAX_ATTRIBUTES_PER_TAG) {
                return ParsedAttributes(limitExceeded = true)
            }
            attributeCount += 1
            val name =
                match.groupValues[1]
                    .trim()
                    .lowercase(Locale.ROOT)
            if (name.isBlank()) continue

            val value =
                when {
                    match.groupValues[DOUBLE_QUOTED_VALUE_GROUP].isNotEmpty() ->
                        match.groupValues[DOUBLE_QUOTED_VALUE_GROUP]
                    match.groupValues[SINGLE_QUOTED_VALUE_GROUP].isNotEmpty() ->
                        match.groupValues[SINGLE_QUOTED_VALUE_GROUP]
                    match.groupValues[UNQUOTED_VALUE_GROUP].isNotEmpty() ->
                        match.groupValues[UNQUOTED_VALUE_GROUP]
                    else -> ""
                }

            attributes[name] = value
        }
        return ParsedAttributes(attributes = attributes)
    }

    private fun findTagClose(
        input: String,
        startIndex: Int,
    ): Int {
        var quote: Char? = null
        var index = startIndex
        val maxEnd = minOf(input.length, startIndex + MAX_TAG_CHARACTERS + 1)
        while (index < maxEnd) {
            val char = input[index]
            if (quote != null) {
                if (char == quote) quote = null
                index += 1
                continue
            }

            if (char == '"' || char == '\'') {
                quote = char
            } else if (char == '>') {
                return index
            }
            index += 1
        }
        return if (index < input.length) index else -1
    }

    private fun findRawTextClose(
        input: String,
        tagName: String,
        startIndex: Int,
    ): Int {
        var candidate = input.indexOf("</$tagName", startIndex, ignoreCase = true)
        while (candidate != -1) {
            val delimiterIndex = candidate + tagName.length + 2
            val delimiter = input.getOrNull(delimiterIndex)
            if (delimiter == null || delimiter.isWhitespace() || delimiter == '>') return candidate
            candidate = input.indexOf("</$tagName", candidate + 2, ignoreCase = true)
        }
        return -1
    }

    private data class ParsedTagToken(val token: EpubMarkupToken? = null, val limitExceeded: Boolean = false,)

    private data class ParsedAttributes(val attributes: Map<String, String> = emptyMap(), val limitExceeded: Boolean = false,)
}
