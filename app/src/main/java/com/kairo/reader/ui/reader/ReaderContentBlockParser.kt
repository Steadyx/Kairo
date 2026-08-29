package com.kairo.reader.ui.reader

import java.util.Locale

private const val HEX_RADIX = 16
internal const val MAX_READER_CHAPTER_LOCAL_IMAGE_CANDIDATES = 256
internal const val READER_CHAPTER_IMAGE_LIMIT_MESSAGE =
    "This chapter contains too many images to display."

internal class ReaderChapterImageLimitException : IllegalArgumentException(READER_CHAPTER_IMAGE_LIMIT_MESSAGE)

private sealed interface HtmlBlockMarker {
    data object Paragraph : HtmlBlockMarker

    data class Image(val path: String, val size: ReaderImageSize?,) : HtmlBlockMarker
}

internal fun buildReaderBlocks(
    htmlContent: String,
    paragraphs: List<Paragraph>,
    imagePaths: List<String>,
): List<ReaderBlock> {
    if (paragraphs.isEmpty() && imagePaths.isEmpty() && htmlContent.isBlank()) {
        return emptyList()
    }

    val markers = extractHtmlBlockMarkers(htmlContent, imagePaths)
    val blocks = mutableListOf<ReaderBlock>()
    var paragraphIndex = 0
    var imageIndex = 0

    if (markers.isEmpty() && paragraphs.isEmpty() && imagePaths.isNotEmpty()) {
        enforceReaderChapterImageLimit(imagePaths.size)
        imagePaths.forEachIndexed { index, path ->
            blocks.add(ReaderImageBlock(path, index))
        }
    } else {
        for (marker in markers) {
            when (marker) {
                HtmlBlockMarker.Paragraph -> {
                    val paragraph = paragraphs.getOrNull(paragraphIndex) ?: continue
                    blocks.add(ReaderParagraphBlock(paragraph))
                    paragraphIndex += 1
                }
                is HtmlBlockMarker.Image -> {
                    blocks.add(
                        ReaderImageBlock(
                            imagePath = marker.path,
                            index = imageIndex,
                            anchorTokenIndex =
                            resolveImageAnchorTokenIndex(
                                paragraphs = paragraphs,
                                nextParagraphIndex = paragraphIndex,
                                blocks = blocks,
                            ),
                            imageSize = marker.size,
                        ),
                    )
                    imageIndex += 1
                }
            }
        }

        while (paragraphIndex < paragraphs.size) {
            blocks.add(ReaderParagraphBlock(paragraphs[paragraphIndex]))
            paragraphIndex += 1
        }
    }

    return blocks
}

private fun resolveImageAnchorTokenIndex(
    paragraphs: List<Paragraph>,
    nextParagraphIndex: Int,
    blocks: List<ReaderBlock>,
): Int? {
    paragraphs.getOrNull(nextParagraphIndex)?.let { return it.startIndex }

    val previousParagraphAnchor =
        blocks
            .asReversed()
            .firstNotNullOfOrNull { block ->
                val paragraph =
                    (block as? ReaderParagraphBlock)?.paragraph
                        ?: return@firstNotNullOfOrNull null
                paragraph.startIndex + paragraph.tokens.lastIndex
            }

    return previousParagraphAnchor ?: paragraphs.lastOrNull()?.let { paragraph ->
        paragraph.startIndex + paragraph.tokens.lastIndex
    }
}

private fun extractHtmlBlockMarkers(
    htmlContent: String,
    imagePaths: List<String>,
): List<HtmlBlockMarker> {
    if (htmlContent.isBlank()) {
        return emptyList()
    }

    val cleaned =
        htmlContent
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")

    val blockSeparated =
        cleaned.replace(
            Regex(
                "</?(p|div|br|h[1-6]|li|tr|blockquote|pre|ul|ol|table|" +
                    "thead|tbody|tfoot|td|th|section|article|figure|figcaption|hr)[^>]*>",
                RegexOption.IGNORE_CASE,
            ),
            "\n\n",
        )
    val rawBlocks =
        blockSeparated
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

    val markers = mutableListOf<HtmlBlockMarker>()
    var fallbackIndex = 0
    val imgRegex =
        Regex(
            "<img\\b[^>]+?src\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"][^>]*>",
            RegexOption.IGNORE_CASE,
        )
    val svgRegex =
        Regex(
            "<image\\b[^>]*?\\b(?:xlink:href|href)\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"][^>]*>",
            RegexOption.IGNORE_CASE,
        )
    var localImageCandidateCount = 0

    fun collectLocalImageMatches(
        block: String,
        regex: Regex,
        destination: MutableList<HtmlImageMatch>,
    ) {
        regex.findAll(block).forEach { match ->
            val src = match.groupValues[1]
            if (isGuaranteedNonLocalImageSource(src)) return@forEach
            localImageCandidateCount += 1
            enforceReaderChapterImageLimit(localImageCandidateCount)
            destination.add(
                HtmlImageMatch(
                    start = match.range.first,
                    endInclusive = match.range.last,
                    src = src,
                    size = extractImageSize(match.value),
                ),
            )
        }
    }

    rawBlocks.forEach { block ->
        val imageMatches = mutableListOf<HtmlImageMatch>()
        collectLocalImageMatches(block, imgRegex, imageMatches)
        collectLocalImageMatches(block, svgRegex, imageMatches)
        var cursor = 0
        var paragraphAdded = false

        fun addParagraphIfText(fragment: String) {
            if (!paragraphAdded && decodeHtmlText(fragment).isNotBlank()) {
                markers += HtmlBlockMarker.Paragraph
                paragraphAdded = true
            }
        }

        imageMatches.sortedBy { it.start }.forEach { imageMatch ->
            if (imageMatch.start >= cursor) {
                addParagraphIfText(block.substring(cursor, imageMatch.start))
            }
            val src = imageMatch.src
            val resolved = resolveInlineImagePath(src, imagePaths, fallbackIndex)
            if (resolved != null) {
                markers += HtmlBlockMarker.Image(
                    path = resolved.first,
                    size = imageMatch.size,
                )
                fallbackIndex = resolved.second
            }
            cursor = (imageMatch.endInclusive + 1).coerceAtLeast(cursor)
        }
        addParagraphIfText(block.substring(cursor))
    }

    return markers
}

private data class HtmlImageMatch(val start: Int, val endInclusive: Int, val src: String, val size: ReaderImageSize?,)

private fun enforceReaderChapterImageLimit(candidateCount: Int) {
    if (candidateCount > MAX_READER_CHAPTER_LOCAL_IMAGE_CANDIDATES) {
        throw ReaderChapterImageLimitException()
    }
}

private fun isGuaranteedNonLocalImageSource(rawSrc: String): Boolean {
    val src = rawSrc.trim()
    return src.startsWith("data:", ignoreCase = true) ||
        src.startsWith("http:", ignoreCase = true) ||
        src.startsWith("https:", ignoreCase = true)
}

private fun extractImageSize(tag: String): ReaderImageSize? {
    val width = extractImageLength(tag, "width")
    val height = extractImageLength(tag, "height")
    if (width == null && height == null) return null
    return ReaderImageSize(widthPx = width, heightPx = height)
}

private fun extractImageLength(
    tag: String,
    property: String,
): Float? =
    extractImageAttributeLength(tag, property)
        ?: extractImageStyleLength(tag, property)

private fun extractImageAttributeLength(
    tag: String,
    attributeName: String,
): Float? {
    val regex =
        Regex(
            "\\b$attributeName\\s*=\\s*(['\"]?)([^'\"\\s>]+)\\1",
            RegexOption.IGNORE_CASE,
        )
    return regex.find(tag)?.groupValues?.getOrNull(2)?.let(::parseCssPixelLength)
}

private fun extractImageStyleLength(
    tag: String,
    property: String,
): Float? {
    val style =
        Regex(
            "\\bstyle\\s*=\\s*(['\"])(.*?)\\1",
            RegexOption.IGNORE_CASE,
        ).find(tag)?.groupValues?.getOrNull(2) ?: return null
    val regex =
        Regex(
            "(?:^|;)\\s*$property\\s*:\\s*([^;]+)",
            RegexOption.IGNORE_CASE,
        )
    return regex.find(style)?.groupValues?.getOrNull(1)?.let(::parseCssPixelLength)
}

private fun parseCssPixelLength(value: String): Float? {
    val normalized = value.trim().lowercase(Locale.ROOT)
    if (normalized.isBlank() ||
        normalized == "auto" ||
        normalized.endsWith("%")
    ) {
        return null
    }

    val numeric =
        when {
            normalized.endsWith("px") -> normalized.dropLast(2)
            normalized.all { it.isDigit() || it == '.' } -> normalized
            else -> return null
        }
    return numeric
        .toFloatOrNull()
        ?.takeIf { it > 0f && it.isFinite() }
}

private fun decodeHtmlText(fragment: String): String =
    fragment
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1]
                .toIntOrNull()
                ?.toChar()
                ?.toString()
                .orEmpty()
        }.replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            match.groupValues[1]
                .toIntOrNull(HEX_RADIX)
                ?.toChar()
                ?.toString()
                .orEmpty()
        }.replace(Regex("[ \\t]+"), " ")
        .trim()

private fun resolveInlineImagePath(
    rawSrc: String,
    imagePaths: List<String>,
    fallbackIndex: Int,
): Pair<String, Int>? {
    val src = sanitizeInlineSrc(rawSrc)
    return when {
        src.isBlank() -> null
        src.startsWith("data:", ignoreCase = true) -> null
        src.startsWith("http://", ignoreCase = true) -> null
        src.startsWith("https://", ignoreCase = true) -> null
        src.startsWith("kairo_epub_assets/") -> src to fallbackIndex
        else -> imagePaths.getOrNull(fallbackIndex)?.let { it to (fallbackIndex + 1) }
    }
}

private fun sanitizeInlineSrc(rawSrc: String): String {
    val trimmed = rawSrc.trim()
    if (trimmed.isBlank()) return ""
    return trimmed
        .substringBefore('#')
        .substringBefore('?')
        .trim()
}
