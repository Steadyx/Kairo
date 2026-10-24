package com.kairo.reader.data.books

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.countWords
import java.io.IOException
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeVisitor

class WebArticleExtractor(private val dispatcherProvider: DispatcherProvider) {
    suspend fun extract(
        normalizedUrl: String,
        bookId: BookId,
    ): Book =
        withContext(dispatcherProvider.io) {
            val document = fetchDocument(normalizedUrl)
            parseDocumentOrNull(document, normalizedUrl, bookId)?.let { return@withContext it }

            document
                .fallbackArticleUrls(normalizedUrl)
                .forEach { fallbackUrl ->
                    val fallbackDocument =
                        fetchDocumentOrNull(
                            normalizedUrl = fallbackUrl,
                            profiles = FALLBACK_REQUEST_PROFILES,
                        ) ?: return@forEach
                    parseDocumentOrNull(fallbackDocument, fallbackUrl, bookId)?.let {
                        return@withContext it
                    }
                }

            throw IllegalArgumentException("Could not extract readable article text from this link.")
        }

    internal fun parseHtml(
        html: String,
        normalizedUrl: String,
        bookId: BookId,
    ): Book =
        parseDocumentOrNull(Jsoup.parse(html, normalizedUrl), normalizedUrl, bookId)
            ?: throw IllegalArgumentException("Could not extract readable article text from this link.")

    private fun fetchDocument(
        normalizedUrl: String,
        profiles: List<RequestProfile> = REQUEST_PROFILES,
    ): Document {
        var lastError: IOException? = null
        profiles.forEach { profile ->
            try {
                return Jsoup
                    .connect(normalizedUrl)
                    .userAgent(profile.userAgent)
                    .timeout(profile.timeoutMs)
                    .maxBodySize(MAX_BODY_SIZE_BYTES)
                    .followRedirects(true)
                    .applyHeaders(profile.headers)
                    .get()
            } catch (error: IOException) {
                lastError = error
            }
        }
        lastError?.let { throw it }
        error("Could not fetch article.")
    }

    private fun fetchDocumentOrNull(
        normalizedUrl: String,
        profiles: List<RequestProfile>,
    ): Document? =
        try {
            fetchDocument(normalizedUrl, profiles)
        } catch (_: IOException) {
            null
        }

    private fun Connection.applyHeaders(headers: Map<String, String>): Connection {
        headers.forEach { (name, value) -> header(name, value) }
        return this
    }

    private fun parseDocumentOrNull(
        document: Document,
        normalizedUrl: String,
        bookId: BookId,
    ): Book? {
        val host = WebArticleUrl.displayHost(normalizedUrl)
        val title = document.articleTitle(host)
        val author = document.articleAuthor() ?: host
        val languageTag = document.selectFirst("html")?.attr("lang")?.takeIf { it.isNotBlank() }
        document.select(REMOVABLE_SELECTOR).remove()

        val plainText = document.readablePlainText().stripLeadingDuplicateTitle(title)
        if (plainText.length < MIN_ARTICLE_CHARS) return null

        val htmlContent = plainText.toSimpleHtml()
        return Book(
            id = bookId,
            title = title,
            authors = listOf(author),
            languageTag = languageTag,
            chapters =
            listOf(
                Chapter(
                    index = 0,
                    title = title,
                    htmlContent = htmlContent,
                    plainText = plainText,
                    wordCount = countWords(plainText),
                ),
            ),
        )
    }

    private fun Document.fallbackArticleUrls(normalizedUrl: String): List<String> {
        val candidates =
            listOfNotNull(
                selectFirst("link[rel=canonical]")?.hrefCandidate(),
                selectFirst("link[rel=amphtml]")?.hrefCandidate(),
                metaContent("meta[property=og:url]", "meta[name=twitter:url]"),
            )
        return candidates
            .asSequence()
            .mapNotNull { candidate -> candidate.resolveUrl(normalizedUrl) }
            .filterNot { candidate -> candidate == normalizedUrl }
            .distinct()
            .take(MAX_FALLBACK_URLS)
            .toList()
    }

    private fun Element.hrefCandidate(): String? =
        attr("abs:href").ifBlank { attr("href") }.takeIf { it.isNotBlank() }

    private fun String.resolveUrl(baseUrl: String): String? =
        runCatching {
            val resolved =
                if (contains("://")) {
                    this
                } else {
                    URI(baseUrl).resolve(this).toString()
                }
            WebArticleUrl.normalize(resolved)
        }.getOrNull()

    private fun Document.articleTitle(host: String): String {
        val title =
            metaContent(
                "meta[property=og:title]",
                "meta[name=twitter:title]",
            )
                ?: selectFirst("h1")?.text()
                ?: title()
        return title
            .normalizeText()
            .removeSiteSuffix(host)
            .ifBlank { host }
    }

    private fun Document.articleAuthor(): String? =
        metaContent(
            "meta[name=author]",
            "meta[property=article:author]",
            "meta[name=byl]",
        )
            ?.normalizeText()
            ?.takeIf { it.isNotBlank() }

    private fun Document.metaContent(vararg selectors: String): String? =
        selectors
            .asSequence()
            .mapNotNull { selector ->
                selectFirst(selector)
                    ?.attr("content")
                    ?.takeIf { it.isNotBlank() }
            }
            .firstOrNull()

    private fun Document.readablePlainText(): String {
        select(PRIORITY_CANDIDATE_SELECTOR)
            .asSequence()
            .distinct()
            .take(MAX_PRIORITY_CANDIDATES)
            .map { it.extractBlockText() }
            .firstOrNull { it.length >= MIN_ARTICLE_CHARS }
            ?.let { return it }

        val candidates =
            select(CANDIDATE_SELECTOR)
                .asSequence()
                .distinct()
                .filterNot { it.tagName().equals("body", ignoreCase = true) }
                .take(MAX_CANDIDATES)
                .toList()
                .ifEmpty { listOfNotNull(body()) }
        val best = candidates.maxByOrNull { it.readabilityScore() } ?: body()
        val extracted = best.extractBlockText()
        if (extracted.length >= MIN_ARTICLE_CHARS) return extracted

        return body()
            .extractBlockText()
            .takeIf { it.length >= MIN_ARTICLE_CHARS }
            ?: body().text().normalizeText()
    }

    private fun Element.readabilityScore(): Int {
        val paragraphs = select("p")
        val paragraphLength = paragraphs.sumOf { it.text().normalizeText().length }
        val paragraphCount = paragraphs.size
        val linkLength = select("a").sumOf { it.text().normalizeText().length }
        val headingBonus = select("h1, h2").size * HEADING_SCORE
        return paragraphLength + paragraphCount * PARAGRAPH_SCORE + headingBonus - linkLength
    }

    private fun Element.extractBlockText(): String {
        val blocks =
            nonOverlappingBlockText()
                .asSequence()
                .map { it.normalizeText() }
                .filter { it.isNotBlank() }
                .filterNot { text -> text.looksLikeBoilerplate() }
                .toList()

        return blocks.joinToString("\n\n")
    }

    private fun Element.nonOverlappingBlockText(): List<String> {
        val blocks = mutableListOf<String>()
        val text = StringBuilder()
        var blockDepth = 0
        fun flush() {
            if (text.isNotBlank()) blocks += text.toString()
            text.clear()
        }
        traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                if (node is Element && node.`is`(BLOCK_SELECTOR)) {
                    flush()
                    blockDepth += 1
                }
                if (blockDepth > 0) {
                    if (node is TextNode) text.append(node.wholeText)
                    if (node is Element && (node.tagName() == "br" || node.isBlock)) text.append(' ')
                }
            }

            override fun tail(node: Node, depth: Int) {
                if (node is Element && node.`is`(BLOCK_SELECTOR)) {
                    flush()
                    blockDepth -= 1
                } else if (blockDepth > 0 && node is Element && node.isBlock) {
                    text.append(' ')
                }
            }
        })
        return blocks
    }

    private fun String.toSimpleHtml(): String =
        split(PARAGRAPH_SPLIT)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n") { "<p>${it.escapeHtml()}</p>" }

    private fun String.stripLeadingDuplicateTitle(title: String): String {
        val parts = split(PARAGRAPH_SPLIT, limit = 2)
        val firstBlock = parts.firstOrNull()?.normalizeText().orEmpty()
        if (!firstBlock.equals(title.normalizeText(), ignoreCase = true)) return this
        return parts.getOrNull(1)?.trim().orEmpty()
    }

    private fun String.normalizeText(): String =
        replace(NBSP, ' ')
            .replace(TEXT_WHITESPACE, " ")
            .trim()

    private fun String.removeSiteSuffix(host: String): String {
        val site = host.removePrefix("www.")
        return TITLE_SEPARATORS.fold(this) { current, separator ->
            current
                .removeSuffix("$separator $site")
                .removeSuffix("$separator ${site.substringBefore('.')}")
                .trim()
        }
    }

    private fun String.looksLikeBoilerplate(): Boolean {
        val normalized = lowercase(Locale.ROOT)
        return BOILERPLATE_MARKERS.any { marker -> normalized.contains(marker) }
    }

    private fun String.escapeHtml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private companion object {
        private const val KAIRO_USER_AGENT =
            "KairoReader/1.0 (+https://kairo.reader; article text extraction)"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Kairo) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
        private const val BROWSER_REQUEST_TIMEOUT_MS = 10_000
        private const val KAIRO_REQUEST_TIMEOUT_MS = 6_000
        private const val FALLBACK_REQUEST_TIMEOUT_MS = 7_000
        private const val MAX_BODY_SIZE_BYTES = 2 * 1024 * 1024
        private const val MIN_ARTICLE_CHARS = 120
        private const val MAX_FALLBACK_URLS = 2
        private const val MAX_PRIORITY_CANDIDATES = 4
        private const val MAX_CANDIDATES = 14
        private const val PARAGRAPH_SCORE = 80
        private const val HEADING_SCORE = 120
        private const val NBSP = '\u00A0'
        private val PARAGRAPH_SPLIT = Regex("\\n{2,}")
        private val TEXT_WHITESPACE = Regex("\\s+")
        private val TITLE_SEPARATORS = listOf("|", "-", "–", "—")
        private const val REMOVABLE_SELECTOR =
            "script, style, noscript, svg, canvas, iframe, nav, aside, form, button, " +
                "header, footer, dialog, [role=banner], [role=navigation], [role=complementary]"
        private const val CANDIDATE_SELECTOR =
            "article, main, [role=main], [class*=article], [class*=post], " +
                "[class*=entry-content], [class*=story], [class*=content], body"
        private const val PRIORITY_CANDIDATE_SELECTOR =
            "article, main, [role=main], [class*=article], [class*=entry-content], [class*=story]"
        private const val BLOCK_SELECTOR = "h1, h2, h3, p, li, blockquote, pre"
        private val BOILERPLATE_MARKERS =
            listOf(
                "accept cookies",
                "cookie settings",
                "privacy policy",
                "all rights reserved",
                "subscribe to",
                "sign up for",
                "advertisement",
            )
        private val REQUEST_PROFILES =
            listOf(
                RequestProfile(
                    userAgent = BROWSER_USER_AGENT,
                    timeoutMs = BROWSER_REQUEST_TIMEOUT_MS,
                    headers =
                    mapOf(
                        "Accept" to
                            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                        "Accept-Language" to "en-GB,en;q=0.9",
                        "Cache-Control" to "no-cache",
                        "Upgrade-Insecure-Requests" to "1",
                    ),
                ),
                RequestProfile(
                    userAgent = KAIRO_USER_AGENT,
                    timeoutMs = KAIRO_REQUEST_TIMEOUT_MS,
                    headers = emptyMap(),
                ),
            )
        private val FALLBACK_REQUEST_PROFILES =
            listOf(
                RequestProfile(
                    userAgent = BROWSER_USER_AGENT,
                    timeoutMs = FALLBACK_REQUEST_TIMEOUT_MS,
                    headers =
                    mapOf(
                        "Accept" to
                            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                        "Accept-Language" to "en-GB,en;q=0.9",
                        "Cache-Control" to "no-cache",
                        "Upgrade-Insecure-Requests" to "1",
                    ),
                ),
            )
    }
}

private data class RequestProfile(val userAgent: String, val timeoutMs: Int, val headers: Map<String, String>,)
