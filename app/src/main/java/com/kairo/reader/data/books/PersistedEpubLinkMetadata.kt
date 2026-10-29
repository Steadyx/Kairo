package com.kairo.reader.data.books

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.data.books.epub.EpubPathResolver

/** Annotates existing links without changing their chapter indices or stored asset paths. */
internal fun annotatePersistedEpubLinks(
    chapters: List<Chapter>,
    linearity: Map<Int, Boolean>,
): Map<Int, String>? {
    val targets = chapters.associateBy { it.index }
    if (targets.size != chapters.size || targets.keys != linearity.keys) return null
    val rewriter = EpubContentRewriter()
    val anchors = mutableMapOf<Int, Map<String, Int>>()
    var ambiguous = false
    val result = chapters.associate { chapter ->
        val html = INTERNAL_LINK.replace(chapter.htmlContent) { match ->
            val targetIndex = match.groupValues[2].toIntOrNull() ?: return@replace match.value
            val target = targets[targetIndex] ?: return@replace match.value
            val fragment = EpubPathResolver.decodeUrlPath(match.groupValues[FRAGMENT_GROUP_INDEX]).trim()
            val offset = if (fragment.isNotEmpty()) {
                val targetAnchors = anchors.getOrPut(targetIndex) {
                    val plain = rewriter.extractPlainTextWithAnchors(target.htmlContent)
                    if (plain.text != target.plainText) ambiguous = true
                    plain.anchorOffsets
                }
                val exact = targetAnchors[fragment]
                val matches = targetAnchors.filterKeys { it.equals(fragment, ignoreCase = true) }.values.toSet()
                if (exact == null && matches.size != 1) ambiguous = true
                exact ?: matches.singleOrNull()
            } else {
                null
            }
            addEpubLinkAttributes(match.value, linearity[targetIndex] == false, offset)
        }
        chapter.index to html
    }
    return result.takeUnless { ambiguous }
}

internal fun addEpubLinkAttributes(openTag: String, isAuxiliary: Boolean, offset: Int?): String {
    val clean = AUDIT_ATTRIBUTE.replace(openTag, "")
    val attributes = buildString {
        if (isAuxiliary) append(" data-kairo-aux=\"1\"")
        if (offset != null) append(" data-kairo-offset=\"$offset\"")
    }
    val closing = if (clean.endsWith("/>")) "/>" else ">"
    return clean.dropLast(closing.length) + attributes + closing
}

private val INTERNAL_LINK = Regex(
    """<a\b[^>]*\bhref\s*=\s*(['"])kairo://chapter/(\d+)(?:#([^'"]*))?\1[^>]*>""",
    RegexOption.IGNORE_CASE,
)
private val AUDIT_ATTRIBUTE = Regex(
    """\s+data-kairo-(?:aux|offset)\s*=\s*(['"])[^'"]*\1""",
    RegexOption.IGNORE_CASE,
)
private const val FRAGMENT_GROUP_INDEX = 3
