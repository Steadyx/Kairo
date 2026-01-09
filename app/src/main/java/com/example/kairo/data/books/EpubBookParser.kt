@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "LongMethod",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "TooManyFunctions",
)

package com.example.kairo.data.books

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.kairo.core.dispatchers.DispatcherProvider
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.withContext
import org.xml.sax.InputSource

/**
 * Full-fidelity EPUB parser that properly handles the EPUB ZIP structure.
 *
 * EPUB Structure:
 * - META-INF/container.xml → Points to content.opf location
 * - content.opf (or similar) → Contains metadata, manifest, and spine
 * - Manifest → Lists all content files (XHTML chapters, images, CSS)
 * - Spine → Defines reading order of chapters
 * - XHTML files → Actual chapter content
 */
class EpubBookParser(private val dispatcherProvider: DispatcherProvider) : BookParser {
    companion object {
        private const val TAG = "EpubBookParser"

        // Max size per text entry (5 MB) to prevent OOM on large embedded files
        private const val MAX_ENTRY_SIZE = 5 * 1024 * 1024

        // Max size per image entry (6 MB)
        private const val MAX_IMAGE_ENTRY_SIZE = 6 * 1024 * 1024

        // Max size for cover image entry (6 MB)
        private const val MAX_COVER_IMAGE_ENTRY_SIZE = 6 * 1024 * 1024

        // Max total size for extracted images (25 MB)
        private const val MAX_TOTAL_IMAGE_SIZE = 25 * 1024 * 1024

        // Buffer size for reading entries
        private const val BUFFER_SIZE = 8192

        // Guardrails for link extraction to avoid expensive scans on huge TOCs.
        private const val MAX_LINKS_PER_CHAPTER = 1000
        private const val MAX_LINK_TEXT_HTML_CHARS = 1200

        private const val MAX_NOISE_TITLE_LENGTH = 32
        private val FILE_LABEL_WITH_NUMBER_REGEX =
            Regex("(?i)^(part|chapter|section|book)(0*)(\\d{1,6})$")
        private val GENERIC_FILE_LABEL_REGEX =
            Regex("(?i)^[a-z]{2,}\\d{3,}$")
        private const val DC_NAMESPACE = "http://purl.org/dc/elements/1.1/"

        // Precompiled regex patterns for HTML processing (performance optimization)
        private val HTML_COMMENT_REGEX = Regex("<!--[\\s\\S]*?-->")
        private val SCRIPT_TAG_REGEX = Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
        private val STYLE_TAG_REGEX = Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
        private val NOSCRIPT_TAG_REGEX = Regex("<noscript[^>]*>[\\s\\S]*?</noscript>", RegexOption.IGNORE_CASE)
        private val BLOCK_OPEN_TAG_REGEX = Regex(
            "<(p|div|br|h[1-6]|li|tr|blockquote|pre|ul|ol|table|thead|tbody|tfoot|td|th|section|article|figure|figcaption|hr)[^>]*>",
            RegexOption.IGNORE_CASE,
        )
        private val BLOCK_CLOSE_TAG_REGEX = Regex(
            "</(p|div|h[1-6]|li|tr|blockquote|pre|ul|ol|table|thead|tbody|tfoot|td|th|section|article|figure|figcaption)>",
            RegexOption.IGNORE_CASE,
        )
        private val ALL_TAGS_REGEX = Regex("<[^>]+>")
        private val HORIZONTAL_WHITESPACE_REGEX = Regex("[ \\t]+")
        private val MULTIPLE_NEWLINES_REGEX = Regex("\\n\\s*\\n+")
        private val WHITESPACE_REGEX = Regex("\\s+")

        // Image extraction patterns
        private val IMG_SRC_REGEX = Regex(
            "<img\\b[^>]*?\\bsrc\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>",
            RegexOption.IGNORE_CASE,
        )
        private val SVG_IMAGE_HREF_REGEX = Regex(
            "<image\\b[^>]*?\\b(?:xlink:href|href)\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>",
            RegexOption.IGNORE_CASE,
        )

        // Image src rewriting patterns
        private val IMG_SRC_REWRITE_REGEX = Regex(
            "(<img\\b[^>]*?\\bsrc\\s*=\\s*['\"])([^'\"]+)(['\"][^>]*>)",
            RegexOption.IGNORE_CASE,
        )
        private val SVG_IMAGE_REWRITE_REGEX = Regex(
            "(<image\\b[^>]*?\\b(?:xlink:href|href)\\s*=\\s*['\"])([^'\"]+)(['\"][^>]*>)",
            RegexOption.IGNORE_CASE,
        )

        // Title/heading extraction patterns
        private val TITLE_TAG_REGEX = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
        private val HEADING_TAG_REGEX = Regex("<h([1-3])[^>]*>([\\s\\S]*?)</h\\1>", RegexOption.IGNORE_CASE)

        // Anchor rewriting pattern
        private val ANCHOR_HREF_REGEX = Regex(
            "(<a\\b[^>]*href\\s*=\\s*['\"])([^'\"]+)(['\"][^>]*>)",
            RegexOption.IGNORE_CASE,
        )

        // Page break patterns
        private val EPUB_PAGE_BREAK_REGEX = Regex(
            "<([a-zA-Z0-9]+)\\b[^>]*\\b(epub:type|role)\\s*=\\s*['\"](?:pagebreak|doc-pagebreak)['\"][^>]*>[\\s\\S]*?</\\1>",
            RegexOption.IGNORE_CASE,
        )
        private val EPUB_PAGE_BREAK_SELF_CLOSING_REGEX = Regex(
            "<[^>]+\\b(epub:type|role)\\s*=\\s*['\"](?:pagebreak|doc-pagebreak)['\"][^>]*/>",
            RegexOption.IGNORE_CASE,
        )
        private val CLASS_PAGE_BREAK_REGEX = Regex(
            "<([a-zA-Z0-9]+)\\b[^>]*\\bclass\\s*=\\s*['\"][^'\"]*(?:pagebreak|page-break)[^'\"]*['\"][^>]*>[\\s\\S]*?</\\1>",
            RegexOption.IGNORE_CASE,
        )
        private val CLASS_PAGE_BREAK_SELF_CLOSING_REGEX = Regex(
            "<[^>]+\\bclass\\s*=\\s*['\"][^'\"]*(?:pagebreak|page-break)[^'\"]*['\"][^>]*/>",
            RegexOption.IGNORE_CASE,
        )

        // Noise title block pattern
        private val BLOCK_ELEMENT_REGEX = Regex("(?is)<(h[1-6]|p|div)[^>]*>([\\s\\S]*?)</\\1>")

        private val NAMED_HTML_ENTITIES =
            mapOf(
                // Basic entities
                "nbsp" to " ",
                "amp" to "&",
                "lt" to "<",
                "gt" to ">",
                "quot" to "\"",
                "apos" to "'",
                // Quotation marks
                "lsquo" to "\u2018",
                "rsquo" to "\u2019",
                "sbquo" to "\u201A",
                "ldquo" to "\u201C",
                "rdquo" to "\u201D",
                "bdquo" to "\u201E",
                "laquo" to "\u00AB",
                "raquo" to "\u00BB",
                "lsaquo" to "\u2039",
                "rsaquo" to "\u203A",
                // Dashes and ellipsis
                "ndash" to "\u2013",
                "mdash" to "\u2014",
                "hellip" to "\u2026",
                // Punctuation and symbols
                "middot" to "\u00B7",
                "bull" to "\u2022",
                "deg" to "\u00B0",
                "copy" to "\u00A9",
                "reg" to "\u00AE",
                "trade" to "\u2122",
                "sect" to "\u00A7",
                "para" to "\u00B6",
                "dagger" to "\u2020",
                "Dagger" to "\u2021",
                "permil" to "\u2030",
                "prime" to "\u2032",
                "Prime" to "\u2033",
                // Currency
                "cent" to "\u00A2",
                "pound" to "\u00A3",
                "euro" to "\u20AC",
                "yen" to "\u00A5",
                "curren" to "\u00A4",
                // Math symbols
                "times" to "\u00D7",
                "divide" to "\u00F7",
                "plusmn" to "\u00B1",
                "minus" to "\u2212",
                "le" to "\u2264",
                "ge" to "\u2265",
                "ne" to "\u2260",
                "asymp" to "\u2248",
                "infin" to "\u221E",
                "sum" to "\u2211",
                "radic" to "\u221A",
                // Fractions
                "frac14" to "\u00BC",
                "frac12" to "\u00BD",
                "frac34" to "\u00BE",
                // Spaces
                "shy" to "\u00AD",
                "thinsp" to "\u2009",
                "ensp" to "\u2002",
                "emsp" to "\u2003",
                "hairsp" to "\u200A",
                "zwnj" to "",
                "zwj" to "",
                // Accented letters (common)
                "agrave" to "\u00E0",
                "aacute" to "\u00E1",
                "acirc" to "\u00E2",
                "atilde" to "\u00E3",
                "auml" to "\u00E4",
                "aring" to "\u00E5",
                "aelig" to "\u00E6",
                "ccedil" to "\u00E7",
                "egrave" to "\u00E8",
                "eacute" to "\u00E9",
                "ecirc" to "\u00EA",
                "euml" to "\u00EB",
                "igrave" to "\u00EC",
                "iacute" to "\u00ED",
                "icirc" to "\u00EE",
                "iuml" to "\u00EF",
                "eth" to "\u00F0",
                "ntilde" to "\u00F1",
                "ograve" to "\u00F2",
                "oacute" to "\u00F3",
                "ocirc" to "\u00F4",
                "otilde" to "\u00F5",
                "ouml" to "\u00F6",
                "oslash" to "\u00F8",
                "ugrave" to "\u00F9",
                "uacute" to "\u00FA",
                "ucirc" to "\u00FB",
                "uuml" to "\u00FC",
                "yacute" to "\u00FD",
                "thorn" to "\u00FE",
                "yuml" to "\u00FF",
                // Uppercase accented letters
                "Agrave" to "\u00C0",
                "Aacute" to "\u00C1",
                "Acirc" to "\u00C2",
                "Atilde" to "\u00C3",
                "Auml" to "\u00C4",
                "Aring" to "\u00C5",
                "AElig" to "\u00C6",
                "Ccedil" to "\u00C7",
                "Egrave" to "\u00C8",
                "Eacute" to "\u00C9",
                "Ecirc" to "\u00CA",
                "Euml" to "\u00CB",
                "Igrave" to "\u00CC",
                "Iacute" to "\u00CD",
                "Icirc" to "\u00CE",
                "Iuml" to "\u00CF",
                "ETH" to "\u00D0",
                "Ntilde" to "\u00D1",
                "Ograve" to "\u00D2",
                "Oacute" to "\u00D3",
                "Ocirc" to "\u00D4",
                "Otilde" to "\u00D5",
                "Ouml" to "\u00D6",
                "Oslash" to "\u00D8",
                "Ugrave" to "\u00D9",
                "Uacute" to "\u00DA",
                "Ucirc" to "\u00DB",
                "Uuml" to "\u00DC",
                "Yacute" to "\u00DD",
                "THORN" to "\u00DE",
                // Other common
                "szlig" to "\u00DF",
                "iexcl" to "\u00A1",
                "iquest" to "\u00BF",
                "ordf" to "\u00AA",
                "ordm" to "\u00BA",
                "not" to "\u00AC",
                "macr" to "\u00AF",
                "acute" to "\u00B4",
                "cedil" to "\u00B8",
                "micro" to "\u00B5",
                "sup1" to "\u00B9",
                "sup2" to "\u00B2",
                "sup3" to "\u00B3",
            )
    }

    override suspend fun parse(
        context: Context,
        uri: Uri,
    ): Book =
        withContext(dispatcherProvider.io) {
            val bookId = BookId(UUID.randomUUID().toString())
            val zipTextEntries = mutableMapOf<String, ByteArray>() // key = lowercased path
            val zipEntryNamesLower = mutableSetOf<String>()

            // Pass 1: read only text/XML resources (OPF, XHTML, container.xml) so we can discover
            // the cover and referenced images without keeping all binary assets in memory.
            requireNotNull(context.contentResolver.openInputStream(uri)) {
                "Unable to read EPUB file"
            }.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val nameLower = entry.name.lowercase()
                            zipEntryNamesLower.add(nameLower)
                            val isTextFile =
                                nameLower.endsWith(".xml") ||
                                    nameLower.endsWith(".xhtml") ||
                                    nameLower.endsWith(".html") ||
                                    nameLower.endsWith(".htm") ||
                                    nameLower.endsWith(".opf") ||
                                    nameLower.endsWith(".ncx")

                            if (isTextFile) {
                                val bytes = readEntryWithLimit(zip, MAX_ENTRY_SIZE)
                                if (bytes != null) {
                                    zipTextEntries[entry.name.lowercase()] = bytes
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            require(zipTextEntries.isNotEmpty()) {
                "EPUB file appears to be empty or corrupted"
            }

            // Parse container.xml to find the OPF file location
            val containerXmlBytes = zipTextEntries["meta-inf/container.xml"]
            val containerXml =
                containerXmlBytes?.let { decodeTextEntry(it) }
            val opfKey =
                resolveOpfPath(
                    containerXml?.let(::parseContainerXml),
                    zipEntryNamesLower,
                ) ?: throw IllegalArgumentException("Invalid EPUB: cannot find OPF file")

            val opfContent =
                requireNotNull(zipTextEntries[opfKey]) {
                    "Invalid EPUB: missing OPF file at $opfKey"
                }

            // Get the base directory of the OPF file for resolving relative paths
            val opfDir = opfKey.substringBeforeLast('/', "")

            // Parse the OPF file
            val opfData = parseOpfFile(decodeTextEntry(opfContent))

            val coverPathLower =
                opfData.coverHref?.let { resolveZipEntryKey(opfDir, it, zipEntryNamesLower) }

            // Determine which image assets we need (cover + any chapter <img> references).
            val neededImagePathsLower = mutableSetOf<String>()
            coverPathLower?.let { neededImagePathsLower.add(it) }

            var spineChapterPathsLower =
                opfData.spineItems.mapNotNull { spineItem ->
                    val href = opfData.manifest[spineItem.idref] ?: return@mapNotNull null
                    resolveZipEntryKey(opfDir, href, zipEntryNamesLower)
                }
            if (spineChapterPathsLower.isEmpty()) {
                spineChapterPathsLower = zipTextEntries.keys.filter(::isHtmlEntry)
            }

            spineChapterPathsLower.forEach { chapterPathLower ->
                val chapterBytes = zipTextEntries[chapterPathLower] ?: return@forEach
                val html = decodeTextEntry(chapterBytes)
                val chapterDir = chapterPathLower.substringBeforeLast('/', "")
                extractImageSrcs(html).forEach { rawSrc ->
                    val src = sanitizeSrc(rawSrc)
                    if (src.isBlank()) return@forEach
                    if (src.startsWith("data:", ignoreCase = true)) return@forEach
                    if (src.startsWith("http://", ignoreCase = true) ||
                        src.startsWith("https://", ignoreCase = true)
                    ) {
                        return@forEach
                    }
                    resolveZipEntryKey(chapterDir, src, zipEntryNamesLower)?.let {
                        neededImagePathsLower.add(it)
                    }
                }
            }

            // Pass 2: extract the needed image bytes from the ZIP and persist them as files.
            // This avoids storing large base64 blobs in the DB (which can crash CursorWindow).
            val imageRelativePathByEpubPathLower = mutableMapOf<String, String>()
            var totalImageBytes = 0L
            var coverImage: ByteArray? = null

            val imageDir = File(context.filesDir, "kairo_epub_assets/${bookId.value}/images")
            val canWriteImages = runCatching {
                imageDir.mkdirs() || imageDir.exists()
            }.getOrDefault(false)

            if (neededImagePathsLower.isNotEmpty()) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val nameLower = entry.name.lowercase()
                                if (neededImagePathsLower.contains(nameLower)) {
                                    val maxEntrySize =
                                        if (nameLower ==
                                            coverPathLower
                                        ) {
                                            MAX_COVER_IMAGE_ENTRY_SIZE
                                        } else {
                                            MAX_IMAGE_ENTRY_SIZE
                                        }
                                    val bytes = readEntryWithLimit(zip, maxEntrySize)
                                    if (bytes != null) {
                                        totalImageBytes += bytes.size
                                        if (totalImageBytes > MAX_TOTAL_IMAGE_SIZE) break
                                        if (nameLower == coverPathLower) {
                                            coverImage = bytes
                                        }

                                        if (canWriteImages) {
                                            val fileName = buildImageFileName(nameLower)
                                            val file = File(imageDir, fileName)
                                            val wrote =
                                                runCatching {
                                                    file.outputStream().use { it.write(bytes) }
                                                    true
                                                }.getOrDefault(false)
                                            if (wrote) {
                                                imageRelativePathByEpubPathLower[nameLower] =
                                                    "kairo_epub_assets/${bookId.value}/images/$fileName"
                                            }
                                        }
                                    }
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
            }

            // Build chapter path to index map for link rewriting
            // First pass: identify valid chapters and their paths
            val spineParsedChapters = opfData.spineItems
                .mapIndexedNotNull { index, spineItem ->
                    val href = opfData.manifest[spineItem.idref] ?: return@mapIndexedNotNull null
                    val chapterPathLower =
                        resolveZipEntryKey(opfDir, href, zipEntryNamesLower)
                            ?: return@mapIndexedNotNull null
                    val chapterContent =
                        zipTextEntries[chapterPathLower] ?: return@mapIndexedNotNull null

                    val originalHtml = decodeTextEntry(chapterContent)
                    val chapterDir = chapterPathLower.substringBeforeLast('/', "")
                    val imagePaths = buildChapterImagePaths(
                        html = originalHtml,
                        baseDir = chapterDir,
                        imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                    )
                    val resolvedHtml = rewriteHtmlImageSrcs(
                        html = originalHtml,
                        baseDir = chapterDir,
                        imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                    )
                    val cleanedHtml = stripNoiseTitleBlocks(resolvedHtml)
                    val plainText = extractPlainText(cleanedHtml)
                    val rawTitle = extractChapterTitle(originalHtml)
                    val title = sanitizeChapterTitle(rawTitle ?: spineItem.idref)

                    // Skip empty chapters
                    if (plainText.isBlank() && imagePaths.isEmpty()) {
                        return@mapIndexedNotNull null
                    }

                    ParsedChapter(
                        pathLower = chapterPathLower,
                        baseDir = chapterDir,
                        chapter = Chapter(
                            index = index,
                            title = title,
                            htmlContent = cleanedHtml,
                            plainText = plainText,
                            imagePaths = imagePaths,
                        ),
                    )
                }.mapIndexed { newIndex, parsed ->
                    // Re-index after filtering out empty chapters
                    parsed.copy(chapter = parsed.chapter.copy(index = newIndex))
                }
            val parsedChapters = spineParsedChapters.ifEmpty {
                buildFallbackChapters(
                    zipTextEntries = zipTextEntries,
                    imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                ).ifEmpty {
                    val htmlEntries = readHtmlEntriesFromZip(context, uri)
                    buildFallbackChapters(
                        zipTextEntries = htmlEntries,
                        imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                    )
                }
            }

            if (coverImage == null) {
                coverImage =
                    resolveCoverFallbackImage(
                        context = context,
                        uri = uri,
                        coverPathLower = coverPathLower,
                        zipEntryNamesLower = zipEntryNamesLower,
                        parsedChapters = parsedChapters,
                    )
            }

            // Build path to final index map
            val chapterIndexByPathLower = parsedChapters.associate { it.pathLower to it.chapter.index }

            // Second pass: rewrite anchor hrefs and extract links with positions (TOC-like only).
            val chapters = parsedChapters.map { parsed ->
                val html = parsed.chapter.htmlContent
                val hasAnchors = html.indexOf("<a", ignoreCase = true) != -1
                val anchorCount =
                    if (hasAnchors) {
                        countAnchorTags(html)
                    } else {
                        0
                    }
                val shouldExtractLinks = anchorCount > 0

                val rewrittenHtml =
                    if (shouldExtractLinks) {
                        rewriteHtmlAnchorHrefs(
                            html = html,
                            baseDir = parsed.baseDir,
                            chapterIndexByPathLower = chapterIndexByPathLower,
                            currentChapterPath = parsed.pathLower,
                        )
                    } else {
                        html
                    }

                parsed.chapter.copy(
                    htmlContent = rewrittenHtml,
                    plainText = parsed.chapter.plainText,
                )
            }

            // Fallback if no chapters found
            val finalChapters =
                chapters.ifEmpty {
                    listOf(
                        Chapter(
                            index = 0,
                            title = "Content",
                            htmlContent = "<p>No readable content found in this EPUB.</p>",
                            plainText = "No readable content found in this EPUB.",
                            imagePaths = emptyList(),
                        ),
                    )
                }

            Book(
                id = bookId,
                title = resolveBookTitle(context, uri, opfData.title),
                authors = opfData.authors,
                languageTag = opfData.languageTag,
                coverImage = coverImage,
                chapters = finalChapters,
            )
        }

    override fun supports(extension: String): Boolean = extension == "epub"

    /**
     * Parses container.xml to find the path to the OPF file.
     */
    private fun parseContainerXml(xml: String): String {
        val fallback = "OEBPS/content.opf"
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // XXE prevention - disable external entity processing
                // Note: We allow DOCTYPE declarations since many valid EPUBs contain them,
                // but external entities are disabled which prevents XXE attacks
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))

            val rootfiles = doc.getElementsByTagName("rootfile")
            if (rootfiles.length > 0) {
                rootfiles
                    .item(0)
                    .attributes
                    .getNamedItem("full-path")
                    ?.nodeValue
                    ?: fallback
            } else {
                fallback
            }
        }.getOrElse { error ->
            Log.w(TAG, "Failed to parse container.xml", error)
            fallback
        }
    }

    /**
     * Data class to hold parsed OPF information.
     */
    private data class OpfData(
        val title: String?,
        val authors: List<String>,
        val languageTag: String?,
        val coverHref: String?,
        val manifest: Map<String, String>, // id -> href
        val spineItems: List<SpineItem>,
    )

    private data class SpineItem(val idref: String)

    private data class ParsedChapter(
        val pathLower: String,
        val baseDir: String,
        val chapter: Chapter,
    )

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String?,
    )

    /**
     * Parses the OPF file to extract metadata, manifest, and spine.
     */
    private fun parseOpfFile(xml: String): OpfData {
        val fallback = OpfData(null, emptyList(), null, null, emptyMap(), emptyList())
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // XXE prevention - disable external entity processing
                // Note: We allow DOCTYPE declarations since many valid EPUBs contain them,
                // but external entities are disabled which prevents XXE attacks
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))

            // Parse metadata
            val title = extractMetadataTexts(doc, "title").firstOrNull()
            val authors = extractMetadataTexts(doc, "creator")
            val languageTag = extractMetadataTexts(doc, "language").firstOrNull()

            // Find cover image reference
            var coverHref: String? = null

            // Method 1: Look for meta with name="cover"
            val metaNodes = findElementsByLocalName(doc, "meta")
            var coverId: String? = null
            for (meta in metaNodes) {
                val name = meta.attributes.getNamedItem("name")?.nodeValue
                if (name == "cover") {
                    coverId = meta.attributes.getNamedItem("content")?.nodeValue
                    break
                }
            }

            // Parse manifest
            val manifest = mutableMapOf<String, String>()
            val manifestNodes = findElementsByLocalName(doc, "item")
            val manifestItems = mutableListOf<ManifestItem>()
            for (item in manifestNodes) {
                val id = item.attributes.getNamedItem("id")?.nodeValue ?: continue
                val hrefRaw = item.attributes.getNamedItem("href")?.nodeValue ?: continue
                val href = normalizeHrefValue(hrefRaw)
                if (href.isBlank()) continue
                val mediaType = item.attributes.getNamedItem("media-type")?.nodeValue

                manifest[id] = href
                manifestItems.add(ManifestItem(id = id, href = href, mediaType = mediaType))
                val isImage =
                    mediaType?.startsWith("image/") == true ||
                        href.endsWith(".jpg", ignoreCase = true) ||
                        href.endsWith(".jpeg", ignoreCase = true) ||
                        href.endsWith(".png", ignoreCase = true) ||
                        href.endsWith(".gif", ignoreCase = true) ||
                        href.endsWith(".webp", ignoreCase = true) ||
                        href.endsWith(".svg", ignoreCase = true)

                // Check if this is the cover image
                if ((id == coverId && isImage) ||
                    (
                        coverHref == null &&
                            isImage &&
                            (
                                id.contains("cover", ignoreCase = true) ||
                                    href.contains("cover", ignoreCase = true)
                                )
                        )
                ) {
                    coverHref = href
                }

                // Method 2: Look for properties="cover-image" (EPUB 3)
                val properties = item.attributes.getNamedItem("properties")?.nodeValue
                if (properties?.contains("cover-image") == true && isImage) {
                    coverHref = href
                }
            }

            // Parse spine
            val spineItems = mutableListOf<SpineItem>()
            val spineNodes = findElementsByLocalName(doc, "itemref")
            for (itemref in spineNodes) {
                val idref = itemref.attributes.getNamedItem("idref")?.nodeValue ?: continue
                // Skip non-linear items (like cover pages that are just images)
                val linear = itemref.attributes.getNamedItem("linear")?.nodeValue
                if (linear != "no") {
                    spineItems.add(SpineItem(idref))
                }
            }

            val resolvedSpine = spineItems.ifEmpty {
                manifestItems
                    .filter { isContentDocument(it.mediaType, it.href) }
                    .map { SpineItem(it.id) }
            }

            OpfData(title, authors, languageTag, coverHref, manifest, resolvedSpine)
        }.getOrElse { error ->
            Log.w(TAG, "Failed to parse OPF metadata", error)
            fallback
        }
    }

    /**
     * Resolves a relative path against a base directory.
     * Prevents path traversal attacks by ensuring the result stays within the EPUB structure.
     */
    private fun resolvePath(
        baseDir: String,
        relativePath: String,
    ): String {
        // Reject absolute paths and protocol-based paths outright
        if (relativePath.contains("://")) {
            return ""
        }

        val pathToResolve = relativePath.removePrefix("./").trimStart('/', '\\')
        if (baseDir.isEmpty()) {
            // Validate there's no path traversal escaping root
            return validateNoEscape(pathToResolve)
        }

        // Handle ../ in paths
        val baseParts = baseDir.split("/").filter { it.isNotEmpty() }.toMutableList()
        val relParts = pathToResolve.split("/")

        for (part in relParts) {
            when (part) {
                ".." -> {
                    if (baseParts.isNotEmpty()) {
                        baseParts.removeAt(baseParts.lastIndex)
                    } else {
                        // Attempting to escape root - reject
                        return ""
                    }
                }
                ".", "" -> { /* ignore */ }
                else -> baseParts.add(part)
            }
        }

        return baseParts.joinToString("/")
    }

    /**
     * Validates that a path doesn't escape the root directory.
     */
    private fun validateNoEscape(path: String): String {
        val parts = path.split("/")
        var depth = 0
        for (part in parts) {
            when (part) {
                ".." -> {
                    depth--
                    if (depth < 0) return "" // Would escape root
                }
                ".", "" -> { /* ignore */ }
                else -> depth++
            }
        }
        return path.split("/").filter { it != "." && it.isNotEmpty() }.joinToString("/")
    }

    private fun extractImageSrcs(html: String): List<String> {
        val matches = mutableListOf<Pair<Int, String>>()
        IMG_SRC_REGEX.findAll(html).forEach { match ->
            matches.add(match.range.first to match.groupValues[1])
        }
        SVG_IMAGE_HREF_REGEX.findAll(html).forEach { match ->
            matches.add(match.range.first to match.groupValues[1])
        }
        return matches.sortedBy { it.first }.map { it.second }
    }

    private fun sanitizeSrc(src: String): String {
        val trimmed = src.trim()
        if (trimmed.isBlank()) return ""
        return normalizeHrefValue(trimmed)
    }

    private fun normalizeHrefValue(href: String): String {
        val withoutFragments = href.substringBefore('#').substringBefore('?').trim()
        if (withoutFragments.isBlank()) return ""
        return decodeUrlPath(decodeHtmlEntities(withoutFragments)).trim()
    }

    private fun resolveZipEntryKey(
        baseDir: String,
        rawHref: String,
        availableEntriesLower: Set<String>,
    ): String? {
        val candidates = buildPathCandidates(rawHref)
        for (candidate in candidates) {
            val resolvedWithBase = resolvePath(baseDir, candidate)
            val matchedWithBase = matchEntryKey(resolvedWithBase, availableEntriesLower)
            if (matchedWithBase != null) return matchedWithBase

            if (baseDir.isNotBlank()) {
                val resolvedRoot = resolvePath("", candidate)
                val matchedRoot = matchEntryKey(resolvedRoot, availableEntriesLower)
                if (matchedRoot != null) return matchedRoot
            }
        }
        return null
    }

    private fun buildPathCandidates(rawHref: String): List<String> {
        val trimmed = rawHref.trim()
        if (trimmed.isBlank()) return emptyList()
        val base = trimmed.substringBefore('#').substringBefore('?').trim()
        if (base.isBlank()) return emptyList()

        val variants = LinkedHashSet<String>()
        fun addVariant(value: String) {
            if (value.isNotBlank()) variants.add(value)
        }

        addVariant(base)
        addVariant(base.replace('\\', '/'))

        val htmlDecoded = decodeHtmlEntities(base)
        addVariant(htmlDecoded)
        addVariant(htmlDecoded.replace('\\', '/'))

        val urlDecoded = decodeUrlPath(htmlDecoded)
        addVariant(urlDecoded)
        addVariant(urlDecoded.replace('\\', '/'))

        addVariant(encodeUrlPath(htmlDecoded))
        if (urlDecoded != htmlDecoded) {
            addVariant(encodeUrlPath(urlDecoded))
        }

        val normalized = LinkedHashSet<String>()
        for (variant in variants) {
            val cleaned =
                variant
                    .trim()
                    .removePrefix("./")
                    .trimStart('/', '\\')
                    .replace('\\', '/')
            if (cleaned.isNotBlank()) normalized.add(cleaned)
        }
        return normalized.toList()
    }

    private fun matchEntryKey(
        resolvedPath: String,
        availableEntriesLower: Set<String>,
    ): String? {
        if (resolvedPath.isBlank()) return null
        val lower = resolvedPath.lowercase()
        return if (availableEntriesLower.contains(lower)) lower else null
    }

    private fun resolveBookTitle(
        context: Context,
        uri: Uri,
        opfTitle: String?,
    ): String {
        val normalizedOpfTitle = opfTitle?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedOpfTitle != null) return normalizedOpfTitle

        val displayName =
            runCatching {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && index >= 0) {
                            cursor.getString(index)
                        } else {
                            null
                        }
                    }
            }.getOrNull()

        val fallback = displayName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }
        return fallback
            ?: uri.lastPathSegment?.substringBeforeLast('.')?.trim()
            ?: "Unknown Book"
    }

    private fun resolveOpfPath(
        rawPath: String?,
        availableEntriesLower: Set<String>,
    ): String? {
        val candidates = LinkedHashSet<String>()
        if (!rawPath.isNullOrBlank()) {
            val trimmed = rawPath.trim()
            candidates.add(trimmed)
            candidates.add(trimmed.replace('\\', '/'))
            candidates.add(trimmed.removePrefix("./"))
            candidates.add(trimmed.trimStart('/', '\\'))
            val normalized = normalizeContainerPath(trimmed)
            if (normalized.isNotBlank()) {
                candidates.add(normalized)
            }
        }

        val matched =
            candidates
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { it.replace('\\', '/').removePrefix("./").trimStart('/') }
                .firstOrNull { availableEntriesLower.contains(it.lowercase()) }
        if (matched != null) return matched.lowercase()

        return availableEntriesLower.firstOrNull { it.endsWith(".opf") }
    }

    private fun normalizeContainerPath(path: String): String {
        var cleaned = decodeUrlPath(decodeHtmlEntities(path)).trim()
        cleaned = cleaned.replace('\\', '/')
        cleaned = cleaned.removePrefix("./")
        cleaned = cleaned.trimStart('/')
        return cleaned
    }

    private fun buildFallbackChapters(
        zipTextEntries: Map<String, ByteArray>,
        imageRelativePathByEpubPathLower: Map<String, String>,
    ): List<ParsedChapter> {
        val candidates = zipTextEntries.keys.filter(::isHtmlEntry).sorted()
        if (candidates.isEmpty()) return emptyList()

        val parsed =
            candidates.mapNotNull { pathLower ->
                val chapterContent = zipTextEntries[pathLower] ?: return@mapNotNull null
                val originalHtml = decodeTextEntry(chapterContent)
                val chapterDir = pathLower.substringBeforeLast('/', "")
                val imagePaths = buildChapterImagePaths(
                    html = originalHtml,
                    baseDir = chapterDir,
                    imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                )
                val resolvedHtml = rewriteHtmlImageSrcs(
                    html = originalHtml,
                    baseDir = chapterDir,
                    imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                )
                val cleanedHtml = stripNoiseTitleBlocks(resolvedHtml)
                val plainText = extractPlainText(cleanedHtml)
                val rawTitle = extractChapterTitle(originalHtml)
                val fileTitle =
                    pathLower
                        .substringAfterLast('/', pathLower)
                        .substringBeforeLast('.')
                val title = sanitizeChapterTitle(rawTitle ?: fileTitle)

                if (plainText.isBlank() && imagePaths.isEmpty()) {
                    return@mapNotNull null
                }

                ParsedChapter(
                    pathLower = pathLower,
                    baseDir = chapterDir,
                    chapter = Chapter(
                        index = 0,
                        title = title,
                        htmlContent = cleanedHtml,
                        plainText = plainText,
                        imagePaths = imagePaths,
                    ),
                )
            }

        return parsed.mapIndexed { index, entry ->
            entry.copy(chapter = entry.chapter.copy(index = index))
        }
    }

    private fun isHtmlEntry(pathLower: String): Boolean {
        return pathLower.endsWith(".xhtml") ||
            pathLower.endsWith(".html") ||
            pathLower.endsWith(".htm")
    }

    private fun readHtmlEntriesFromZip(
        context: Context,
        uri: Uri,
    ): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val nameLower = entry.name.lowercase()
                        if (isHtmlEntry(nameLower)) {
                            val bytes = readEntryWithLimit(zip, MAX_ENTRY_SIZE)
                            if (bytes != null) {
                                entries[nameLower] = bytes
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return entries
    }

    private fun resolveCoverFallbackImage(
        context: Context,
        uri: Uri,
        coverPathLower: String?,
        zipEntryNamesLower: Set<String>,
        parsedChapters: List<ParsedChapter>,
    ): ByteArray? {
        if (coverPathLower != null) {
            val bytes = readZipEntryBytes(context, uri, coverPathLower)
            if (bytes != null && bytes.isNotEmpty()) return bytes
        }

        val chapterImagePath = parsedChapters.firstOrNull()?.chapter?.imagePaths?.firstOrNull()
        if (chapterImagePath != null) {
            val file = File(context.filesDir, chapterImagePath)
            if (file.exists()) {
                val sizeOk = file.length() <= MAX_COVER_IMAGE_ENTRY_SIZE
                if (sizeOk) {
                    val bytes = runCatching { file.readBytes() }.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) return bytes
                }
            }
        }

        val coverCandidates = zipEntryNamesLower.filter(::isImageEntry)
        val coverNamed =
            coverCandidates.firstOrNull { it.contains("cover", ignoreCase = true) }
        val fallback = coverNamed ?: coverCandidates.firstOrNull()
        if (fallback != null) {
            val bytes = readZipEntryBytes(context, uri, fallback)
            if (bytes != null && bytes.isNotEmpty()) return bytes
        }

        return null
    }

    private fun readZipEntryBytes(
        context: Context,
        uri: Uri,
        targetLower: String,
    ): ByteArray? {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val nameLower = entry.name.lowercase()
                        if (nameLower == targetLower) {
                            return readEntryWithLimit(zip, MAX_COVER_IMAGE_ENTRY_SIZE)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return null
    }

    private fun isImageEntry(pathLower: String): Boolean {
        return pathLower.endsWith(".jpg") ||
            pathLower.endsWith(".jpeg") ||
            pathLower.endsWith(".png") ||
            pathLower.endsWith(".gif") ||
            pathLower.endsWith(".webp") ||
            pathLower.endsWith(".svg")
    }

    private fun buildChapterImagePaths(
        html: String,
        baseDir: String,
        imageRelativePathByEpubPathLower: Map<String, String>,
    ): List<String> {
        val unique = LinkedHashSet<String>()
        val chapterSrcs = extractImageSrcs(html)
        for (rawSrc in chapterSrcs) {
            val src = sanitizeSrc(rawSrc)
            if (src.isBlank()) continue
            if (src.startsWith("data:", ignoreCase = true)) continue
            if (src.startsWith("http://", ignoreCase = true) ||
                src.startsWith("https://", ignoreCase = true)
            ) {
                continue
            }

            val resolvedLower =
                resolveZipEntryKey(baseDir, src, imageRelativePathByEpubPathLower.keys)
                    ?: continue
            val relativePath = imageRelativePathByEpubPathLower[resolvedLower] ?: continue
            unique.add(relativePath)
        }
        return unique.toList()
    }

    private fun buildImageFileName(epubPathLower: String): String {
        val extRaw = epubPathLower.substringAfterLast('.', missingDelimiterValue = "")
        val ext = extRaw.take(10).filter { it.isLetterOrDigit() }
        val base = UUID.nameUUIDFromBytes(epubPathLower.toByteArray(Charsets.UTF_8)).toString()
        return if (ext.isNotEmpty()) "img_$base.$ext" else "img_$base"
    }

    private fun rewriteHtmlImageSrcs(
        html: String,
        baseDir: String,
        imageRelativePathByEpubPathLower: Map<String, String>,
    ): String {
        val rewritten =
            IMG_SRC_REWRITE_REGEX.replace(html) { match ->
                val rawSrc = match.groupValues[2]
                val src = sanitizeSrc(rawSrc)
                if (src.isBlank()) return@replace match.value
                if (src.startsWith("data:", ignoreCase = true)) return@replace match.value
                if (src.startsWith("http://", ignoreCase = true) ||
                    src.startsWith("https://", ignoreCase = true)
                ) {
                    return@replace match.value
                }
                if (src.startsWith("kairo_epub_assets/")) return@replace match.value

            val resolvedLower =
                resolveZipEntryKey(baseDir, src, imageRelativePathByEpubPathLower.keys)
                    ?: return@replace match.value
            val relativePath =
                imageRelativePathByEpubPathLower[resolvedLower] ?: return@replace match.value
            "${match.groupValues[1]}$relativePath${match.groupValues[3]}"
        }
        return SVG_IMAGE_REWRITE_REGEX.replace(rewritten) { match ->
            val rawSrc = match.groupValues[2]
            val src = sanitizeSrc(rawSrc)
            if (src.isBlank()) return@replace match.value
            if (src.startsWith("data:", ignoreCase = true)) return@replace match.value
            if (src.startsWith("http://", ignoreCase = true) ||
                src.startsWith("https://", ignoreCase = true)
            ) {
                return@replace match.value
            }
            if (src.startsWith("kairo_epub_assets/")) return@replace match.value

            val resolvedLower =
                resolveZipEntryKey(baseDir, src, imageRelativePathByEpubPathLower.keys)
                    ?: return@replace match.value
            val relativePath =
                imageRelativePathByEpubPathLower[resolvedLower] ?: return@replace match.value
            "${match.groupValues[1]}$relativePath${match.groupValues[3]}"
        }
    }

    /**
     * Extracts plain text from HTML/XHTML content.
     */
    private fun extractPlainText(html: String): String =
        normalizePageBreakElements(html)
            // Remove HTML comments
            .replace(HTML_COMMENT_REGEX, "")
            // Remove scripts, styles, and noscript
            .replace(SCRIPT_TAG_REGEX, "")
            .replace(STYLE_TAG_REGEX, "")
            .replace(NOSCRIPT_TAG_REGEX, "")
            // Convert block elements to newlines
            .replace(BLOCK_OPEN_TAG_REGEX, "\n")
            .replace(BLOCK_CLOSE_TAG_REGEX, "\n")
            // Remove all remaining tags
            .replace(ALL_TAGS_REGEX, "")
            // Decode common HTML entities
            .let(::decodeHtmlEntities)
            // Clean up whitespace
            .replace(HORIZONTAL_WHITESPACE_REGEX, " ")
            .replace(MULTIPLE_NEWLINES_REGEX, "\n\n")
            .trim()

    private fun normalizePageBreakElements(html: String): String {
        val pageBreakToken = " "
        return html
            .replace(EPUB_PAGE_BREAK_REGEX, pageBreakToken)
            .replace(EPUB_PAGE_BREAK_SELF_CLOSING_REGEX, pageBreakToken)
            .replace(CLASS_PAGE_BREAK_REGEX, pageBreakToken)
            .replace(CLASS_PAGE_BREAK_SELF_CLOSING_REGEX, pageBreakToken)
    }

    /**
     * Extracts chapter title from HTML content.
     */
    private fun extractChapterTitle(html: String): String? {
        // Try to find title in <title> tag
        val titleMatch = TITLE_TAG_REGEX.find(html)
        if (titleMatch != null) {
            val title = decodeHtmlEntities(titleMatch.groupValues[1]).trim()
            if (title.isNotBlank() && !title.equals("untitled", ignoreCase = true)) {
                return title
            }
        }

        // Try to find first heading (handles nested elements like <h1><span>Title</span></h1>)
        val headingMatch = HEADING_TAG_REGEX.find(html)
        if (headingMatch != null) {
            // Strip inner HTML tags and decode entities
            val innerHtml = headingMatch.groupValues[2]
            val heading = decodeHtmlEntities(innerHtml.replace(ALL_TAGS_REGEX, ""))
                .replace(WHITESPACE_REGEX, " ")
                .trim()
            if (heading.isNotBlank()) {
                return heading.take(100) // Limit title length
            }
        }

        return null
    }

    private fun sanitizeChapterTitle(title: String?): String? {
        val trimmed = title?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (isLikelyFileLabel(trimmed)) null else trimmed
    }

    private fun stripNoiseTitleBlocks(html: String): String {
        if (html.isBlank()) return html
        return BLOCK_ELEMENT_REGEX.replace(html) { match ->
            val inner = match.groupValues[2]
            val text =
                decodeHtmlEntities(inner.replace(ALL_TAGS_REGEX, " "))
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
            if (text.length <= MAX_NOISE_TITLE_LENGTH && isLikelyFileLabel(text)) {
                ""
            } else {
                match.value
            }
        }
    }

    private fun isLikelyFileLabel(text: String): Boolean {
        val normalized = normalizeNoiseLabel(text)
        if (normalized.isBlank()) return false
        val compact = normalized.replace(Regex("[\\s_-]+"), "")
        val numberedMatch = FILE_LABEL_WITH_NUMBER_REGEX.matchEntire(compact)
        if (numberedMatch != null) {
            val zeros = numberedMatch.groupValues[2]
            val digits = numberedMatch.groupValues[3]
            if (zeros.isNotEmpty() || digits.length >= 3) return true
        }
        return GENERIC_FILE_LABEL_REGEX.matches(compact)
    }

    private fun normalizeNoiseLabel(text: String): String {
        val trimmed = text.trim().lowercase()
        if (trimmed.isBlank()) return ""
        return trimmed.substringBeforeLast('.', trimmed)
    }

    /**
     * Reads a ZIP entry with a size limit using buffered reading.
     * Returns null if the entry exceeds the size limit.
     * This prevents OOM by not loading huge entries all at once.
     */
    private fun readEntryWithLimit(
        zip: ZipInputStream,
        maxSize: Int,
    ): ByteArray? {
        val buffer = ByteArray(BUFFER_SIZE)
        val output = java.io.ByteArrayOutputStream()
        var totalRead = 0

        return try {
            var bytesRead: Int
            while (zip.read(buffer).also { bytesRead = it } != -1) {
                totalRead += bytesRead
                if (totalRead > maxSize) {
                    // Entry too large, skip it
                    return null
                }
                output.write(buffer, 0, bytesRead)
            }
            output.toByteArray()
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read EPUB entry", e)
            null
        }
    }

    /**
     * Rewrites internal anchor hrefs to kairo://chapter/X format.
     * This enables the tokenizer to identify clickable links.
     */
    private fun rewriteHtmlAnchorHrefs(
        html: String,
        baseDir: String,
        chapterIndexByPathLower: Map<String, Int>,
        currentChapterPath: String? = null,
    ): String {
        return ANCHOR_HREF_REGEX.replace(html) { match ->
            val prefix = match.groupValues[1]
            val href = decodeHtmlEntities(match.groupValues[2])
            val suffix = match.groupValues[3]

            // Skip external links and data URIs
            if (href.startsWith("http://", true) ||
                href.startsWith("https://", true) ||
                href.startsWith("mailto:", true) ||
                href.startsWith("data:", true) ||
                href.startsWith("kairo://", true)
            ) {
                return@replace match.value
            }

            // Extract path and fragment
            val path = decodeUrlPath(href.substringBefore('#').substringBefore('?')).trim()
            val fragment = decodeUrlPath(href.substringAfter('#', "")).trim()

            // Determine target path
            val targetPath =
                when {
                    path.isNotBlank() ->
                        resolveZipEntryKey(baseDir, path, chapterIndexByPathLower.keys)
                    fragment.isNotBlank() && currentChapterPath != null ->
                        currentChapterPath.lowercase()
                    else -> null
                } ?: return@replace match.value

            val chapterIndex = chapterIndexByPathLower[targetPath] ?: return@replace match.value

            "${prefix}kairo://chapter/$chapterIndex${suffix}"
        }
    }

    private fun countAnchorTags(html: String): Int {
        val limit = MAX_LINKS_PER_CHAPTER + 1
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

    private fun decodeHtmlEntities(input: String): String {
        if (!input.contains('&')) return input

        val sb = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val ampIndex = input.indexOf('&', index)
            if (ampIndex == -1) {
                sb.append(input, index, input.length)
                break
            }
            sb.append(input, index, ampIndex)
            val semiIndex = input.indexOf(';', ampIndex + 1)
            if (semiIndex == -1) {
                sb.append(input, ampIndex, input.length)
                break
            }
            val entity = input.substring(ampIndex + 1, semiIndex)
            val decoded = decodeEntity(entity)
            if (decoded != null) {
                sb.append(decoded)
            } else {
                sb.append(input, ampIndex, semiIndex + 1)
            }
            index = semiIndex + 1
        }
        return sb.toString()
    }

    private fun decodeEntity(entity: String): String? {
        if (entity.isBlank()) return null
        if (entity[0] == '#') {
            val codePoint =
                if (entity.length > 1 && (entity[1] == 'x' || entity[1] == 'X')) {
                    entity.substring(2).toIntOrNull(16)
                } else {
                    entity.substring(1).toIntOrNull()
                }
            return codePoint?.let { toCodePointString(it) }
        }
        return NAMED_HTML_ENTITIES[entity.lowercase()]
    }

    private fun toCodePointString(codePoint: Int): String? {
        if (codePoint !in 0..0x10FFFF) return null
        return if (codePoint <= Char.MAX_VALUE.code) {
            codePoint.toChar().toString()
        } else {
            String(Character.toChars(codePoint))
        }
    }

    private fun extractMetadataTexts(
        doc: org.w3c.dom.Document,
        localName: String,
    ): List<String> {
        val candidates =
            listOf(
                doc.getElementsByTagName("dc:$localName"),
                doc.getElementsByTagNameNS(DC_NAMESPACE, localName),
                doc.getElementsByTagName(localName),
            )

        for (nodes in candidates) {
            val texts = extractTextContents(nodes)
            if (texts.isNotEmpty()) return texts
        }
        return emptyList()
    }

    private fun extractTextContents(nodes: org.w3c.dom.NodeList): List<String> {
        if (nodes.length <= 0) return emptyList()
        val results = ArrayList<String>(nodes.length)
        for (i in 0 until nodes.length) {
            val text =
                nodes.item(i)
                    ?.textContent
                    ?.let(::decodeHtmlEntities)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            if (text != null) {
                results.add(text)
            }
        }
        return results
    }

    private fun findElementsByLocalName(
        doc: org.w3c.dom.Document,
        localName: String,
    ): List<org.w3c.dom.Element> {
        val nodes = doc.getElementsByTagName("*")
        if (nodes.length <= 0) return emptyList()
        val results = mutableListOf<org.w3c.dom.Element>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is org.w3c.dom.Element) {
                val name =
                    node.localName
                        ?: node.nodeName.substringAfterLast(':', node.nodeName)
                if (name.equals(localName, ignoreCase = true)) {
                    results.add(node)
                }
            }
        }
        return results
    }

    private fun isContentDocument(
        mediaType: String?,
        href: String,
    ): Boolean {
        if (mediaType != null) {
            val normalized = mediaType.lowercase()
            if (normalized.contains("xhtml") || normalized.contains("html")) {
                return true
            }
        }
        return href.endsWith(".xhtml", ignoreCase = true) ||
            href.endsWith(".html", ignoreCase = true) ||
            href.endsWith(".htm", ignoreCase = true)
    }

    private fun decodeUrlPath(input: String): String {
        if (!input.contains('%') && !input.contains('+')) return input
        val safe = input.replace("+", "%2B")
        return runCatching { URLDecoder.decode(safe, "UTF-8") }.getOrDefault(input)
    }

    private fun encodeUrlPath(input: String): String {
        if (input.isBlank()) return input
        val sb = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val codePoint = input.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            if (isUnreservedAscii(codePoint) || codePoint == '/'.code) {
                sb.appendCodePoint(codePoint)
            } else {
                val bytes = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
                for (b in bytes) {
                    sb.append('%')
                    val hex = (b.toInt() and 0xFF).toString(16).uppercase()
                    if (hex.length == 1) sb.append('0')
                    sb.append(hex)
                }
            }
            index += charCount
        }
        return sb.toString()
    }

    private fun isUnreservedAscii(codePoint: Int): Boolean {
        return (codePoint in 'a'.code..'z'.code) ||
            (codePoint in 'A'.code..'Z'.code) ||
            (codePoint in '0'.code..'9'.code) ||
            codePoint == '-'.code ||
            codePoint == '.'.code ||
            codePoint == '_'.code ||
            codePoint == '~'.code
    }

    private fun decodeTextEntry(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        val bomCharset = detectBomCharset(bytes)
        if (bomCharset != null) {
            return runCatching { String(bytes, bomCharset) }.getOrDefault("")
        }

        val sample = bytes.copyOf(minOf(bytes.size, 4096))
        val asciiSample = String(sample, Charsets.ISO_8859_1)
        val declaredCharset = extractDeclaredCharset(asciiSample)
        if (declaredCharset != null) {
            val charset = runCatching { Charset.forName(declaredCharset) }.getOrNull()
            if (charset != null) {
                return runCatching { String(bytes, charset) }.getOrDefault("")
            }
        }

        val utf16 = detectUtf16Heuristic(sample)
        if (utf16 != null) {
            return runCatching { String(bytes, utf16) }.getOrDefault("")
        }

        return runCatching { String(bytes, Charsets.UTF_8) }.getOrDefault("")
    }

    private fun detectBomCharset(bytes: ByteArray): Charset? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return Charsets.UTF_8
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return Charsets.UTF_16LE
            }
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return Charsets.UTF_16BE
            }
        }
        if (bytes.size >= 4) {
            if (bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xFE.toByte() &&
                bytes[2] == 0x00.toByte() &&
                bytes[3] == 0x00.toByte()
            ) {
                return Charset.forName("UTF-32LE")
            }
            if (bytes[0] == 0x00.toByte() &&
                bytes[1] == 0x00.toByte() &&
                bytes[2] == 0xFE.toByte() &&
                bytes[3] == 0xFF.toByte()
            ) {
                return Charset.forName("UTF-32BE")
            }
        }
        return null
    }

    private fun detectUtf16Heuristic(sample: ByteArray): Charset? {
        if (sample.isEmpty()) return null
        var evenZeros = 0
        var oddZeros = 0
        var total = 0
        var index = 0
        while (index < sample.size) {
            if (sample[index] == 0.toByte()) {
                if (index % 2 == 0) evenZeros += 1 else oddZeros += 1
            }
            total += 1
            index += 1
        }
        if (total < 8) return null
        val evenRatio = evenZeros.toDouble() / total.toDouble()
        val oddRatio = oddZeros.toDouble() / total.toDouble()
        if (evenRatio > 0.2 && oddRatio < 0.05) return Charsets.UTF_16BE
        if (oddRatio > 0.2 && evenRatio < 0.05) return Charsets.UTF_16LE
        return null
    }

    private fun extractDeclaredCharset(sample: String): String? {
        val xmlMatch =
            Regex("""<\?xml[^>]*encoding=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
                .find(sample)
        if (xmlMatch != null) return xmlMatch.groupValues[1].trim()

        val metaCharset =
            Regex(
                """<meta[^>]*charset=['"]?([^'"\s/>]+)""",
                RegexOption.IGNORE_CASE,
            ).find(sample)
        if (metaCharset != null) return metaCharset.groupValues[1].trim()

        val contentType =
            Regex(
                """<meta[^>]*http-equiv=['"]content-type['"][^>]*content=['"][^'"]*charset=([^'"\s/>]+)""",
                RegexOption.IGNORE_CASE,
            ).find(sample)
        return contentType?.groupValues?.getOrNull(1)?.trim()
    }
}
