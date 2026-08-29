package com.kairo.reader.data.books

import com.kairo.reader.data.books.epub.EpubHtmlEntities
import com.kairo.reader.data.books.epub.EpubPathResolver
import java.util.Locale

internal data class EpubPlainTextContent(val text: String, val anchorOffsets: Map<String, Int>,)

internal class EpubContentRewriter {
    private companion object {
        const val MAX_LINKS_PER_CHAPTER = 1000
        const val MAX_NOISE_TITLE_LENGTH = 32
        const val PAGE_BREAK_MARKER = "\u000C"
        val FILE_LABEL_WITH_NUMBER_REGEX = Regex("(?i)^(part|chapter|section|book)(0*)(\\d{1,6})$")
        val GENERIC_FILE_LABEL_REGEX = Regex("(?i)^[a-z]{2,}\\d{3,}$")
        val HTML_COMMENT_REGEX = Regex("<!--[\\s\\S]*?-->")
        val SCRIPT_TAG_REGEX = Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
        val STYLE_TAG_REGEX = Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
        val ALL_TAGS_REGEX = Regex("<[^>]+>")
        val HORIZONTAL_WHITESPACE_REGEX = Regex("[ \\t]+")
        val MULTIPLE_NEWLINES_REGEX = Regex("\\n\\s*\\n+")
        val WHITESPACE_REGEX = Regex("\\s+")
        val IMG_SRC_REWRITE_REGEX =
            Regex(
                "(<img\\b[^>]*?\\bsrc\\s*=\\s*['\"])" +
                    "([^'\"]+)(['\"][^>]*>)",
                RegexOption.IGNORE_CASE,
            )
        val SVG_IMAGE_REWRITE_REGEX =
            Regex(
                "(<image\\b[^>]*?\\b(?:xlink:href|href)\\s*=\\s*['\"])" +
                    "([^'\"]+)(['\"][^>]*>)",
                RegexOption.IGNORE_CASE,
            )
        val SRCSET_REWRITE_REGEX =
            Regex(
                "(<(?:img|source)\\b[^>]*?\\bsrcset\\s*=\\s*['\"])" +
                    "([^'\"]+)(['\"][^>]*>)",
                RegexOption.IGNORE_CASE,
            )
        val IMAGE_SOURCE_SRC_REGEX =
            Regex(
                """<(?:img|source)\b[^>]*?\bsrc\s*=""" +
                    """\s*(['"])(.*?)\1""",
                RegexOption.IGNORE_CASE,
            )
        val IMAGE_SOURCE_SRCSET_REGEX =
            Regex(
                """<(?:img|source)\b[^>]*?\bsrcset\s*=""" +
                    """\s*(['"])(.*?)\1""",
                RegexOption.IGNORE_CASE,
            )
        val SVG_IMAGE_HREF_REGEX =
            Regex(
                """<image\b[^>]*?\b(?:xlink:href|href)\s*=""" +
                    """\s*(['"])(.*?)\1""",
                RegexOption.IGNORE_CASE,
            )
        val ANCHOR_TAG_REGEX = Regex("<a\\b", RegexOption.IGNORE_CASE)
        val ANCHOR_HREF_REGEX = Regex("(<a\\b[^>]*href\\s*=\\s*['\"])([^'\"]+)(['\"][^>]*>)", RegexOption.IGNORE_CASE)
        val ANCHOR_MARKER_REGEX = Regex("\uE000[0-9a-z]+\uE001")
        val BLOCK_ELEMENT_REGEX = Regex("(?is)<(h[1-6]|p|div)[^>]*>([\\s\\S]*?)</\\1>")
    }

    private val markupParser = EpubMarkupParser()

    fun buildChapterImagePaths(
        html: String,
        baseDir: String,
        imageRelativePathByEpubPathLower: Map<String, String>,
        chapterSrcs: List<String>? = null,
    ): List<String> {
        val srcs = chapterSrcs ?: extractImageSrcs(html)
        return srcs.mapNotNull { rawSrc ->
            rewriteImageReference(rawSrc, baseDir, imageRelativePathByEpubPathLower)
                ?.substringBefore('#')
                ?.substringBefore('?')
        }.distinct()
    }

    data class HrefParts(val path: String, val suffix: String, val fragment: String,)

    fun extractImageSrcs(html: String): List<String> {
        if (!hasImageReferences(html)) return emptyList()
        val sources = mutableListOf<String>()
        IMAGE_SOURCE_SRC_REGEX.findAll(html).forEach { match ->
            match.groupValues.getOrNull(2)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(sources::add)
        }
        IMAGE_SOURCE_SRCSET_REGEX.findAll(html).forEach { match ->
            val srcset = match.groupValues.getOrNull(2).orEmpty()
            sources += extractSrcsetUrls(srcset)
        }
        SVG_IMAGE_HREF_REGEX.findAll(html).forEach { match ->
            match.groupValues.getOrNull(2)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(sources::add)
        }
        return sources
    }

    fun hasImageReferences(html: String): Boolean =
        html.indexOf("<img", ignoreCase = true) >= 0 ||
            html.indexOf("<source", ignoreCase = true) >= 0 ||
            html.indexOf("<image", ignoreCase = true) >= 0 ||
            html.indexOf("srcset", ignoreCase = true) >= 0

    fun sanitizeSrc(src: String): String {
        val trimmed = src.trim()
        if (trimmed.isBlank()) return ""
        return normalizeHrefValue(trimmed)
    }

    fun normalizeHrefValue(href: String): String {
        return EpubPathResolver.normalizeHrefValue(href)
    }

    fun splitHrefParts(rawHref: String): HrefParts {
        val decoded = decodeHtmlEntities(rawHref).trim()
        if (decoded.isBlank()) {
            return HrefParts(path = "", suffix = "", fragment = "")
        }
        val queryIndex = decoded.indexOf('?').takeIf { it >= 0 } ?: Int.MAX_VALUE
        val fragmentIndex = decoded.indexOf('#').takeIf { it >= 0 } ?: Int.MAX_VALUE
        val splitIndex = minOf(queryIndex, fragmentIndex)
        val path =
            if (splitIndex == Int.MAX_VALUE) {
                decoded
            } else {
                decoded.take(splitIndex)
            }
        val suffix =
            if (splitIndex == Int.MAX_VALUE) {
                ""
            } else {
                decoded.substring(splitIndex)
            }
        val fragment = decoded.substringAfter('#', "").substringBefore('?')
        return HrefParts(path = path, suffix = suffix, fragment = fragment)
    }

    fun rewriteHtmlImageSrcs(
        html: String,
        baseDir: String,
        imageRelativePathByEpubPathLower: Map<String, String>,
    ): String {
        if (imageRelativePathByEpubPathLower.isEmpty() || !hasImageReferences(html)) {
            return html
        }

        val rewrittenImgSrc =
            IMG_SRC_REWRITE_REGEX.replace(html) { match ->
                val rewritten = rewriteImageReference(match.groupValues[2], baseDir, imageRelativePathByEpubPathLower)
                if (rewritten == null) {
                    match.value
                } else {
                    "${match.groupValues[1]}$rewritten${match.groupValues[REGEX_SUFFIX_GROUP]}"
                }
            }
        val rewrittenSvgHref =
            SVG_IMAGE_REWRITE_REGEX.replace(rewrittenImgSrc) { match ->
                val rewritten = rewriteImageReference(match.groupValues[2], baseDir, imageRelativePathByEpubPathLower)
                if (rewritten == null) {
                    match.value
                } else {
                    "${match.groupValues[1]}$rewritten${match.groupValues[REGEX_SUFFIX_GROUP]}"
                }
            }
        return SRCSET_REWRITE_REGEX.replace(rewrittenSvgHref) { match ->
            val rewrittenSrcset =
                rewriteSrcset(
                    srcset = match.groupValues[2],
                    baseDir = baseDir,
                    imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                )
            "${match.groupValues[1]}$rewrittenSrcset${match.groupValues[REGEX_SUFFIX_GROUP]}"
        }
    }

    private fun rewriteImageReference(
        rawSrc: String,
        baseDir: String,
        imageRelativePathByEpubPathLower: Map<String, String>,
    ): String? {
        val hrefParts = splitHrefParts(rawSrc)
        val src = sanitizeSrc(hrefParts.path)
        val isExternal =
            src.startsWith("data:", ignoreCase = true) ||
                src.startsWith("http://", ignoreCase = true) ||
                src.startsWith("https://", ignoreCase = true) ||
                src.startsWith("kairo_epub_assets/")
        return if (src.isBlank() || isExternal) {
            null
        } else {
            resolveZipEntryKey(baseDir, src, imageRelativePathByEpubPathLower.keys)
                ?.let(imageRelativePathByEpubPathLower::get)
                ?.plus(hrefParts.suffix)
        }
    }

    private fun rewriteSrcset(
        srcset: String,
        baseDir: String,
        imageRelativePathByEpubPathLower: Map<String, String>,
    ): String {
        if (srcset.isBlank()) return srcset
        return srcset
            .split(',')
            .joinToString(", ") { descriptor ->
                val trimmed = descriptor.trim()
                if (trimmed.isBlank()) return@joinToString trimmed
                val parts = trimmed.split(Regex("\\s+"), limit = 2)
                val candidate = parts.firstOrNull().orEmpty()
                val suffix = if (parts.size > 1) " ${parts[1]}" else ""
                val rewritten = rewriteImageReference(candidate, baseDir, imageRelativePathByEpubPathLower)
                (rewritten ?: candidate) + suffix
            }
    }

    private fun extractSrcsetUrls(srcset: String): List<String> {
        if (srcset.isBlank()) return emptyList()
        return srcset
            .split(',')
            .mapNotNull { descriptor ->
                val trimmed = descriptor.trim()
                if (trimmed.isBlank()) return@mapNotNull null
                trimmed
                    .split(Regex("\\s+"), limit = 2)
                    .firstOrNull()
                    ?.takeIf { it.isNotBlank() }
            }
    }

    /**
     * Extracts plain text from HTML/XHTML content.
     */
    fun extractPlainText(html: String): String =
        extractPlainText(parseMarkupDocument(html))

    fun extractPlainText(document: EpubMarkupDocument): String =
        extractPlainTextWithAnchors(document).text

    fun extractPlainTextWithAnchors(html: String): EpubPlainTextContent =
        extractPlainTextWithAnchors(parseMarkupDocument(html))

    fun extractPlainTextWithAnchors(document: EpubMarkupDocument): EpubPlainTextContent {
        val marked = EpubMarkupInspector.renderPlainTextWithAnchorMarkers(document)
        if (marked.markers.isEmpty()) {
            return EpubPlainTextContent(
                text = normalizePlainText(marked.text),
                anchorOffsets = emptyMap(),
            )
        }
        val normalized = normalizePlainTextWhitespace(marked.text)

        val anchorIdByMarker = marked.markers.associate { marker -> marker.marker to marker.anchorId }
        val plainText = StringBuilder(normalized.length)
        val anchorOffsets = linkedMapOf<String, Int>()
        var cursor = 0
        ANCHOR_MARKER_REGEX.findAll(normalized).forEach { match ->
            plainText.append(normalized, cursor, match.range.first)
            anchorIdByMarker[match.value]
                ?.let(::decodeHtmlEntities)
                ?.takeIf(String::isNotBlank)
                ?.let { anchorId -> anchorOffsets.putIfAbsent(anchorId, plainText.length) }
            cursor = match.range.last + 1
        }
        plainText.append(normalized, cursor, normalized.length)
        val untrimmedText = plainText.toString()
        val finalText = trimPlainTextPreservingPageBreak(untrimmedText)
        val leadingTrimmedCharacters =
            finalText
                .takeIf(String::isNotEmpty)
                ?.let(untrimmedText::indexOf)
                ?.coerceAtLeast(0)
                ?: 0
        return EpubPlainTextContent(
            text = finalText,
            anchorOffsets =
            anchorOffsets.mapValues { (_, offset) ->
                var resolved =
                    (offset - leadingTrimmedCharacters).coerceIn(0, finalText.length)
                while (resolved < finalText.length && finalText[resolved].isWhitespace()) {
                    resolved += 1
                }
                resolved
            },
        )
    }

    private fun normalizePlainText(text: String): String =
        normalizePlainTextWhitespace(text)
            .let(::trimPlainTextPreservingPageBreak)

    private fun normalizePlainTextWhitespace(text: String): String =
        text
            .let(::decodeHtmlEntities)
            .replace(HORIZONTAL_WHITESPACE_REGEX, " ")
            .replace(MULTIPLE_NEWLINES_REGEX, "\n\n")

    private fun trimPlainTextPreservingPageBreak(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.isEmpty() && text.contains(PAGE_BREAK_MARKER)) {
            PAGE_BREAK_MARKER
        } else {
            trimmed
        }
    }

    /**
     * Extracts chapter title from HTML content.
     */
    fun extractChapterTitle(html: String): String? =
        extractChapterTitle(parseMarkupDocument(html))

    fun extractChapterTitle(document: EpubMarkupDocument): String? {
        val titleText = EpubMarkupInspector.firstTextInTags(document, setOf("title"))
        if (titleText != null) {
            val title =
                decodeHtmlEntities(titleText)
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
            if (title.isNotBlank() && !title.equals("untitled", ignoreCase = true)) {
                return title
            }
        }

        val headingText = EpubMarkupInspector.firstTextInTags(document, setOf("h1", "h2", "h3"))
        if (headingText != null) {
            val heading =
                decodeHtmlEntities(headingText)
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
            if (heading.isNotBlank()) {
                return heading.take(MAX_CHAPTER_TITLE_LENGTH)
            }
        }

        return null
    }

    fun sanitizeChapterTitle(title: String?): String? {
        val trimmed = title?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (isLikelyFileLabel(trimmed)) null else trimmed
    }

    fun stripNoiseTitleBlocks(html: String): String {
        if (html.isBlank()) return html
        var result = html
        repeat(2) {
            val match = BLOCK_ELEMENT_REGEX.find(result) ?: return@repeat
            val leading = result.take(match.range.first)
            if (visibleText(leading).isNotBlank()) return@repeat
            val inner = match.groupValues[2]
            val text = visibleText(inner)
            if (text.length <= MAX_NOISE_TITLE_LENGTH && isLikelyFileLabel(text)) {
                result = result.removeRange(match.range.first, match.range.last + 1)
            } else {
                return@repeat
            }
        }
        return result
    }

    private fun visibleText(htmlFragment: String): String =
        decodeHtmlEntities(htmlFragment.replace(ALL_TAGS_REGEX, " "))
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun isLikelyFileLabel(text: String): Boolean {
        val normalized = normalizeNoiseLabel(text)
        if (normalized.isBlank()) return false
        val compact = normalized.replace(Regex("[\\s_-]+"), "")
        val numberedMatch = FILE_LABEL_WITH_NUMBER_REGEX.matchEntire(compact)
        if (numberedMatch != null) {
            val zeros = numberedMatch.groupValues[2]
            val digits = numberedMatch.groupValues[REGEX_SUFFIX_GROUP]
            if (zeros.isNotEmpty() || digits.length >= MIN_UNPADDED_FILE_NUMBER_DIGITS) return true
        }
        return GENERIC_FILE_LABEL_REGEX.matches(compact)
    }

    private fun normalizeNoiseLabel(text: String): String {
        val trimmed = text.trim().lowercase(Locale.ROOT)
        if (trimmed.isBlank()) return ""
        return trimmed.substringBeforeLast('.', trimmed)
    }

    /**
     * Rewrites internal anchor hrefs to kairo://chapter/X format.
     * This enables the tokenizer to identify clickable links.
     */
    fun rewriteHtmlAnchorHrefs(
        html: String,
        baseDir: String,
        chapterIndexByPathLower: Map<String, Int>,
        currentChapterPath: String? = null,
    ): String {
        return ANCHOR_HREF_REGEX.replace(html) { match ->
            val prefix = match.groupValues[1]
            val href = decodeHtmlEntities(match.groupValues[2]).trim()
            val suffix = match.groupValues[REGEX_SUFFIX_GROUP]

            // Skip external links and data URIs
            if (href.startsWith("http://", true) ||
                href.startsWith("https://", true) ||
                href.startsWith("mailto:", true) ||
                href.startsWith("data:", true) ||
                href.startsWith("kairo://", true)
            ) {
                return@replace match.value
            }

            val hrefParts = splitHrefParts(href)
            val path = decodeUrlPath(hrefParts.path).trim()
            val fragment = decodeUrlPath(hrefParts.fragment).trim()

            // Determine target path
            val targetPath =
                when {
                    path.isNotBlank() ->
                        resolveZipEntryKey(baseDir, path, chapterIndexByPathLower.keys)
                    fragment.isNotBlank() && currentChapterPath != null ->
                        currentChapterPath.lowercase(Locale.ROOT)
                    else -> null
                } ?: return@replace match.value

            val chapterIndex = chapterIndexByPathLower[targetPath] ?: return@replace match.value

            val fragmentSuffix =
                fragment
                    .takeIf { it.isNotBlank() }
                    ?.let { "#${EpubPathResolver.encodeUrlPath(it)}" }
                    .orEmpty()
            "${prefix}kairo://chapter/$chapterIndex$fragmentSuffix$suffix"
        }
    }

    fun countAnchorTags(html: String): Int {
        if (html.indexOf("<a", ignoreCase = true) < 0) return 0
        return ANCHOR_TAG_REGEX
            .findAll(html)
            .take(MAX_LINKS_PER_CHAPTER + 1)
            .count()
    }

    fun parseMarkupDocument(html: String): EpubMarkupDocument {
        val sanitized =
            html
                .replace(HTML_COMMENT_REGEX, "")
                .replace(SCRIPT_TAG_REGEX, "")
                .replace(STYLE_TAG_REGEX, "")
        return markupParser.parse(sanitized)
    }

    fun decodeHtmlEntities(input: String): String = EpubHtmlEntities.decode(input)

    fun decodeUrlPath(input: String): String = EpubPathResolver.decodeUrlPath(input)

    private fun resolveZipEntryKey(baseDir: String, rawHref: String, available: Set<String>): String? =
        EpubPathResolver.resolveZipEntryKey(baseDir, rawHref, available)
}

private const val REGEX_SUFFIX_GROUP = 3
private const val MAX_CHAPTER_TITLE_LENGTH = 100
private const val MIN_UNPADDED_FILE_NUMBER_DIGITS = 3
