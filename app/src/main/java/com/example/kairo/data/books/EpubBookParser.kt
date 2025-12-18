package com.example.kairo.data.books

import android.content.Context
import android.net.Uri
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

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
class EpubBookParser : BookParser {

    companion object {
        // Max size per text entry (5 MB) to prevent OOM on large embedded files
        private const val MAX_ENTRY_SIZE = 5 * 1024 * 1024
        // Max size per image entry (2 MB)
        private const val MAX_IMAGE_ENTRY_SIZE = 2 * 1024 * 1024
        // Max size for cover image entry (6 MB)
        private const val MAX_COVER_IMAGE_ENTRY_SIZE = 6 * 1024 * 1024
        // Max total size for extracted images (25 MB)
        private const val MAX_TOTAL_IMAGE_SIZE = 25 * 1024 * 1024
        // Buffer size for reading entries
        private const val BUFFER_SIZE = 8192
    }

    override suspend fun parse(context: Context, uri: Uri): Book = withContext(Dispatchers.IO) {
        val bookId = BookId(UUID.randomUUID().toString())
        val zipTextEntries = mutableMapOf<String, ByteArray>() // key = lowercased path

        // Pass 1: read only text/XML resources (OPF, XHTML, container.xml) so we can discover
        // the cover and referenced images without keeping all binary assets in memory.
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val nameLower = entry.name.lowercase()
                        val isTextFile = nameLower.endsWith(".xml") ||
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
        } ?: throw IllegalArgumentException("Unable to read EPUB file")

        if (zipTextEntries.isEmpty()) {
            throw IllegalArgumentException("EPUB file appears to be empty or corrupted")
        }

        // Parse container.xml to find the OPF file location
        val containerXml = zipTextEntries["meta-inf/container.xml"]

        val opfPath = if (containerXml != null) {
            parseContainerXml(String(containerXml))
        } else {
            // Fallback: search for OPF file directly
            zipTextEntries.keys.find { it.endsWith(".opf", ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid EPUB: cannot find OPF file")
        }

        val opfContent = zipTextEntries[opfPath.lowercase()]
            ?: throw IllegalArgumentException("Invalid EPUB: missing OPF file at $opfPath")

        // Get the base directory of the OPF file for resolving relative paths
        val opfDir = opfPath.substringBeforeLast('/', "")

        // Parse the OPF file
        val opfData = parseOpfFile(String(opfContent))

        val coverPathLower = opfData.coverHref?.let { resolvePath(opfDir, it).lowercase() }

        // Determine which image assets we need (cover + any chapter <img> references).
        val neededImagePathsLower = mutableSetOf<String>()
        coverPathLower?.let { neededImagePathsLower.add(it) }

        val spineChapterPathsLower = opfData.spineItems.mapNotNull { spineItem ->
            val href = opfData.manifest[spineItem.idref] ?: return@mapNotNull null
            resolvePath(opfDir, href).lowercase()
        }

        spineChapterPathsLower.forEach { chapterPathLower ->
            val chapterBytes = zipTextEntries[chapterPathLower] ?: return@forEach
            val html = String(chapterBytes, Charsets.UTF_8)
            val chapterDir = chapterPathLower.substringBeforeLast('/', "")
            extractImageSrcs(html).forEach { rawSrc ->
                val src = sanitizeSrc(rawSrc)
                if (src.isBlank()) return@forEach
                if (src.startsWith("data:", ignoreCase = true)) return@forEach
                if (src.startsWith("http://", ignoreCase = true) || src.startsWith("https://", ignoreCase = true)) return@forEach
                neededImagePathsLower.add(resolvePath(chapterDir, src).lowercase())
            }
        }

        // Pass 2: extract the needed image bytes from the ZIP and persist them as files.
        // This avoids storing large base64 blobs in the DB (which can crash CursorWindow).
        val imageRelativePathByEpubPathLower = mutableMapOf<String, String>()
        var totalImageBytes = 0L
        var coverImage: ByteArray? = null

        val imageDir = File(context.filesDir, "kairo_epub_assets/${bookId.value}/images")
        val canWriteImages = runCatching { imageDir.mkdirs() || imageDir.exists() }.getOrDefault(false)

        if (neededImagePathsLower.isNotEmpty()) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val nameLower = entry.name.lowercase()
                            if (neededImagePathsLower.contains(nameLower)) {
                                val maxEntrySize =
                                    if (nameLower == coverPathLower) MAX_COVER_IMAGE_ENTRY_SIZE else MAX_IMAGE_ENTRY_SIZE
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
                                        val wrote = runCatching {
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

        // Parse chapters from spine
        val chapters = opfData.spineItems.mapIndexedNotNull { index, spineItem ->
            val href = opfData.manifest[spineItem.idref] ?: return@mapIndexedNotNull null
            val chapterPath = resolvePath(opfDir, href)
            val chapterContent = zipTextEntries[chapterPath.lowercase()] ?: return@mapIndexedNotNull null

            val originalHtml = String(chapterContent, Charsets.UTF_8)
            val chapterDir = chapterPath.substringBeforeLast('/', "")
            val imagePaths = buildChapterImagePaths(
                html = originalHtml,
                baseDir = chapterDir,
                imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower
            )
            val resolvedHtml = rewriteHtmlImageSrcs(
                html = originalHtml,
                baseDir = chapterDir,
                imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower
            )
            val plainText = extractPlainText(resolvedHtml)
            val title = extractChapterTitle(originalHtml) ?: spineItem.idref

            // Skip empty chapters
            if (plainText.isBlank()) return@mapIndexedNotNull null

            Chapter(
                index = index,
                title = title,
                htmlContent = resolvedHtml,
                plainText = plainText,
                imagePaths = imagePaths
            )
        }.mapIndexed { newIndex, chapter ->
            // Re-index after filtering out empty chapters
            chapter.copy(index = newIndex)
        }

        // Fallback if no chapters found
        val finalChapters = chapters.ifEmpty {
            listOf(Chapter(
                index = 0,
                title = "Content",
                htmlContent = "<p>No readable content found in this EPUB.</p>",
                plainText = "No readable content found in this EPUB.",
                imagePaths = emptyList()
            ))
        }

        Book(
            id = bookId,
            title = opfData.title ?: uri.lastPathSegment?.substringBeforeLast('.') ?: "Unknown Book",
            authors = opfData.authors,
            coverImage = coverImage,
            chapters = finalChapters
        )
    }

    override fun supports(extension: String): Boolean = extension == "epub"

    /**
     * Parses container.xml to find the path to the OPF file.
     */
    private fun parseContainerXml(xml: String): String {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))

            val rootfiles = doc.getElementsByTagName("rootfile")
            if (rootfiles.length > 0) {
                rootfiles.item(0).attributes.getNamedItem("full-path")?.nodeValue
                    ?: "OEBPS/content.opf"
            } else {
                "OEBPS/content.opf"
            }
        } catch (e: Exception) {
            // Fallback to common locations
            "OEBPS/content.opf"
        }
    }

    /**
     * Data class to hold parsed OPF information.
     */
    private data class OpfData(
        val title: String?,
        val authors: List<String>,
        val coverHref: String?,
        val manifest: Map<String, String>,  // id -> href
        val spineItems: List<SpineItem>
    )

    private data class SpineItem(val idref: String)

    /**
     * Parses the OPF file to extract metadata, manifest, and spine.
     */
    private fun parseOpfFile(xml: String): OpfData {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))

            // Parse metadata
            val titleNodes = doc.getElementsByTagName("dc:title")
            val title = if (titleNodes.length > 0) titleNodes.item(0).textContent else null

            val creatorNodes = doc.getElementsByTagName("dc:creator")
            val authors = (0 until creatorNodes.length).mapNotNull { i ->
                creatorNodes.item(i).textContent?.takeIf { it.isNotBlank() }
            }

            // Find cover image reference
            var coverHref: String? = null

            // Method 1: Look for meta with name="cover"
            val metaNodes = doc.getElementsByTagName("meta")
            var coverId: String? = null
            for (i in 0 until metaNodes.length) {
                val meta = metaNodes.item(i)
                val name = meta.attributes.getNamedItem("name")?.nodeValue
                if (name == "cover") {
                    coverId = meta.attributes.getNamedItem("content")?.nodeValue
                    break
                }
            }

            // Parse manifest
            val manifest = mutableMapOf<String, String>()
            val manifestNodes = doc.getElementsByTagName("item")
            for (i in 0 until manifestNodes.length) {
                val item = manifestNodes.item(i)
                val id = item.attributes.getNamedItem("id")?.nodeValue ?: continue
                val href = item.attributes.getNamedItem("href")?.nodeValue ?: continue
                val mediaType = item.attributes.getNamedItem("media-type")?.nodeValue

                manifest[id] = href
                val isImage = mediaType?.startsWith("image/") == true ||
                    href.endsWith(".jpg", ignoreCase = true) ||
                    href.endsWith(".jpeg", ignoreCase = true) ||
                    href.endsWith(".png", ignoreCase = true) ||
                    href.endsWith(".gif", ignoreCase = true) ||
                    href.endsWith(".webp", ignoreCase = true) ||
                    href.endsWith(".svg", ignoreCase = true)

                // Check if this is the cover image
                if ((id == coverId && isImage) || (coverHref == null && isImage &&
                        (id.contains("cover", ignoreCase = true) || href.contains("cover", ignoreCase = true)))) {
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
            val spineNodes = doc.getElementsByTagName("itemref")
            for (i in 0 until spineNodes.length) {
                val itemref = spineNodes.item(i)
                val idref = itemref.attributes.getNamedItem("idref")?.nodeValue ?: continue
                // Skip non-linear items (like cover pages that are just images)
                val linear = itemref.attributes.getNamedItem("linear")?.nodeValue
                if (linear != "no") {
                    spineItems.add(SpineItem(idref))
                }
            }

            OpfData(title, authors, coverHref, manifest, spineItems)
        } catch (e: Exception) {
            // Return empty data on parse failure
            OpfData(null, emptyList(), null, emptyMap(), emptyList())
        }
    }

    /**
     * Resolves a relative path against a base directory.
     */
    private fun resolvePath(baseDir: String, relativePath: String): String {
        if (relativePath.startsWith("/")) return relativePath.substring(1)
        if (baseDir.isEmpty()) return relativePath

        // Handle ../ in paths
        val baseParts = baseDir.split("/").toMutableList()
        val relParts = relativePath.split("/")

        for (part in relParts) {
            when (part) {
                ".." -> if (baseParts.isNotEmpty()) baseParts.removeAt(baseParts.lastIndex)
                "." -> { /* ignore */ }
                else -> baseParts.add(part)
            }
        }

        return baseParts.joinToString("/")
    }

    private fun extractImageSrcs(html: String): List<String> {
        val regex = Regex("<img[^>]+?src\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"][^>]*>", RegexOption.IGNORE_CASE)
        return regex.findAll(html).map { it.groupValues[1] }.toList()
    }

    private fun sanitizeSrc(src: String): String {
        val trimmed = src.trim()
        if (trimmed.isBlank()) return ""
        return trimmed
            .substringBefore('#')
            .substringBefore('?')
            .trim()
    }

    private fun buildChapterImagePaths(
        html: String,
        baseDir: String,
        imageRelativePathByEpubPathLower: Map<String, String>
    ): List<String> {
        val unique = LinkedHashSet<String>(8)
        val chapterSrcs = extractImageSrcs(html)
        for (rawSrc in chapterSrcs) {
            if (unique.size >= 6) break
            val src = sanitizeSrc(rawSrc)
            if (src.isBlank()) continue
            if (src.startsWith("data:", ignoreCase = true)) continue
            if (src.startsWith("http://", ignoreCase = true) || src.startsWith("https://", ignoreCase = true)) continue

            val resolvedLower = resolvePath(baseDir, src).lowercase()
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
        imageRelativePathByEpubPathLower: Map<String, String>
    ): String {
        val imgRegex = Regex(
            "(<img[^>]+?src\\s*=\\s*['\\\"])([^'\\\"]+)(['\\\"][^>]*>)",
            RegexOption.IGNORE_CASE
        )
        return imgRegex.replace(html) { match ->
            val rawSrc = match.groupValues[2]
            val src = sanitizeSrc(rawSrc)
            if (src.isBlank()) return@replace match.value
            if (src.startsWith("data:", ignoreCase = true)) return@replace match.value
            if (src.startsWith("http://", ignoreCase = true) || src.startsWith("https://", ignoreCase = true)) {
                return@replace match.value
            }
            if (src.startsWith("kairo_epub_assets/")) return@replace match.value

            val resolvedLower = resolvePath(baseDir, src).lowercase()
            val relativePath = imageRelativePathByEpubPathLower[resolvedLower] ?: return@replace match.value
            "${match.groupValues[1]}$relativePath${match.groupValues[3]}"
        }
    }

    /**
     * Extracts plain text from HTML/XHTML content.
     */
    private fun extractPlainText(html: String): String {
        return html
            // Remove scripts and styles
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            // Convert block elements to newlines
            .replace(Regex("<(p|div|br|h[1-6]|li|tr)[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|h[1-6]|li|tr)>", RegexOption.IGNORE_CASE), "\n")
            // Remove all remaining tags
            .replace(Regex("<[^>]+>"), "")
            // Decode common HTML entities
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: ""
            }
            // Clean up whitespace
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n\\s*\\n+"), "\n\n")
            .trim()
    }

    /**
     * Extracts chapter title from HTML content.
     */
    private fun extractChapterTitle(html: String): String? {
        // Try to find title in <title> tag
        val titleMatch = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE).find(html)
        if (titleMatch != null) {
            val title = titleMatch.groupValues[1].trim()
            if (title.isNotBlank() && !title.equals("untitled", ignoreCase = true)) {
                return title
            }
        }

        // Try to find first heading
        val headingMatch = Regex("<h[1-3][^>]*>([^<]+)</h[1-3]>", RegexOption.IGNORE_CASE).find(html)
        if (headingMatch != null) {
            val heading = headingMatch.groupValues[1].trim()
            if (heading.isNotBlank()) {
                return heading.take(100)  // Limit title length
            }
        }

        return null
    }

    /**
     * Reads a ZIP entry with a size limit using buffered reading.
     * Returns null if the entry exceeds the size limit.
     * This prevents OOM by not loading huge entries all at once.
     */
    private fun readEntryWithLimit(zip: ZipInputStream, maxSize: Int): ByteArray? {
        val buffer = ByteArray(BUFFER_SIZE)
        val output = java.io.ByteArrayOutputStream()
        var totalRead = 0

        try {
            var bytesRead: Int
            while (zip.read(buffer).also { bytesRead = it } != -1) {
                totalRead += bytesRead
                if (totalRead > maxSize) {
                    // Entry too large, skip it
                    return null
                }
                output.write(buffer, 0, bytesRead)
            }
            return output.toByteArray()
        } catch (e: Exception) {
            // Handle read errors gracefully
            return null
        }
    }
}
