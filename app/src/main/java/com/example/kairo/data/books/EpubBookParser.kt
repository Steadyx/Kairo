package com.example.kairo.data.books

import android.content.Context
import android.net.Uri
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
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
        // Max size per entry (5 MB) to prevent OOM on large embedded files
        private const val MAX_ENTRY_SIZE = 5 * 1024 * 1024
        // Max total size for all text entries (50 MB) - images are skipped except cover
        private const val MAX_TOTAL_SIZE = 50 * 1024 * 1024
        // Buffer size for reading entries
        private const val BUFFER_SIZE = 8192
        // Max cover image size (1 MB)
        private const val MAX_COVER_SIZE = 1 * 1024 * 1024
    }

    override suspend fun parse(context: Context, uri: Uri): Book = withContext(Dispatchers.IO) {
        val zipEntries = mutableMapOf<String, ByteArray>()
        var totalSize = 0L

        // Extract files from the EPUB ZIP with size limits
        // We only keep text/XML files and a single cover image to save memory
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.lowercase()
                        val isTextFile = name.endsWith(".xml") ||
                                        name.endsWith(".xhtml") ||
                                        name.endsWith(".html") ||
                                        name.endsWith(".htm") ||
                                        name.endsWith(".opf") ||
                                        name.endsWith(".ncx")
                        val isCoverImage = name.contains("cover") &&
                                          (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                                           name.endsWith(".png") || name.endsWith(".gif"))

                        // Skip non-essential files (CSS, fonts, non-cover images)
                        if (!isTextFile && !isCoverImage) {
                            zip.closeEntry()
                            entry = zip.nextEntry
                            continue
                        }

                        // Read entry with size limit using buffered approach
                        val maxSize = if (isCoverImage) MAX_COVER_SIZE else MAX_ENTRY_SIZE
                        val bytes = readEntryWithLimit(zip, maxSize)

                        if (bytes != null) {
                            totalSize += bytes.size

                            // Stop if we've exceeded total size limit
                            if (totalSize > MAX_TOTAL_SIZE) {
                                break
                            }

                            zipEntries[entry.name] = bytes
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalArgumentException("Unable to read EPUB file")

        // Debug: check what files we found
        if (zipEntries.isEmpty()) {
            throw IllegalArgumentException("EPUB file appears to be empty or corrupted")
        }

        // Parse container.xml to find the OPF file location
        val containerXml = zipEntries["META-INF/container.xml"]

        val opfPath = if (containerXml != null) {
            parseContainerXml(String(containerXml))
        } else {
            // Fallback: search for OPF file directly
            zipEntries.keys.find { it.endsWith(".opf", ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid EPUB: cannot find OPF file")
        }

        val opfContent = zipEntries[opfPath]
            ?: throw IllegalArgumentException("Invalid EPUB: missing OPF file at $opfPath")

        // Get the base directory of the OPF file for resolving relative paths
        val opfDir = opfPath.substringBeforeLast('/', "")

        // Parse the OPF file
        val opfData = parseOpfFile(String(opfContent))

        // Extract cover image if available
        val coverImage = opfData.coverHref?.let { coverHref ->
            val coverPath = resolvePath(opfDir, coverHref)
            zipEntries[coverPath]
        }

        // Parse chapters from spine
        val chapters = opfData.spineItems.mapIndexedNotNull { index, spineItem ->
            val href = opfData.manifest[spineItem.idref] ?: return@mapIndexedNotNull null
            val chapterPath = resolvePath(opfDir, href)
            val chapterContent = zipEntries[chapterPath] ?: return@mapIndexedNotNull null

            val htmlContent = String(chapterContent, Charsets.UTF_8)
            val plainText = extractPlainText(htmlContent)
            val title = extractChapterTitle(htmlContent) ?: spineItem.idref

            // Skip empty chapters
            if (plainText.isBlank()) return@mapIndexedNotNull null

            Chapter(
                index = index,
                title = title,
                htmlContent = htmlContent,
                plainText = plainText
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
                plainText = "No readable content found in this EPUB."
            ))
        }

        Book(
            id = BookId(UUID.randomUUID().toString()),
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

                // Check if this is the cover image
                if (id == coverId || (coverHref == null && mediaType?.startsWith("image/") == true &&
                        (id.contains("cover", ignoreCase = true) || href.contains("cover", ignoreCase = true)))) {
                    coverHref = href
                }

                // Method 2: Look for properties="cover-image" (EPUB 3)
                val properties = item.attributes.getNamedItem("properties")?.nodeValue
                if (properties?.contains("cover-image") == true) {
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
