package com.example.kairo.data.books.mobi

import com.example.kairo.core.model.Chapter

internal class MobiContentProcessor {
    private val fileLabelWithNumberRegex = Regex("(?i)^(part|chapter|section|book)(0*)(\\d{1,6})$")
    private val genericFileLabelRegex = Regex("(?i)^[a-z]{2,}\\d{3,}$")

    fun extractHtml(
        data: ByteArray,
        recordOffsets: List<Int>,
        compression: Int,
        textRecordCount: Int,
        header: MobiHeader,
        firstImageIndexHint: Int,
    ): String {
        val textBuilder = StringBuilder()
        val textRecordStart = 1
        val recordLimit =
            if (firstImageIndexHint > 0 && firstImageIndexHint <= recordOffsets.lastIndex) {
                firstImageIndexHint
            } else {
                recordOffsets.size
            }
        val textRecordEnd = minOf(textRecordStart + textRecordCount, recordLimit)

        for (index in textRecordStart until textRecordEnd) {
            val start = recordOffsets[index]
            val end = if (index + 1 < recordOffsets.size) recordOffsets[index + 1] else data.size
            if (end !in 0..data.size || start !in 0 until end) continue
            val recordData = data.copyOfRange(start, end)
            if (MobiBinary.detectImageType(recordData) != null) continue

            val decodedBytes =
                when (compression) {
                    1 -> recordData
                    2 -> MobiBinary.decompressPalmDoc(recordData)
                    // Some producers mark unsupported compression but still ship readable text chunks.
                    else -> recordData
                }
            if (MobiBinary.looksMostlyBinary(decodedBytes)) continue
            textBuilder.append(MobiBinary.decodeText(decodedBytes, header.textCharset))
        }

        if (textBuilder.isBlank()) {
            appendHtmlFromAllRecords(data, recordOffsets, header, textBuilder)
        }

        val cleaned = cleanMobiHtml(textBuilder.toString())
        val hasEscapedTags =
            !looksLikeHtml(cleaned) &&
                cleaned.contains("&lt;", ignoreCase = true) &&
                cleaned.contains("&gt;", ignoreCase = true)
        val htmlCandidate = if (hasEscapedTags) MobiHtmlUtils.decodeHtmlEntities(cleaned) else cleaned
        return if (looksLikeHtml(htmlCandidate)) {
            htmlCandidate
        } else {
            wrapPlainTextAsHtml(breakLongRuns(htmlCandidate))
        }
    }

    fun splitHtmlIntoChapters(
        html: String,
        fallbackTitle: String,
    ): List<Chapter> {
        val slices = splitHtmlIntoChapterSlices(html, fallbackTitle)
        if (slices.isEmpty()) return emptyList()

        val chapters =
            slices.mapIndexed { index, slice ->
                val plain = extractPlainText(slice.htmlContent)
                Chapter(
                    index = index,
                    title = sanitizeChapterTitle(slice.title ?: fallbackTitle),
                    htmlContent = slice.htmlContent,
                    plainText = plain,
                    imagePaths = extractImagePathsFromHtml(slice.htmlContent),
                )
            }

        val rewritten = rewriteInternalLinks(chapters, slices)
        return splitLargeChapters(rewritten, fallbackTitle)
    }

    fun extractPlainText(html: String): String =
        normalizePageBreakElements(html)
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<(p|div|br|h[1-6]|li|tr)[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|h[1-6]|li|tr)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .let(MobiHtmlUtils::decodeHtmlEntities)
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n\\s*\\n+"), "\n\n")
            .trim()

    fun extractImagePathsFromHtml(html: String): List<String> {
        if (!html.contains("<img", ignoreCase = true)) return emptyList()
        val regex =
            Regex(
                """<img[^>]+?src\s*=\s*['"]([^'"]+)['"][^>]*>""",
                RegexOption.IGNORE_CASE,
            )
        val unique = LinkedHashSet<String>(8)
        regex.findAll(html).forEach { match ->
            if (unique.size >= 6) return@forEach
            val src = match.groupValues[1].trim()
            if (src.isBlank()) return@forEach
            if (src.startsWith("data:", true)) return@forEach
            if (src.startsWith("http://", true) || src.startsWith("https://", true)) return@forEach
            unique.add(src)
        }
        return unique.toList()
    }

    fun extractCoverImageRecindices(html: String): Set<Int> {
        if (html.isBlank()) return emptySet()
        val limited = html.take(MobiLimits.COVER_HTML_SCAN_CHARS)
        val coverIds = LinkedHashSet<String>()

        val metaRegex =
            Regex(
                """<meta[^>]+name\s*=\s*['"]cover['"][^>]+content\s*=\s*['"]([^'"]+)['"][^>]*>""",
                RegexOption.IGNORE_CASE,
            )
        metaRegex.findAll(limited).forEach { match ->
            val id = match.groupValues[1].trim()
            if (id.isNotEmpty()) coverIds.add(id)
        }

        val candidates = LinkedHashSet<Int>()
        var firstRecindex: Int? = null
        val imgRegex = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
        for (match in imgRegex.findAll(limited)) {
            val tag = match.value
            val recindex = extractRecindexFromImgTag(tag)
            if (recindex != null && firstRecindex == null) {
                firstRecindex = recindex
            }
            if (recindex == null) continue
            val id = MobiHtmlUtils.extractAttribute(tag, "id") ?: MobiHtmlUtils.extractAttribute(tag, "name")
            val alt = MobiHtmlUtils.extractAttribute(tag, "alt").orEmpty()
            val title = MobiHtmlUtils.extractAttribute(tag, "title").orEmpty()
            val classAttr = MobiHtmlUtils.extractAttribute(tag, "class").orEmpty()
            val looksCover =
                (id != null && coverIds.contains(id)) ||
                    id?.contains("cover", ignoreCase = true) == true ||
                    alt.contains("cover", ignoreCase = true) ||
                    title.contains("cover", ignoreCase = true) ||
                    classAttr.contains("cover", ignoreCase = true)
            if (looksCover) {
                candidates.add(recindex)
                if (candidates.size >= MobiLimits.COVER_FALLBACK_IMAGE_SCAN) break
            }
        }
        if (candidates.isEmpty()) {
            val firstImg = imgRegex.find(limited)?.value
            if (firstImg != null) {
                val recindex = extractRecindexFromImgTag(firstImg)
                if (recindex != null) {
                    candidates.add(recindex)
                } else {
                    val src = MobiHtmlUtils.extractAttribute(firstImg, "src")
                    if (!src.isNullOrBlank() &&
                        !src.startsWith("data:", ignoreCase = true) &&
                        !src.startsWith("http://", ignoreCase = true) &&
                        !src.startsWith("https://", ignoreCase = true)
                    ) {
                        candidates.add(0)
                    }
                }
            }
            firstRecindex?.let { candidates.add(it) }
        }
        return candidates
    }

    fun extractReferencedImageIndices(
        html: String,
        extra: Set<Int> = emptySet(),
    ): Set<Int> {
        if (html.isBlank()) return extra
        if (!html.contains("recindex", true) && !html.contains("kindle:embed", true)) return extra

        val indices = LinkedHashSet<Int>(extra)
        Regex("""recindex\s*=\s*['"]?(\d+)['"]?""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                match.groupValues[1].toIntOrNull()?.let(indices::add)
            }
        Regex("""kindle:embed:(\d+)""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                match.groupValues[1].toIntOrNull()?.let(indices::add)
            }
        return indices
    }

    fun splitFallbackText(text: String): List<Chapter> {
        val chapterPattern = Regex("(?i)(chapter|part|section|book)\\s*\\d+", RegexOption.MULTILINE)
        val matches = chapterPattern.findAll(text).toList()
        val chapters =
            if (matches.size >= 2) {
                val indices = matches.map { it.range.first } + text.length
                indices.zipWithNext()
                    .mapIndexed { index, (start, end) ->
                        val content = text.substring(start, end).trim()
                        val title = matches.getOrNull(index)?.value ?: "Chapter ${index + 1}"
                        Chapter(
                            index = index,
                            title = title,
                            htmlContent = "<p>${content.replace("\n", "</p><p>")}</p>",
                            plainText = content,
                        )
                    }.filter { it.plainText.isNotBlank() }
            } else {
                val paragraphs = text.split(Regex("\n\\s*\n")).filter(String::isNotBlank)
                val chunks = mutableListOf<List<String>>()
                val current = mutableListOf<String>()
                var words = 0

                for (paragraph in paragraphs) {
                    val paragraphWords = paragraph.split(Regex("\\s+")).size
                    if (words + paragraphWords > 2000 && current.isNotEmpty()) {
                        chunks.add(current.toList())
                        current.clear()
                        words = 0
                    }
                    current.add(paragraph)
                    words += paragraphWords
                }
                if (current.isNotEmpty()) chunks.add(current.toList())

                chunks.mapIndexed { index, chunk ->
                    val content = chunk.joinToString("\n\n")
                    Chapter(
                        index = index,
                        title = "Chapter ${index + 1}",
                        htmlContent = "<p>${content.replace("\n\n", "</p><p>")}</p>",
                        plainText = content,
                    )
                }
            }

        return chapters.ifEmpty {
            listOf(
                Chapter(
                    index = 0,
                    title = "Content",
                    htmlContent = "<p>$text</p>",
                    plainText = text,
                ),
            )
        }
    }

    fun extractFallbackText(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val builder = StringBuilder(minOf(data.size, MobiLimits.MAX_FALLBACK_TEXT_CHARS))
        var lastWasSpace = false
        for (byte in data) {
            if (builder.length >= MobiLimits.MAX_FALLBACK_TEXT_CHARS) break
            val value = byte.toInt() and 0xFF
            val ch =
                when (value) {
                    0x09, 0x0A, 0x0D -> ' '
                    in 0x20..0x7E -> value.toChar()
                    in 0xA0..0xFF -> value.toChar()
                    else -> null
                }
            if (ch == null) {
                if (!lastWasSpace) {
                    builder.append(' ')
                    lastWasSpace = true
                }
            } else {
                builder.append(ch)
                lastWasSpace = ch.isWhitespace()
            }
        }
        return builder.toString()
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun appendHtmlFromAllRecords(
        data: ByteArray,
        recordOffsets: List<Int>,
        header: MobiHeader,
        textBuilder: StringBuilder,
    ) {
        for (index in recordOffsets.indices) {
            val start = recordOffsets[index]
            val end = if (index + 1 < recordOffsets.size) recordOffsets[index + 1] else data.size
            if (start < 0 || end > data.size || end <= start) continue
            val bytes = data.copyOfRange(start, end)
            if (MobiBinary.detectImageType(bytes) != null) continue
            val text = MobiBinary.decodeText(bytes, header.textCharset)
            if (looksLikeHtml(text)) {
                textBuilder.append(text)
            }
        }
    }

    private fun splitHtmlIntoChapterSlices(
        html: String,
        fallbackTitle: String,
    ): List<MobiChapterSlice> {
        val headingRegex = Regex("<h[1-3][^>]*>.*?</h[1-3]>", RegexOption.IGNORE_CASE)
        val matches = headingRegex.findAll(html).toList()
        if (matches.size >= 2) {
            val slices = mutableListOf<MobiChapterSlice>()
            val indices = matches.map { it.range.first } + html.length
            indices.zipWithNext().forEachIndexed { index, (start, end) ->
                val segment = html.substring(start, end).trim()
                val cleaned = stripNoiseTitleBlocks(segment)
                val title =
                    extractPlainText(matches.getOrNull(index)?.value.orEmpty())
                        .lineSequence()
                        .firstOrNull()
                        ?.take(100)
                        ?.takeIf(String::isNotBlank)
                val plain = extractPlainText(cleaned)
                if (plain.isBlank()) return@forEachIndexed
                slices.add(
                    MobiChapterSlice(
                        start = start,
                        end = end,
                        title = title,
                        htmlContent = cleaned,
                    ),
                )
            }
            if (slices.isNotEmpty()) return slices
        }
        val byFilepos = splitByTocFilepos(html, fallbackTitle)
        if (byFilepos.isNotEmpty()) return byFilepos

        val cleaned = stripNoiseTitleBlocks(html)
        val plain = extractPlainText(cleaned)
        if (plain.isBlank()) return emptyList()
        return listOf(
            MobiChapterSlice(
                start = 0,
                end = html.length,
                title = fallbackTitle,
                htmlContent = cleaned,
            ),
        )
    }

    private fun splitByTocFilepos(
        html: String,
        fallbackTitle: String,
    ): List<MobiChapterSlice> {
        if (html.isBlank()) return emptyList()
        val tocRegion = html.take(MobiLimits.MAX_TOC_TEXT_CHARS)
        val entries = extractTocEntries(tocRegion, html.length)
        if (entries.size < 2) return emptyList()

        val filtered = mutableListOf<TocEntry>()
        var lastPos = -1
        for (entry in entries) {
            if (entry.filepos - lastPos < MobiLimits.MIN_FILEPOS_GAP_CHARS) continue
            filtered.add(entry)
            lastPos = entry.filepos
            if (filtered.size >= MobiLimits.MAX_FILEPOS_CHAPTERS) break
        }
        if (filtered.size < 2) return emptyList()

        val slices = mutableListOf<MobiChapterSlice>()
        val starts = filtered.map { it.filepos }
        val tocEnd = starts.first()
        if (tocEnd > 0) {
            val tocHtml = html.substring(0, tocEnd).trim()
            val tocPlain = extractPlainText(tocHtml)
            if (tocPlain.isNotBlank()) {
                slices.add(
                    MobiChapterSlice(
                        start = 0,
                        end = tocEnd,
                        title = if (tocRegion.contains("contents", true)) "Table of Contents" else fallbackTitle,
                        htmlContent = stripNoiseTitleBlocks(tocHtml),
                    ),
                )
            }
        }

        filtered.forEachIndexed { index, entry ->
            val start = starts[index]
            val end = starts.getOrNull(index + 1) ?: html.length
            if (start >= end || start < 0 || end > html.length) return@forEachIndexed
            val segment = html.substring(start, end).trim()
            val cleaned = stripNoiseTitleBlocks(segment)
            if (extractPlainText(cleaned).isBlank()) return@forEachIndexed
            slices.add(
                MobiChapterSlice(
                    start = start,
                    end = end,
                    title = entry.title,
                    htmlContent = cleaned,
                ),
            )
        }
        return slices
    }

    private fun extractTocEntries(
        html: String,
        maxLength: Int,
    ): List<TocEntry> {
        val anchorRegex =
            Regex(
                """<a\b[^>]*\bfilepos\s*=\s*['"]?(\d+)['"]?[^>]*>([\s\S]*?)</a>""",
                RegexOption.IGNORE_CASE,
            )
        val entries = mutableListOf<TocEntry>()
        val seen = HashSet<Int>()
        for (match in anchorRegex.findAll(html)) {
            val filepos = match.groupValues[1].toIntOrNull() ?: continue
            if (filepos <= 0 || filepos >= maxLength) continue
            if (!seen.add(filepos)) continue
            val text = extractLinkText(match.groupValues[2])
            if (text.isBlank() || isPageNumberText(text)) continue
            entries.add(TocEntry(filepos = filepos, title = text))
        }
        return entries.sortedBy { it.filepos }
    }

    private fun rewriteInternalLinks(
        chapters: List<Chapter>,
        slices: List<MobiChapterSlice>,
    ): List<Chapter> {
        if (chapters.isEmpty()) return chapters
        val titleIndex = mutableMapOf<String, Int>()
        val idIndex = mutableMapOf<String, Int>()
        chapters.forEach { chapter ->
            chapter.title?.takeIf(String::isNotBlank)?.let { title ->
                titleIndex.putIfAbsent(normalizeTitle(title), chapter.index)
            }
            extractAnchorIds(chapter.htmlContent).forEach { id ->
                idIndex.putIfAbsent(id, chapter.index)
            }
        }

        return chapters.map { chapter ->
            val rewritten =
                Regex("""<a\b[^>]*>""", RegexOption.IGNORE_CASE).replace(chapter.htmlContent) { match ->
                    val tag = match.value
                    if (tag.contains("kairo://", true)) return@replace tag
                    val href = MobiHtmlUtils.extractAttribute(tag, "href")
                    val filepos = MobiHtmlUtils.extractAttribute(tag, "filepos")
                    val resolved =
                        resolveTargetFromHref(href, idIndex, slices) ?:
                            resolveTargetFromFilepos(filepos, slices) ?:
                            resolveTargetFromText(tag, titleIndex)
                    if (resolved == null) {
                        tag
                    } else {
                        insertOrReplaceHref(tag, "kairo://chapter/$resolved")
                    }
                }
            chapter.copy(htmlContent = rewritten)
        }
    }

    private fun resolveTargetFromHref(
        href: String?,
        idIndex: Map<String, Int>,
        slices: List<MobiChapterSlice>,
    ): Int? {
        if (href.isNullOrBlank()) return null
        val trimmed = href.trim()
        if (trimmed.startsWith("http://", true) ||
            trimmed.startsWith("https://", true) ||
            trimmed.startsWith("mailto:", true) ||
            trimmed.startsWith("data:", true) ||
            trimmed.startsWith("kairo://", true)
        ) {
            return null
        }
        extractFilePosFromHref(trimmed)?.let { return resolveTargetFromFilepos(it.toString(), slices) }
        val fragment = trimmed.substringAfter('#', "").trim()
        if (fragment.isBlank()) return null
        val decoded = MobiHtmlUtils.decodeFragment(fragment)
        return idIndex[decoded]
    }

    private fun resolveTargetFromFilepos(
        filepos: String?,
        slices: List<MobiChapterSlice>,
    ): Int? {
        val offset = filepos?.toIntOrNull() ?: return null
        return slices.indexOfFirst { offset >= it.start && offset < it.end }
            .takeIf { it >= 0 }
    }

    private fun resolveTargetFromText(
        openAnchorTag: String,
        titleIndex: Map<String, Int>,
    ): Int? {
        if (titleIndex.isEmpty()) return null
        val normalized = normalizeTitle(extractLinkText(openAnchorTag))
        if (normalized.isBlank()) return null
        return titleIndex[normalized] ?: titleIndex.entries.firstOrNull { (key, _) ->
            (key.length >= 4 && normalized.contains(key)) ||
                (normalized.length >= 4 && key.contains(normalized))
        }?.value
    }

    private fun splitLargeChapters(
        chapters: List<Chapter>,
        fallbackTitle: String,
    ): List<Chapter> {
        if (chapters.isEmpty()) return chapters
        val expanded = mutableListOf<Chapter>()
        chapters.forEach { chapter ->
            val isTocLike = countAnchorTags(chapter.htmlContent, MobiLimits.TOC_ANCHOR_THRESHOLD) >=
                MobiLimits.TOC_ANCHOR_THRESHOLD
            if (chapter.plainText.length <= MobiLimits.MAX_CHAPTER_TEXT_CHARS || isTocLike) {
                expanded.add(chapter.copy(index = expanded.size))
                return@forEach
            }
            val parts = splitFallbackText(chapter.plainText)
            if (parts.size <= 1) {
                expanded.add(chapter.copy(index = expanded.size))
                return@forEach
            }
            val baseTitle = chapter.title?.takeIf(String::isNotBlank) ?: fallbackTitle
            parts.forEachIndexed { partIndex, part ->
                val title = if (parts.size > 1) "$baseTitle (${partIndex + 1})" else baseTitle
                expanded.add(
                    part.copy(
                        index = expanded.size,
                        title = title,
                        imagePaths = if (partIndex == 0) chapter.imagePaths else emptyList(),
                    ),
                )
            }
        }
        return expanded
    }

    private fun extractRecindexFromImgTag(tag: String): Int? {
        MobiHtmlUtils.extractAttribute(tag, "recindex")?.toIntOrNull()?.let { return it }
        val src = MobiHtmlUtils.extractAttribute(tag, "src") ?: return null
        return Regex("kindle:embed:(\\d+)", RegexOption.IGNORE_CASE)
            .find(src)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun insertOrReplaceHref(
        tag: String,
        href: String,
    ): String {
        val hrefRegex =
            Regex(
                """\bhref\s*=\s*(?:['"][^'"]*['"]|[^\s>]+)""",
                RegexOption.IGNORE_CASE,
            )
        return if (hrefRegex.containsMatchIn(tag)) {
            hrefRegex.replace(tag) { "href=\"$href\"" }
        } else {
            val (prefix, suffix) = if (tag.endsWith("/>")) tag.dropLast(2) to "/>" else tag.dropLast(1) to ">"
            "$prefix href=\"$href\"$suffix"
        }
    }

    private fun extractFilePosFromHref(href: String): Int? {
        val patterns =
            listOf(
                Regex("filepos[:=](\\d+)", RegexOption.IGNORE_CASE),
                Regex("kindle:pos:\\S*?off:(\\d+)", RegexOption.IGNORE_CASE),
                Regex("kindle:pos:off:(\\d+)", RegexOption.IGNORE_CASE),
                Regex("#filepos(\\d+)", RegexOption.IGNORE_CASE),
                Regex("#pos(\\d+)", RegexOption.IGNORE_CASE),
            )
        for (pattern in patterns) {
            val value = pattern.find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun extractAnchorIds(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val ids = LinkedHashSet<String>()
        val regex =
            Regex(
                """\b(?:id|name)\s*=\s*['"]([^'"]+)['"]""",
                RegexOption.IGNORE_CASE,
            )
        regex.findAll(html).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) ids.add(value)
        }
        return ids.toList()
    }

    private fun countAnchorTags(
        html: String,
        limit: Int,
    ): Int {
        var count = 0
        var index = 0
        while (true) {
            val found = html.indexOf("<a", index, ignoreCase = true)
            if (found == -1) return count
            count += 1
            if (count >= limit) return count
            index = found + 2
        }
    }

    private fun cleanMobiHtml(html: String): String =
        html.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")

    private fun looksLikeHtml(text: String): Boolean =
        text.contains("<p", true) ||
            text.contains("<div", true) ||
            text.contains("<html", true) ||
            text.contains("<body", true) ||
            text.contains("<a", true) ||
            text.contains("<h", true) ||
            text.contains("<br", true)

    private fun wrapPlainTextAsHtml(text: String): String {
        if (text.isBlank()) return ""
        val paragraphs =
            text.split(Regex("\\n\\s*\\n")).map(String::trim).filter(String::isNotBlank)
        return paragraphs.joinToString(separator = "</p><p>", prefix = "<p>", postfix = "</p>") {
            it.replace("\n", " ")
        }
    }

    private fun breakLongRuns(
        text: String,
        maxRunLength: Int = 80,
    ): String {
        if (text.length <= maxRunLength) return text
        val builder = StringBuilder(text.length + (text.length / maxRunLength))
        var run = 0
        text.forEach { ch ->
            if (ch.isWhitespace()) {
                run = 0
                builder.append(ch)
            } else {
                run += 1
                if (run > maxRunLength) {
                    builder.append(' ')
                    run = 1
                }
                builder.append(ch)
            }
        }
        return builder.toString()
    }

    private fun sanitizeChapterTitle(title: String?): String? {
        val trimmed = title?.trim()?.takeIf(String::isNotBlank) ?: return null
        return if (isLikelyFileLabel(trimmed)) null else trimmed
    }

    private fun stripNoiseTitleBlocks(html: String): String {
        if (html.isBlank()) return html
        val blockRegex = Regex("(?is)<(h[1-6]|p|div)[^>]*>([\\s\\S]*?)</\\1>")
        return blockRegex.replace(html) { match ->
            val inner = match.groupValues[2]
            val text = inner.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").trim()
            if (text.length <= 32 && isLikelyFileLabel(text)) "" else match.value
        }
    }

    private fun normalizePageBreakElements(html: String): String =
        html
            .replace(Regex("""<\s*mbp:pagebreak\s*/?>""", RegexOption.IGNORE_CASE), " ")
            .replace(
                Regex(
                    """<[^>]+\bclass\s*=\s*['"][^'"]*(?:pagebreak|page-break)[^'"]*['"][^>]*>""",
                    RegexOption.IGNORE_CASE,
                ),
                " ",
            )

    private fun isLikelyFileLabel(text: String): Boolean {
        val normalized = text.trim().lowercase().substringBeforeLast('.', "")
        if (normalized.isBlank()) return false
        val compact = normalized.replace(Regex("[\\s_-]+"), "")
        val numberedMatch = fileLabelWithNumberRegex.matchEntire(compact)
        if (numberedMatch != null) {
            val zeros = numberedMatch.groupValues[2]
            val digits = numberedMatch.groupValues[3]
            if (zeros.isNotEmpty() || digits.length >= 3) return true
        }
        return genericFileLabelRegex.matches(compact)
    }

    private fun normalizeTitle(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9]+"), "")

    private fun extractLinkText(html: String): String =
        MobiHtmlUtils.decodeHtmlEntities(html.replace(Regex("<[^>]+>"), " "))
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isPageNumberText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.all { it.isDigit() }) return true
        return Regex("^[ivxlcdm]+$", RegexOption.IGNORE_CASE).matches(trimmed) && trimmed.length > 1
    }
}
