package com.example.kairo.data.books.mobi

import java.net.URLDecoder

internal object MobiHtmlUtils {
    fun extractAttribute(
        tag: String,
        name: String,
    ): String? {
        val regex =
            Regex(
                """\b${Regex.escape(name)}\s*=\s*(?:['"]([^'"]+)['"]|([^\s>]+))""",
                RegexOption.IGNORE_CASE,
            )
        val match = regex.find(tag) ?: return null
        return match.groupValues.getOrNull(1)?.ifBlank { null }
            ?: match.groupValues.getOrNull(2)?.ifBlank { null }
    }

    fun decodeHtmlEntities(text: String): String =
        text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
            }.replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1].toIntOrNull(16)?.toChar()?.toString().orEmpty()
            }

    fun decodeFragment(fragment: String): String {
        if (!fragment.contains('%')) return fragment
        return runCatching { URLDecoder.decode(fragment, "UTF-8") }.getOrDefault(fragment)
    }

    fun decodePath(path: String): String =
        runCatching { URLDecoder.decode(path, "UTF-8") }.getOrDefault(path)
}
