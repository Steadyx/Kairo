package com.kairo.reader.data.books

import android.content.Context
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.countWords
import java.util.Locale
import kotlin.io.encoding.Base64
import org.w3c.dom.Element
import org.w3c.dom.Node

internal class Fb2BookParser(dispatcherProvider: DispatcherProvider) :
    BinaryBookParser(
        dispatcherProvider = dispatcherProvider,
        supportedExtensions = BookImportFormats.fb2.extensions,
        maxFileSizeBytes = MAX_FILE_SIZE_BYTES,
    ) {
    override fun parseSource(
        context: Context,
        request: BinaryBookParseRequest,
    ): Book {
        val assetStore = BookAssetStore(context, FB2_ASSET_ROOT, request.bookId)
        return Fb2ParserEngine.parse(request, assetStore::writeImage)
    }

    private companion object {
        private const val FB2_ASSET_ROOT = "kairo_fb2_assets"
        private const val MAX_FILE_SIZE_BYTES = 64L * 1024L * 1024L
    }
}

internal object Fb2ParserEngine {
    fun parse(
        request: BinaryBookParseRequest,
        writeImage: (sourceName: String, bytes: ByteArray) -> String? = { _, _ -> null },
    ): Book {
        val xmlBytes = resolveFb2Xml(request)
        val document = SafeXml.parse(xmlBytes)
        val root = document.documentElement
        require(root.localNameValue().equals("FictionBook", ignoreCase = true)) {
            "File is not a valid FictionBook document"
        }

        val description = root.firstDirectChildNamed("description")
        val titleInfo = description?.firstDirectChildNamed("title-info")
        val title =
            titleInfo
                ?.firstDirectChildNamed("book-title")
                ?.normalizedText()
                ?: request.sourceDisplayName.toFb2FilenameTitle()
        val authors = titleInfo?.directChildrenNamed("author").orEmpty().mapNotNull(::readAuthor)
        val languageTag = titleInfo?.firstDirectChildNamed("lang")?.normalizedText()
        val binaryImages = readBinaryImages(root)
        val imagePathsById = mutableMapOf<String, String?>()

        fun resolveImage(reference: String): String? {
            val id = reference.removePrefix("#").trim()
            if (id.isBlank()) return null
            return imagePathsById.getOrPut(id) {
                val image = binaryImages[id] ?: return@getOrPut null
                val bytes = image.decode() ?: return@getOrPut null
                writeImage(image.fileName(id), bytes)
            }
        }

        val coverImage =
            titleInfo
                ?.firstDirectChildNamed("coverpage")
                ?.descendantsNamed("image")
                ?.firstOrNull()
                ?.attributeByLocalName("href")
                ?.removePrefix("#")
                ?.let(binaryImages::get)
                ?.decode()
                ?.takeIf { bytes -> bytes.size <= MAX_COVER_BYTES }

        val chapters = buildChapters(root, ::resolveImage)
        require(chapters.isNotEmpty()) { "No readable chapters found in FictionBook file" }
        return Book(
            id = request.bookId,
            title = title,
            authors = authors,
            languageTag = languageTag,
            chapters = chapters,
            coverImage = coverImage,
        )
    }

    private fun resolveFb2Xml(request: BinaryBookParseRequest): ByteArray {
        val isZip = request.sourceExtension == "fb2.zip" || request.bytes.hasZipHeader()
        if (!isZip) return request.bytes

        val entries =
            BoundedZipReader.read(
                archiveBytes = request.bytes,
                policy =
                ZipReadPolicy(
                    maxEntries = MAX_ZIP_ENTRIES,
                    maxEntryBytes = MAX_FB2_XML_BYTES,
                    maxTotalUncompressedBytes = MAX_ZIP_TOTAL_BYTES,
                    includeEntry = { name -> name.endsWith(".fb2") },
                ),
            )
        require(entries.isNotEmpty()) { "FictionBook ZIP does not contain an .fb2 document" }
        require(entries.size == 1) { "FictionBook ZIP must contain exactly one .fb2 document" }
        return entries.single().bytes
    }

    private fun readAuthor(author: Element): String? {
        val parts =
            listOf("first-name", "middle-name", "last-name", "nickname")
                .mapNotNull { part -> author.firstDirectChildNamed(part)?.normalizedText() }
        return parts.distinct().joinToString(" ").takeIf(String::isNotBlank)
    }

    private fun readBinaryImages(root: Element): Map<String, Fb2BinaryImage> =
        root.directChildrenNamed("binary")
            .mapNotNull { binary ->
                val id = binary.attributeByLocalName("id")?.trim()?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val contentType = binary.attributeByLocalName("content-type")?.trim().orEmpty()
                val encoded = binary.textContent?.filterNot(Char::isWhitespace).orEmpty()
                if (encoded.length > MAX_BASE64_CHARS) return@mapNotNull null
                id to Fb2BinaryImage(contentType = contentType, encoded = encoded)
            }.toMap()

    private fun buildChapters(
        root: Element,
        resolveImage: (String) -> String?,
    ): List<Chapter> {
        val renderer = Fb2MarkupRenderer(resolveImage)
        val drafts = mutableListOf<Fb2ChapterDraft>()
        root.directChildrenNamed("body").forEach { body ->
            val bodyTitle = body.firstDirectChildNamed("title")?.toFb2Title()
            val sections = body.directChildrenNamed("section")
            if (sections.isEmpty()) {
                drafts += renderer.renderChapter(body, bodyTitle)
            } else {
                sections.forEachIndexed { index, section ->
                    val fallbackTitle =
                        bodyTitle?.let { title ->
                            if (sections.size == 1) title else "$title ${index + 1}"
                        }
                    drafts += renderer.renderChapter(section, fallbackTitle)
                }
            }
        }

        return drafts
            .filter { draft -> draft.plainText.isNotBlank() }
            .mapIndexed { index, draft ->
                Chapter(
                    index = index,
                    title = draft.title,
                    htmlContent = draft.html,
                    plainText = draft.plainText,
                    imagePaths = draft.imagePaths,
                    wordCount = countWords(draft.plainText),
                )
            }
    }

    private fun Element.toFb2Title(): String? =
        directChildrenNamed("p")
            .mapNotNull(Node::normalizedText)
            .joinToString(" ")
            .takeIf(String::isNotBlank)

    private fun String.toFb2FilenameTitle(): String =
        removeSuffix(".zip")
            .substringBeforeLast('.', this)
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { DEFAULT_TITLE }

    private fun ByteArray.hasZipHeader(): Boolean =
        size >= ZIP_HEADER.size && ZIP_HEADER.indices.all { index -> this[index] == ZIP_HEADER[index] }

    private data class Fb2BinaryImage(val contentType: String, val encoded: String,) {
        fun decode(): ByteArray? =
            runCatching {
                Base64.Default
                    .withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
                    .decode(encoded)
            }
                .getOrNull()
                ?.takeIf { bytes -> bytes.size <= MAX_DECODED_IMAGE_BYTES }

        fun fileName(id: String): String {
            val existingExtension = id.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (existingExtension in IMAGE_EXTENSIONS) return id
            val extension = CONTENT_TYPE_EXTENSIONS[contentType.lowercase(Locale.ROOT)] ?: "bin"
            return "$id.$extension"
        }
    }

    private const val DEFAULT_TITLE = "FictionBook import"
    private const val MAX_ZIP_ENTRIES = 128
    private const val MAX_FB2_XML_BYTES = 48L * 1024L * 1024L
    private const val MAX_ZIP_TOTAL_BYTES = 64L * 1024L * 1024L
    private const val MAX_BASE64_CHARS = 24 * 1024 * 1024
    private const val MAX_DECODED_IMAGE_BYTES = 16 * 1024 * 1024
    private const val MAX_COVER_BYTES = 8 * 1024 * 1024
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "svg")
    private val CONTENT_TYPE_EXTENSIONS =
        mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/gif" to "gif",
            "image/webp" to "webp",
            "image/svg+xml" to "svg",
        )

    // ZIP local-header bytes are a fixed protocol signature.
    @Suppress("MagicNumber")
    private val ZIP_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
}

private data class Fb2ChapterDraft(val title: String?, val html: String, val plainText: String, val imagePaths: List<String>,)

private class Fb2MarkupRenderer(private val resolveImage: (String) -> String?,) {
    private val contentRewriter = EpubContentRewriter()

    fun renderChapter(
        element: Element,
        fallbackTitle: String?,
    ): Fb2ChapterDraft {
        val imagePaths = mutableListOf<String>()
        val html = renderElement(element, imagePaths, isRoot = true).trim()
        val plainText = contentRewriter.extractPlainText(html)
        val title = element.firstDirectChildNamed("title")?.toFb2Title() ?: fallbackTitle
        return Fb2ChapterDraft(
            title = title,
            html = html,
            plainText = plainText,
            imagePaths = imagePaths.distinct(),
        )
    }

    private fun renderElement(
        element: Element,
        imagePaths: MutableList<String>,
        isRoot: Boolean = false,
    ): String {
        val tagName = element.localNameValue().lowercase(Locale.ROOT)
        if (tagName == "image") return renderImage(element, imagePaths)
        val children = renderChildren(element, imagePaths)
        if (isRoot) return children
        return when (tagName) {
            "title" -> "<h2>$children</h2>"
            "subtitle" -> "<h3>$children</h3>"
            "p", "v", "text-author", "date" -> "<p>$children</p>"
            "empty-line" -> "<p><br></p>"
            "emphasis" -> "<em>$children</em>"
            "strong" -> "<strong>$children</strong>"
            "strikethrough" -> "<s>$children</s>"
            "code" -> "<code>$children</code>"
            "sup" -> "<sup>$children</sup>"
            "sub" -> "<sub>$children</sub>"
            "section", "epigraph", "poem", "stanza", "cite", "annotation" -> "<section>$children</section>"
            "a", "style" -> children
            else -> children
        }
    }

    private fun renderChildren(
        parent: Element,
        imagePaths: MutableList<String>,
    ): String {
        val html = StringBuilder()
        val nodes = parent.childNodes
        for (index in 0 until nodes.length) {
            when (val child = nodes.item(index)) {
                is Element -> html.append(renderElement(child, imagePaths))
                else -> if (child.nodeType == Node.TEXT_NODE) html.append(child.nodeValue.orEmpty().escapeBookHtml())
            }
        }
        return html.toString()
    }

    private fun renderImage(
        image: Element,
        imagePaths: MutableList<String>,
    ): String {
        val reference = image.attributeByLocalName("href") ?: return ""
        val path = resolveImage(reference) ?: return ""
        imagePaths += path
        val alt = image.attributeByLocalName("alt")?.escapeBookHtml().orEmpty()
        return "<img src=\"${path.escapeBookHtml()}\" alt=\"$alt\">"
    }

    private fun Element.toFb2Title(): String? =
        directChildrenNamed("p")
            .mapNotNull(Node::normalizedText)
            .joinToString(" ")
            .takeIf(String::isNotBlank)
}
