package com.kairo.reader.data.books.mobi

import com.kairo.reader.core.text.HtmlEntities
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

    fun decodeHtmlEntities(text: String): String = HtmlEntities.decode(text)

    fun decodeFragment(fragment: String): String {
        if (!fragment.contains('%')) return fragment
        return runCatching { URLDecoder.decode(fragment, "UTF-8") }.getOrDefault(fragment)
    }

    fun decodePath(path: String): String =
        runCatching { URLDecoder.decode(path, "UTF-8") }.getOrDefault(path)
}
