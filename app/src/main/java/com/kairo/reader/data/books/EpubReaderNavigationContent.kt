package com.kairo.reader.data.books

import com.kairo.reader.data.books.epub.EpubHtmlEntities
import com.kairo.reader.data.books.epub.EpubNavigationReference

internal data class EpubNavigationParseResult(
    val references: List<EpubNavigationReference>,
    val readerHtml: String? = null,
    val provenance: EpubNavigationProvenance = EpubNavigationProvenance.NONE,
    val navigationOnly: Boolean = false,
    val complete: Boolean = true,
    val heading: EpubReaderNavigationHeading? = null,
    val ariaLabel: String? = null,
) {
    val repairEligible: Boolean
        get() =
            complete &&
                provenance == EpubNavigationProvenance.EXPLICIT_TOC &&
                navigationOnly &&
                references.isNotEmpty()
}

internal enum class EpubNavigationProvenance {
    NONE,
    EXPLICIT_TOC,
    UNTYPED_FALLBACK,
    NCX,
}

internal data class EpubReaderNavigationHeading(val level: Int, val text: String,)

internal object EpubReaderNavigationContent {
    const val MARKER = "data-kairo-reader-nav=\"toc-v2\""

    fun renderPersisted(
        result: EpubNavigationParseResult,
        validChapterIndexes: Set<Int>,
    ): String? =
        render(
            references =
            result.references.map { reference ->
                reference.copy(
                    href = persistedChapterHref(reference.href, validChapterIndexes),
                )
            },
            heading = result.heading,
            ariaLabel = result.ariaLabel,
        )

    fun render(
        references: List<EpubNavigationReference>,
        heading: EpubReaderNavigationHeading?,
        ariaLabel: String?,
    ): String? {
        if (references.isEmpty()) return null

        return buildString {
            append("<html><body><nav ")
            append(MARKER)
            ariaLabel?.let { label ->
                append(" aria-label=\"")
                append(escapeHtml(label))
                append('\"')
            }
            append('>')
            heading?.let { authoredHeading ->
                append("<h")
                append(authoredHeading.level)
                append('>')
                append(escapeHtml(authoredHeading.text))
                append("</h")
                append(authoredHeading.level)
                append('>')
            }
            append("<ol>")
            references.forEach { reference ->
                append("<li data-kairo-depth=\"")
                append(reference.depth)
                append("\">")
                val href = reference.href
                if (href == null) {
                    append("<span>")
                    append(escapeHtml(reference.label))
                    append("</span>")
                } else {
                    append("<a href=\"")
                    append(escapeHtml(EpubHtmlEntities.decode(href)))
                    append("\">")
                    append(escapeHtml(reference.label))
                    append("</a>")
                }
                append("</li>")
            }
            append("</ol></nav></body></html>")
        }
    }

    private fun escapeHtml(value: String): String =
        buildString(value.length) {
            value.forEach { character ->
                when (character) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '\"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(character)
                }
            }
        }

    private fun persistedChapterHref(
        rawHref: String?,
        validChapterIndexes: Set<Int>,
    ): String? {
        val href = rawHref?.take(MAX_PERSISTED_HREF_CHARACTERS) ?: return null
        val match = PERSISTED_CHAPTER_HREF.matchEntire(href) ?: return null
        val chapterIndex = match.groupValues[1].toIntOrNull() ?: return null
        return href.takeIf { chapterIndex in validChapterIndexes }
    }

    private val PERSISTED_CHAPTER_HREF =
        Regex("""kairo://chapter/([0-9]{1,10})(#[A-Za-z0-9._~!&'()*+,;=:@%/?-]{1,512})?""")
    private const val MAX_PERSISTED_HREF_CHARACTERS = 640
}
