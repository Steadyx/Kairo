package com.kairo.reader.data.books

import android.content.Context
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.countWords
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

internal class TextFileBookParser(dispatcherProvider: DispatcherProvider) :
    BinaryBookParser(
        dispatcherProvider = dispatcherProvider,
        supportedExtensions = BookImportFormats.textFileExtensions,
        maxFileSizeBytes = MAX_FILE_SIZE_BYTES,
    ) {
    override fun parseSource(
        context: Context,
        request: BinaryBookParseRequest,
    ): Book = TextFileParserEngine.parse(request)

    private companion object {
        private const val MAX_FILE_SIZE_BYTES = 16L * 1024L * 1024L
    }
}

internal object TextFileParserEngine {
    fun parse(request: BinaryBookParseRequest): Book =
        when (request.sourceExtension.lowercase(Locale.ROOT)) {
            "html", "htm" -> parseHtml(request)
            "txt", "md", "markdown" -> parseLooseText(request)
            else -> throw IllegalArgumentException("Unsupported text file format")
        }

    private fun parseLooseText(request: BinaryBookParseRequest): Book {
        val content = BookTextDecoder.decode(request.bytes)
        val parsed = TextImportParser.parse(TextImportRequest(content = content))
        return parsed.toBook(request.bookId).withFallbackTitle(request.sourceDisplayName)
    }

    private fun parseHtml(request: BinaryBookParseRequest): Book {
        val source = BookTextDecoder.decode(request.bytes)
        val dirty = Jsoup.parse(source)
        val title =
            dirty.title().normalizeWhitespace().takeIf(String::isNotBlank)
                ?: dirty.selectFirst("h1")?.text()?.normalizeWhitespace()?.takeIf(String::isNotBlank)
                ?: request.sourceDisplayName.toFilenameTitle()
        val author =
            dirty.selectFirst("meta[name=author]")
                ?.attr("content")
                ?.normalizeWhitespace()
                ?.takeIf(String::isNotBlank)
        val languageTag =
            dirty.selectFirst("html")
                ?.attr("lang")
                ?.trim()
                ?.takeIf(String::isNotBlank)

        dirty.select(REMOVABLE_ELEMENTS).remove()
        val clean = Cleaner(HTML_SAFELIST).clean(dirty)
        val body = requireNotNull(clean.body()) { "HTML file has no readable body" }
        val plainText = body.clone().toReadablePlainText()
        require(plainText.isNotBlank()) { "No readable text found in HTML file" }
        val safeHtml = body.html().trim()

        return Book(
            id = request.bookId,
            title = title,
            authors = listOfNotNull(author),
            languageTag = languageTag,
            chapters =
            listOf(
                Chapter(
                    index = 0,
                    title = title,
                    htmlContent = safeHtml,
                    plainText = plainText,
                    wordCount = countWords(plainText),
                ),
            ),
        )
    }

    private fun Element.toReadablePlainText(): String {
        select("br").append("\n")
        select(BLOCK_ELEMENTS).forEach { element ->
            element.prepend("\n")
            element.append("\n")
        }
        return wholeText()
            .replace(NON_BREAKING_SPACE, ' ')
            .replace(HORIZONTAL_WHITESPACE, " ")
            .replace(EXCESS_NEWLINES, "\n\n")
            .trim()
    }

    private fun Book.withFallbackTitle(sourceDisplayName: String): Book =
        if (title == DEFAULT_IMPORTED_TEXT_TITLE) {
            copy(title = sourceDisplayName.toFilenameTitle())
        } else {
            this
        }

    private fun String.toFilenameTitle(): String =
        substringBeforeLast('.', this)
            .replace('_', ' ')
            .replace('-', ' ')
            .normalizeWhitespace()
            .ifBlank { DEFAULT_IMPORTED_TEXT_TITLE }

    private fun String.normalizeWhitespace(): String =
        replace(Regex("\\s+"), " ").trim()

    private const val DEFAULT_IMPORTED_TEXT_TITLE = "Imported text"
    private const val NON_BREAKING_SPACE = '\u00A0'
    private const val REMOVABLE_ELEMENTS =
        "script, style, noscript, iframe, object, embed, form, input, button, canvas, svg"
    private const val BLOCK_ELEMENTS =
        "address, article, aside, blockquote, dd, div, dl, dt, figcaption, figure, footer, " +
            "h1, h2, h3, h4, h5, h6, header, hr, li, main, nav, ol, p, pre, section, table, " +
            "tbody, td, tfoot, th, thead, tr, ul"
    private val HORIZONTAL_WHITESPACE = Regex("[\\t\\x0B\\f ]+")
    private val EXCESS_NEWLINES = Regex("\\n\\s*\\n(?:\\s*\\n)+")
    private val HTML_SAFELIST =
        Safelist.relaxed()
            .removeTags("img")
            .removeAttributes(":all", "style", "class", "id")
}
