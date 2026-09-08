package com.kairo.reader.data.books

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.data.books.epub.ChapterOrderResolution
import com.kairo.reader.data.books.epub.ChapterOrderSource
import com.kairo.reader.data.books.epub.ContainerXmlResolution
import com.kairo.reader.data.books.epub.EpubChapterOrdering
import com.kairo.reader.data.books.epub.EpubContainerParser
import com.kairo.reader.data.books.epub.EpubLogger
import com.kairo.reader.data.books.epub.EpubOpfParser
import com.kairo.reader.data.books.epub.EpubPathResolver
import com.kairo.reader.data.books.epub.EpubReaderChapterPlanner
import com.kairo.reader.data.books.epub.EpubTextDecoder
import com.kairo.reader.data.books.epub.OpfData
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

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

        // Max total size for in-memory text entries (40 MB)
        private const val MAX_TOTAL_TEXT_SIZE = 40 * 1024 * 1024

        // Buffer size for reading entries
        private const val BUFFER_SIZE = 8192
    }

    private val contentRewriter = EpubContentRewriter()
    private val navigationClassifier = EpubNavigationClassifier(contentRewriter)
    private val navigationParser = EpubNavigationParser()
    private val tableOfContentsResolver = EpubTableOfContentsResolver(contentRewriter)
    private val chapterBuilder = EpubChapterBuilder(contentRewriter, navigationClassifier)
    private val containerParser = EpubContainerParser()
    private val opfParser = EpubOpfParser()
    private val packageSelector = EpubPackageSelector(opfParser)

    override suspend fun parse(
        context: Context,
        uri: Uri,
        bookId: BookId,
    ): Book = parse(context, uri, bookId, sourceDisplayName = null)

    @Suppress("CyclomaticComplexMethod", "LongMethod", "LoopWithTooManyJumpStatements")
    override suspend fun parse(
        context: Context,
        uri: Uri,
        bookId: BookId,
        sourceDisplayName: String?,
    ): Book =
        withContext(dispatcherProvider.io) {
            val zipTextEntries = mutableMapOf<String, ByteArray>() // key = lowercased path
            val zipEntryNamesLower = mutableSetOf<String>()
            val oversizedTextEntriesLower = mutableSetOf<String>()
            val decodedTextEntries = mutableMapOf<String, String>()
            val chapterImageSrcsByPathLower = mutableMapOf<String, List<String>>()
            val diagnostics = ParseDiagnostics()
            var totalTextBytes = 0L

            // Pass 1: read only text/XML resources (OPF, XHTML, container.xml) so we can discover
            // the cover and referenced images without keeping all binary assets in memory.
            requireNotNull(context.contentResolver.openInputStream(uri)) {
                "Unable to read EPUB file"
            }.use { inputStream ->
                openZip(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val nameLower = normalizeZipEntryNameLower(entry.name)
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
                                if (bytes != null && !zipTextEntries.containsKey(nameLower)) {
                                    if (totalTextBytes + bytes.size <= MAX_TOTAL_TEXT_SIZE) {
                                        zipTextEntries[nameLower] = bytes
                                        totalTextBytes += bytes.size
                                    } else {
                                        oversizedTextEntriesLower.add(nameLower)
                                        logWarn(
                                            "Skipping EPUB text entry due to total text budget: ${entry.name}",
                                        )
                                    }
                                } else if (bytes != null && zipTextEntries.containsKey(nameLower)) {
                                    logWarn("Case-colliding EPUB text entry ignored: ${entry.name}")
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
            val containerResolution = containerXml?.let(::parseContainerXmlWithResult)
            diagnostics.usedLenientContainerFallback =
                containerResolution?.usedLenientFallback == true
            val opfSelection =
                packageSelector.selectBest(
                    containerCandidates = containerResolution?.candidatePaths.orEmpty(),
                    zipEntryNamesLower = zipEntryNamesLower,
                    zipTextEntries = zipTextEntries,
                )
            val opfKey = opfSelection.path
                ?: throw IllegalArgumentException("Invalid EPUB: cannot find OPF file")
            val opfParseResult = opfSelection.parseResult
                ?: throw IllegalArgumentException("Invalid EPUB: cannot parse OPF file at $opfKey")
            val opfData = opfParseResult.opfData
            diagnostics.usedLenientOpfFallback = opfParseResult.usedLenientFallback
            // Get the base directory of the OPF file for resolving relative paths
            val opfDir = opfKey.substringBeforeLast('/', "")

            val coverPathLower =
                opfData.coverHref?.let { resolveZipEntryKey(opfDir, it, zipEntryNamesLower) }
            val chapterOrderResolution =
                resolveChapterOrder(
                    opfData = opfData,
                    opfDir = opfDir,
                    availableEntriesLower = zipEntryNamesLower,
                    availableTextEntriesLower = zipTextEntries.keys,
                )
            diagnostics.chapterOrderSource = chapterOrderResolution.source
            diagnostics.unresolvedSpineItems = chapterOrderResolution.unresolvedSpineCount
            val navigationPathLower =
                opfData.navigationHref?.let { href ->
                    resolveZipEntryKey(opfDir, href, zipTextEntries.keys)
                }
            val readerChapterPlan =
                EpubReaderChapterPlanner.create(
                    readingOrderPaths = chapterOrderResolution.paths,
                    spinePaths = chapterOrderResolution.resolvedSpinePaths,
                    navigationPath = navigationPathLower,
                )
            val orderedChapterPathsLower = readerChapterPlan.paths
            val navigationParseResult =
                navigationPathLower
                    ?.let { path -> decodedTextEntry(path, zipTextEntries, decodedTextEntries) }
                    ?.let { document ->
                        navigationParser.parse(
                            document = document,
                            isNcx = navigationPathLower.endsWith(".ncx", ignoreCase = true),
                        )
                    } ?: EpubNavigationParseResult(emptyList())
            val navigationReferences = navigationParseResult.references
            val readerHtmlOverridesByPathLower =
                navigationPathLower
                    ?.let { path ->
                        navigationParseResult.readerHtml?.let { html -> mapOf(path to html) }
                    }.orEmpty()

            // Determine which image assets we need (cover + any chapter <img> references).
            val neededImagePathsLower = mutableSetOf<String>()
            val fallbackCoverPathLower =
                if (coverPathLower == null) {
                    EpubCoverSelector.select(zipEntryNamesLower.filter(::isImageEntry))
                } else {
                    null
                }
            (coverPathLower ?: fallbackCoverPathLower)?.let { neededImagePathsLower.add(it) }

            val chapterPathsForImageScan =
                orderedChapterPathsLower.ifEmpty {
                    zipTextEntries.keys
                        .asSequence()
                        .filter(::isChapterCandidateEntry)
                        .sortedWith(::comparePathsNaturally)
                        .toList()
                }

            chapterPathsForImageScan.forEach { chapterPathLower ->
                val html =
                    readerHtmlOverridesByPathLower[chapterPathLower]
                        ?: decodedTextEntry(chapterPathLower, zipTextEntries, decodedTextEntries)
                        ?: return@forEach
                val chapterDir = chapterPathLower.substringBeforeLast('/', "")
                val imageSrcs = contentRewriter.extractImageSrcs(html)
                if (imageSrcs.isNotEmpty()) {
                    chapterImageSrcsByPathLower[chapterPathLower] = imageSrcs
                }
                imageSrcs.forEach { rawSrc ->
                    val src = contentRewriter.sanitizeSrc(rawSrc)
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
                    openZip(inputStream).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val nameLower = normalizeZipEntryNameLower(entry.name)
                                if (neededImagePathsLower.contains(nameLower)) {
                                    val maxEntrySize =
                                        if (nameLower == coverPathLower ||
                                            nameLower == fallbackCoverPathLower
                                        ) {
                                            MAX_COVER_IMAGE_ENTRY_SIZE
                                        } else {
                                            MAX_IMAGE_ENTRY_SIZE
                                        }
                                    val bytes = readEntryWithLimit(zip, maxEntrySize)
                                    if (bytes != null) {
                                        totalImageBytes += bytes.size
                                        if (totalImageBytes > MAX_TOTAL_IMAGE_SIZE) break
                                        if (nameLower == coverPathLower ||
                                            nameLower == fallbackCoverPathLower
                                        ) {
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

            val primaryFallbackBuild =
                chapterBuilder.buildWithResult(
                    zipTextEntries = zipTextEntries,
                    imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                    preferredChapterPathsLower = orderedChapterPathsLower,
                    decodedTextEntries = decodedTextEntries,
                    chapterImageSrcsByPathLower = chapterImageSrcsByPathLower,
                    preservedNavigationPathsLower = readerChapterPlan.preservedNavigationPaths,
                    htmlOverridesByPathLower = readerHtmlOverridesByPathLower,
                )
            diagnostics.navigationFilteredChapters += primaryFallbackBuild.navigationFilteredCount
            diagnostics.navigationFilterSuppressed =
                diagnostics.navigationFilterSuppressed ||
                primaryFallbackBuild.navigationFilterSuppressed

            val parsedChapters =
                primaryFallbackBuild.chapters.ifEmpty {
                    val htmlEntries =
                        readHtmlEntriesFromZip(
                            context = context,
                            uri = uri,
                            skippedEntriesLower = oversizedTextEntriesLower,
                        )
                    val secondaryFallbackBuild =
                        chapterBuilder.buildWithResult(
                            zipTextEntries = htmlEntries,
                            imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                            preferredChapterPathsLower = orderedChapterPathsLower,
                            decodedTextEntries = emptyMap(),
                            chapterImageSrcsByPathLower = emptyMap(),
                            preservedNavigationPathsLower = readerChapterPlan.preservedNavigationPaths,
                            htmlOverridesByPathLower = readerHtmlOverridesByPathLower,
                        )
                    diagnostics.navigationFilteredChapters += secondaryFallbackBuild.navigationFilteredCount
                    diagnostics.navigationFilterSuppressed =
                        diagnostics.navigationFilterSuppressed ||
                        secondaryFallbackBuild.navigationFilterSuppressed
                    secondaryFallbackBuild.chapters
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
            val tableOfContents =
                navigationPathLower
                    ?.let { path ->
                        tableOfContentsResolver.resolve(
                            references = navigationReferences,
                            navigationPathLower = path,
                            chapters = parsedChapters,
                        )
                    }.orEmpty()

            // Second pass: rewrite anchor hrefs and extract links with positions (TOC-like only).
            val chapters = parsedChapters.map { parsed ->
                val html = parsed.chapter.htmlContent
                val hasAnchors = html.indexOf("<a", ignoreCase = true) != -1
                val anchorCount =
                    if (hasAnchors) {
                        contentRewriter.countAnchorTags(html)
                    } else {
                        0
                    }
                val shouldExtractLinks = anchorCount > 0

                val rewrittenHtml =
                    if (shouldExtractLinks) {
                        contentRewriter.rewriteHtmlAnchorHrefs(
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

            logParseDiagnostics(
                bookId = bookId,
                diagnostics = diagnostics,
                chapterCount = finalChapters.size,
            )

            Book(
                id = bookId,
                title = resolveBookTitle(context, uri, opfData.title, sourceDisplayName),
                authors = opfData.authors,
                languageTag = opfData.languageTag,
                coverImage = coverImage,
                chapters = finalChapters,
                tableOfContents = tableOfContents,
            )
        }

    override fun supports(extension: String): Boolean = extension in BookImportFormats.epub.extensions

    private fun parseContainerXmlWithResult(xml: String): ContainerXmlResolution =
        containerParser.parse(xml)

    private data class ParseDiagnostics(
        var usedLenientContainerFallback: Boolean = false,
        var usedLenientOpfFallback: Boolean = false,
        var chapterOrderSource: ChapterOrderSource = ChapterOrderSource.ZIP_FALLBACK,
        var unresolvedSpineItems: Int = 0,
        var navigationFilteredChapters: Int = 0,
        var navigationFilterSuppressed: Boolean = false,
    )

    private fun normalizeZipEntryNameLower(name: String): String =
        name.replace('\\', '/').trimStart('/').lowercase(Locale.ROOT)

    private fun decodedTextEntry(
        pathLower: String,
        zipTextEntries: Map<String, ByteArray>,
        decodedTextEntries: MutableMap<String, String>,
    ): String? {
        decodedTextEntries[pathLower]?.let { return it }
        val bytes = zipTextEntries[pathLower] ?: return null
        val decoded = decodeTextEntry(bytes)
        decodedTextEntries[pathLower] = decoded
        return decoded
    }

    private fun resolveZipEntryKey(
        baseDir: String,
        rawHref: String,
        availableEntriesLower: Set<String>,
    ): String? = EpubPathResolver.resolveZipEntryKey(baseDir, rawHref, availableEntriesLower)

    private fun resolveBookTitle(
        context: Context,
        uri: Uri,
        opfTitle: String?,
        sourceDisplayName: String?,
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

        val fallback =
            sourceDisplayName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }
                ?: displayName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }
        return fallback
            ?: uri.lastPathSegment?.substringBeforeLast('.')?.trim()
            ?: "Unknown Book"
    }

    private fun resolveChapterOrder(
        opfData: OpfData,
        opfDir: String,
        availableEntriesLower: Set<String>,
        availableTextEntriesLower: Set<String>,
    ): ChapterOrderResolution =
        EpubChapterOrdering.resolveChapterOrder(
            opfData = opfData,
            opfDir = opfDir,
            availableEntriesLower = availableEntriesLower,
            availableTextEntriesLower = availableTextEntriesLower,
        )

    private fun logParseDiagnostics(
        bookId: BookId,
        diagnostics: ParseDiagnostics,
        chapterCount: Int,
    ) {
        logInfo(
            "EPUB parse diagnostics book=${bookId.value} chapters=$chapterCount " +
                "order=${diagnostics.chapterOrderSource} unresolvedSpine=${diagnostics.unresolvedSpineItems} " +
                "lenientContainer=${diagnostics.usedLenientContainerFallback} " +
                "lenientOpf=${diagnostics.usedLenientOpfFallback} " +
                "navFiltered=${diagnostics.navigationFilteredChapters} " +
                "navFilterSuppressed=${diagnostics.navigationFilterSuppressed}",
        )
    }

    private fun isChapterCandidateEntry(pathLower: String): Boolean {
        return EpubChapterOrdering.isChapterCandidateEntry(pathLower)
    }

    private suspend fun readHtmlEntriesFromZip(
        context: Context,
        uri: Uri,
        skippedEntriesLower: Set<String> = emptySet(),
    ): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            openZip(inputStream).use { zip ->
                readChapterEntries(zip, skippedEntriesLower, entries)
            }
        }
        return entries
    }

    private fun readChapterEntries(
        zip: ZipInputStream,
        skippedEntriesLower: Set<String>,
        entries: MutableMap<String, ByteArray>,
    ) {
        var entry = zip.nextEntry
        while (entry != null) {
            val nameLower = normalizeZipEntryNameLower(entry.name)
            val shouldRead =
                !entry.isDirectory &&
                    isChapterCandidateEntry(nameLower) &&
                    nameLower !in skippedEntriesLower
            if (shouldRead) {
                readEntryWithLimit(zip, MAX_ENTRY_SIZE)?.let { entries[nameLower] = it }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }

    private suspend fun resolveCoverFallbackImage(
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
        val fallback = EpubCoverSelector.select(coverCandidates)
        if (fallback != null) {
            val bytes = readZipEntryBytes(context, uri, fallback)
            if (bytes != null && bytes.isNotEmpty()) return bytes
        }

        return null
    }

    private fun comparePathsNaturally(
        left: String,
        right: String,
    ): Int = EpubChapterOrdering.comparePathsNaturally(left, right)

    private suspend fun readZipEntryBytes(
        context: Context,
        uri: Uri,
        targetLower: String,
    ): ByteArray? {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            openZip(inputStream).use { zip ->
                return findZipEntryBytes(zip, targetLower)
            }
        }
        return null
    }

    private fun findZipEntryBytes(
        zip: ZipInputStream,
        targetLower: String,
    ): ByteArray? {
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && normalizeZipEntryNameLower(entry.name) == targetLower) {
                return readEntryWithLimit(zip, MAX_COVER_IMAGE_ENTRY_SIZE)
            }
            zip.closeEntry()
            entry = zip.nextEntry
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

    private fun buildImageFileName(epubPathLower: String): String {
        val extRaw = epubPathLower.substringAfterLast('.', missingDelimiterValue = "")
        val ext = extRaw.take(MAX_SAFE_EXTENSION_LENGTH).filter { it.isLetterOrDigit() }
        val base = UUID.nameUUIDFromBytes(epubPathLower.toByteArray(Charsets.UTF_8)).toString()
        return if (ext.isNotEmpty()) "img_$base.$ext" else "img_$base"
    }

    private suspend fun openZip(input: InputStream): EpubZipInputStream {
        val parsingContext = currentCoroutineContext()
        return EpubZipInputStream(input, checkActive = { parsingContext.ensureActive() })
    }

    /** Reject immediately instead of inflating the rest of an oversized entry. */
    private fun readEntryWithLimit(
        zip: ZipInputStream,
        maxSize: Int,
    ): ByteArray? {
        val buffer = ByteArray(BUFFER_SIZE)
        val output = java.io.ByteArrayOutputStream()
        var totalRead = 0L
        return try {
            var bytesRead: Int
            while (zip.read(buffer).also { bytesRead = it } != -1) {
                totalRead += bytesRead
                require(totalRead <= maxSize) { "EPUB entry exceeds the import limit" }
                output.write(buffer, 0, bytesRead)
            }
            output.toByteArray()
        } catch (error: IOException) {
            logWarn("Failed to read EPUB entry", error)
            null
        }
    }

    private fun logInfo(message: String) {
        EpubLogger.info(TAG, message)
    }

    private fun logWarn(
        message: String,
        error: Throwable? = null,
    ) {
        EpubLogger.warn(TAG, message, error)
    }

    private fun decodeTextEntry(bytes: ByteArray): String = EpubTextDecoder.decodeTextEntry(bytes)
}

private const val MAX_SAFE_EXTENSION_LENGTH = 10
