package com.kairo.reader.data.books

import android.content.Context
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.countWords
import java.util.Locale
import org.w3c.dom.Document
import org.w3c.dom.Element

internal class DocxBookParser(dispatcherProvider: DispatcherProvider) :
    BinaryBookParser(
        dispatcherProvider = dispatcherProvider,
        supportedExtensions = BookImportFormats.docx.extensions,
        maxFileSizeBytes = MAX_FILE_SIZE_BYTES,
    ) {
    override fun parseSource(
        context: Context,
        request: BinaryBookParseRequest,
    ): Book {
        val assetStore = BookAssetStore(context, DOCX_ASSET_ROOT, request.bookId)
        return DocxParserEngine.parse(request, assetStore::writeImage)
    }

    private companion object {
        private const val DOCX_ASSET_ROOT = "kairo_docx_assets"
        private const val MAX_FILE_SIZE_BYTES = 64L * 1024L * 1024L
    }
}

internal object DocxParserEngine {
    fun parse(
        request: BinaryBookParseRequest,
        writeImage: (sourceName: String, bytes: ByteArray) -> String? = { _, _ -> null },
    ): Book {
        val entries = readPackage(request.bytes)
        require(CONTENT_TYPES_PATH in entries) { "DOCX package is missing [Content_Types].xml" }
        val documentBytes = requireNotNull(entries[DOCUMENT_PATH]) { "DOCX package is missing word/document.xml" }
        val document = SafeXml.parse(documentBytes)
        val styles = entries[STYLES_PATH]?.let(SafeXml::parse)?.let(::readStyles).orEmpty()
        val relationships = entries[RELATIONSHIPS_PATH]?.let(SafeXml::parse)?.let(::readRelationships).orEmpty()
        val metadata = entries[CORE_PROPERTIES_PATH]?.let(SafeXml::parse)?.let(::readMetadata) ?: DocxMetadata()
        val mediaEntries = entries.filterKeys { path -> path.startsWith(MEDIA_PREFIX) }
        val imagePathByRelationship = mutableMapOf<String, String?>()

        fun resolveImage(relationshipId: String): String? =
            imagePathByRelationship.getOrPut(relationshipId) {
                val target = relationships[relationshipId] ?: return@getOrPut null
                val bytes = mediaEntries[target] ?: return@getOrPut null
                writeImage(target.substringAfterLast('/'), bytes)
            }

        val body = document.documentElement.descendantsNamed("body").firstOrNull()
            ?: throw IllegalArgumentException("DOCX document body is missing")
        val blocks = readBodyBlocks(body, styles, ::resolveImage)
        require(blocks.any { block -> block.plainText.isNotBlank() }) { "No readable text found in DOCX file" }
        require(blocks.sumOf { block -> block.plainText.length.toLong() } <= MAX_EXTRACTED_TEXT_CHARS) {
            "DOCX document contains too much extracted text"
        }

        val title =
            metadata.title
                ?: blocks.firstOrNull { block -> block.kind == DocxBlockKind.TITLE }?.plainText
                ?: request.sourceDisplayName.toDocxFilenameTitle()
        val chapters = buildChapters(blocks, title)
        return Book(
            id = request.bookId,
            title = title,
            authors = metadata.creator?.let(::splitAuthors).orEmpty(),
            languageTag = metadata.language,
            chapters = chapters,
        )
    }

    private fun readPackage(bytes: ByteArray): Map<String, ByteArray> =
        BoundedZipReader.read(
            archiveBytes = bytes,
            policy =
            ZipReadPolicy(
                maxEntries = MAX_ZIP_ENTRIES,
                maxEntryBytes = MAX_ZIP_ENTRY_BYTES,
                maxTotalUncompressedBytes = MAX_ZIP_TOTAL_BYTES,
                includeEntry = { path ->
                    path in REQUIRED_OR_OPTIONAL_PATHS || path.startsWith(MEDIA_PREFIX)
                },
            ),
        ).associate { entry -> entry.name to entry.bytes }

    private fun readStyles(document: Document): Map<String, DocxParagraphStyle> =
        document.documentElement
            .descendantsNamed("style")
            .mapNotNull { style ->
                if (!style.attributeByLocalName("type").equals("paragraph", ignoreCase = true)) {
                    return@mapNotNull null
                }
                val id = style.attributeByLocalName("styleId") ?: return@mapNotNull null
                val name = style.firstDirectChildNamed("name")?.attributeByLocalName("val").orEmpty()
                val outlineLevel =
                    style.descendantsNamed("outlineLvl")
                        .firstOrNull()
                        ?.attributeByLocalName("val")
                        ?.toIntOrNull()
                id to DocxParagraphStyle(name = name, outlineLevel = outlineLevel)
            }.toMap()

    private fun readRelationships(document: Document): Map<String, String> =
        document.documentElement
            .descendantsNamed("Relationship")
            .mapNotNull { relationship ->
                if (relationship.attributeByLocalName("TargetMode").equals("External", ignoreCase = true)) {
                    return@mapNotNull null
                }
                val id = relationship.attributeByLocalName("Id") ?: return@mapNotNull null
                val type = relationship.attributeByLocalName("Type").orEmpty()
                if (!type.endsWith("/image", ignoreCase = true)) return@mapNotNull null
                val target = relationship.attributeByLocalName("Target") ?: return@mapNotNull null
                id to normalizeWordTarget(target)
            }.toMap()

    private fun normalizeWordTarget(target: String): String {
        val segments = mutableListOf<String>()
        val source = if (target.startsWith('/')) target.trimStart('/') else "word/$target"
        source.replace('\\', '/').split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        return segments.joinToString("/").lowercase(Locale.ROOT)
    }

    private fun readMetadata(document: Document): DocxMetadata {
        val root = document.documentElement
        return DocxMetadata(
            title = root.descendantsNamed("title").firstOrNull()?.normalizedText(),
            creator = root.descendantsNamed("creator").firstOrNull()?.normalizedText(),
            language = root.descendantsNamed("language").firstOrNull()?.normalizedText(),
        )
    }

    private fun readBodyBlocks(
        body: Element,
        styles: Map<String, DocxParagraphStyle>,
        resolveImage: (relationshipId: String) -> String?,
    ): List<DocxBlock> {
        val renderer = DocxMarkupRenderer(resolveImage)
        return body.directChildElements().mapNotNull { child ->
            when (child.localNameValue().lowercase(Locale.ROOT)) {
                "p" -> renderer.renderParagraph(child, styles)
                "tbl" -> renderer.renderTable(child)
                else -> null
            }
        }
    }

    private fun buildChapters(
        blocks: List<DocxBlock>,
        bookTitle: String,
    ): List<Chapter> {
        val chapters = mutableListOf<DocxChapterBuilder>()
        var current = DocxChapterBuilder(title = null)

        blocks.forEach { block ->
            if (block.kind == DocxBlockKind.HEADING && current.hasReadableContent()) {
                chapters += current
                current = DocxChapterBuilder(title = block.plainText)
            } else if (block.kind == DocxBlockKind.HEADING) {
                current.title = block.plainText
            }
            current.add(block)
        }
        if (current.hasReadableContent()) chapters += current

        return chapters.mapIndexed { index, chapter ->
            val plainText = chapter.plainText()
            Chapter(
                index = index,
                title = chapter.title ?: if (chapters.size == 1) bookTitle else "Part ${index + 1}",
                htmlContent = chapter.html(),
                plainText = plainText,
                imagePaths = chapter.imagePaths.distinct(),
                wordCount = countWords(plainText),
            )
        }
    }

    private fun splitAuthors(value: String): List<String> =
        value.split(AUTHOR_SEPARATOR)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

    private fun String.toDocxFilenameTitle(): String =
        substringBeforeLast('.', this)
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { DEFAULT_TITLE }

    private const val DEFAULT_TITLE = "Word document"
    private const val CONTENT_TYPES_PATH = "[content_types].xml"
    private const val DOCUMENT_PATH = "word/document.xml"
    private const val STYLES_PATH = "word/styles.xml"
    private const val RELATIONSHIPS_PATH = "word/_rels/document.xml.rels"
    private const val CORE_PROPERTIES_PATH = "docprops/core.xml"
    private const val MEDIA_PREFIX = "word/media/"
    private const val MAX_ZIP_ENTRIES = 4096
    private const val MAX_ZIP_ENTRY_BYTES = 32L * 1024L * 1024L
    private const val MAX_ZIP_TOTAL_BYTES = 128L * 1024L * 1024L
    private const val MAX_EXTRACTED_TEXT_CHARS = 12L * 1024L * 1024L
    private val REQUIRED_OR_OPTIONAL_PATHS =
        setOf(CONTENT_TYPES_PATH, DOCUMENT_PATH, STYLES_PATH, RELATIONSHIPS_PATH, CORE_PROPERTIES_PATH)
    private val AUTHOR_SEPARATOR = Regex("\\s*(?:;|\\band\\b)\\s*", RegexOption.IGNORE_CASE)
}

private data class DocxMetadata(val title: String? = null, val creator: String? = null, val language: String? = null,)

private data class DocxParagraphStyle(val name: String, val outlineLevel: Int?,) {
    fun kind(): DocxBlockKind {
        val normalizedName = name.lowercase(Locale.ROOT).replace(" ", "")
        return when {
            normalizedName == "title" -> DocxBlockKind.TITLE
            normalizedName.startsWith("heading") || outlineLevel in HEADING_OUTLINE_LEVELS -> DocxBlockKind.HEADING
            else -> DocxBlockKind.CONTENT
        }
    }

    private companion object {
        private val HEADING_OUTLINE_LEVELS = 0..8
    }
}

private enum class DocxBlockKind { TITLE, HEADING, CONTENT }

private data class DocxBlock(val html: String, val plainText: String, val imagePaths: List<String>, val kind: DocxBlockKind,)

private class DocxMarkupRenderer(private val resolveImage: (relationshipId: String) -> String?,) {
    private val contentRewriter = EpubContentRewriter()

    fun renderParagraph(
        paragraph: Element,
        styles: Map<String, DocxParagraphStyle>,
    ): DocxBlock? {
        val styleId =
            paragraph.firstDirectChildNamed("pPr")
                ?.firstDirectChildNamed("pStyle")
                ?.attributeByLocalName("val")
        val style = styleId?.let(styles::get)
        val kind = style?.kind() ?: styleId.toFallbackKind()
        val imagePaths = mutableListOf<String>()
        val inlineHtml = renderInlineChildren(paragraph, imagePaths).trim()
        val tag = if (kind == DocxBlockKind.HEADING || kind == DocxBlockKind.TITLE) "h2" else "p"
        val prefix = if (paragraph.descendantsNamed("numPr").isNotEmpty()) "&#8226; " else ""
        val html = "<$tag>$prefix$inlineHtml</$tag>"
        val plainText = contentRewriter.extractPlainText(html)
        if (plainText.isBlank() && imagePaths.isEmpty()) return null
        return DocxBlock(html = html, plainText = plainText, imagePaths = imagePaths, kind = kind)
    }

    fun renderTable(table: Element): DocxBlock? {
        val imagePaths = mutableListOf<String>()
        val rows = table.directChildrenNamed("tr").map { row ->
            val cells = row.directChildrenNamed("tc").map { cell ->
                val contents = cell.directChildrenNamed("p").joinToString("<br>") { paragraph ->
                    renderInlineChildren(paragraph, imagePaths)
                }
                "<td>$contents</td>"
            }
            "<tr>${cells.joinToString("")}</tr>"
        }
        val html = "<table>${rows.joinToString("")}</table>"
        val plainText = contentRewriter.extractPlainText(html)
        if (plainText.isBlank() && imagePaths.isEmpty()) return null
        return DocxBlock(html, plainText, imagePaths, DocxBlockKind.CONTENT)
    }

    private fun renderInlineChildren(
        parent: Element,
        imagePaths: MutableList<String>,
    ): String {
        val output = StringBuilder()
        val nodes = parent.childNodes
        for (index in 0 until nodes.length) {
            val child = nodes.item(index)
            if (child !is Element) continue
            when (child.localNameValue().lowercase(Locale.ROOT)) {
                "ppr", "rpr", "bookmarkstart", "bookmarkend", "prooferr",
                "del", "movefrom", "deltext", "delinstrtext", "instrtext" -> Unit
                "t" -> output.append(child.textContent.orEmpty().escapeBookHtml())
                "tab" -> output.append(' ')
                "br", "cr" -> output.append("<br>")
                "blip" -> appendImage(child, imagePaths, output)
                "r" -> output.append(renderRun(child, imagePaths))
                else -> output.append(renderInlineChildren(child, imagePaths))
            }
        }
        return output.toString()
    }

    private fun renderRun(
        run: Element,
        imagePaths: MutableList<String>,
    ): String {
        val content = renderInlineChildren(run, imagePaths)
        val properties = run.firstDirectChildNamed("rPr")
        return when {
            properties?.firstDirectChildNamed("b") != null -> "<strong>$content</strong>"
            properties?.firstDirectChildNamed("i") != null -> "<em>$content</em>"
            else -> content
        }
    }

    private fun appendImage(
        blip: Element,
        imagePaths: MutableList<String>,
        output: StringBuilder,
    ) {
        val relationshipId = blip.attributeByLocalName("embed") ?: return
        val imagePath = resolveImage(relationshipId) ?: return
        imagePaths += imagePath
        output.append("<img src=\"")
        output.append(imagePath.escapeBookHtml())
        output.append("\">")
    }

    private fun String?.toFallbackKind(): DocxBlockKind {
        val normalized = this?.lowercase(Locale.ROOT).orEmpty()
        return when {
            normalized == "title" -> DocxBlockKind.TITLE
            normalized.startsWith("heading") -> DocxBlockKind.HEADING
            else -> DocxBlockKind.CONTENT
        }
    }
}

private class DocxChapterBuilder(var title: String?,) {
    private val blocks = mutableListOf<DocxBlock>()
    val imagePaths = mutableListOf<String>()

    fun add(block: DocxBlock) {
        blocks += block
        imagePaths += block.imagePaths
    }

    fun hasReadableContent(): Boolean = blocks.any { block -> block.plainText.isNotBlank() }

    fun html(): String = blocks.joinToString("\n", transform = DocxBlock::html)

    fun plainText(): String =
        blocks.map(DocxBlock::plainText).filter(String::isNotBlank).joinToString("\n\n")
}
