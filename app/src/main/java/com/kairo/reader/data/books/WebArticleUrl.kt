package com.kairo.reader.data.books

import java.net.IDN
import java.net.URI
import java.util.Locale

internal object WebArticleUrl {
    fun normalize(rawUrl: String): String {
        val candidate =
            rawUrl
                .trim()
                .let { extractBestWebUrl(it) ?: it }
                .removeSurrounding("<", ">")
                .trimTrailingUrlPunctuation()
                .let { value ->
                    if (value.contains("://")) value else "https://$value"
                }

        val uri =
            runCatching { URI(candidate) }
                .getOrElse { throw IllegalArgumentException("Enter a valid web link.") }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "https") {
            "Only https links are supported."
        }
        val host =
            uri.host
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Enter a valid web link.")
        val normalizedHost = IDN.toASCII(host).lowercase(Locale.ROOT)
        val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val query = uri.rawQuery?.withoutTrackingParameters()?.let { "?$it" }.orEmpty()
        return "$scheme://$normalizedHost$port$path$query"
    }

    fun extractBestWebUrl(text: String): String? =
        WEB_URL_REGEX
            .findAll(text)
            .map { match -> match.value.trimTrailingUrlPunctuation() }
            .mapNotNull { url ->
                val uri = runCatching { URI(url) }.getOrNull() ?: return@mapNotNull null
                val scheme = uri.scheme?.lowercase(Locale.ROOT)
                if (scheme == "https") {
                    ScoredUrl(url = url, score = uri.shareScore())
                } else {
                    null
                }
            }
            .maxByOrNull { it.score }
            ?.url

    fun extractFirstWebUrl(text: String): String? = extractBestWebUrl(text)

    fun displayHost(normalizedUrl: String): String =
        runCatching { URI(normalizedUrl).host }
            .getOrNull()
            ?.removePrefix("www.")
            .orEmpty()
            .ifBlank { "Web article" }

    private fun String.trimTrailingUrlPunctuation(): String {
        var value = trim()
        while (value.isNotEmpty()) {
            val closing = value.last()
            val opening = when (closing) {
                ')' -> '('
                ']' -> '['
                '}' -> '{'
                else -> null
            }
            val unmatchedBracket = opening != null && value.count { it == closing } > value.count { it == opening }
            if (!unmatchedBracket && closing !in ".,;:!?\"'") break
            value = value.dropLast(1)
        }
        return value
    }

    private fun String.withoutTrackingParameters(): String? {
        val kept =
            split("&")
                .filter { parameter ->
                    val name = parameter.substringBefore("=").lowercase(Locale.ROOT)
                    name.isNotBlank() && !name.isTrackingParameter()
                }
        return kept.joinToString("&").takeIf { it.isNotBlank() }
    }

    private fun String.isTrackingParameter(): Boolean =
        startsWith("utm_") || this in TRACKING_PARAMETERS

    private fun URI.shareScore(): Int {
        val host = host?.lowercase(Locale.ROOT)?.removePrefix("www.").orEmpty()
        val path = rawPath.orEmpty().trim('/')
        val segments = path.split('/').filter { it.isNotBlank() }
        val query = rawQuery.orEmpty()

        var score = 0
        if (!host.isSocialHost()) score += NON_SOCIAL_HOST_SCORE
        if (segments.isNotEmpty()) score += HAS_PATH_SCORE
        score += (segments.size.coerceAtMost(MAX_SEGMENT_SCORE_COUNT) * PATH_SEGMENT_SCORE)
        if (segments.any { it.any(Char::isDigit) }) score += NUMERIC_PATH_SCORE
        if (query.isNotBlank()) score += QUERY_SCORE
        if (host.isSocialHost()) score -= SOCIAL_HOST_PENALTY
        if (segments.size <= 1 && host.isSocialHost()) score -= SHALLOW_SOCIAL_LINK_PENALTY
        return score
    }

    private fun String.isSocialHost(): Boolean =
        SOCIAL_HOSTS.any { socialHost -> this == socialHost || endsWith(".$socialHost") }

    private data class ScoredUrl(val url: String, val score: Int,)

    private val WEB_URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private const val MAX_SEGMENT_SCORE_COUNT = 6
    private const val NON_SOCIAL_HOST_SCORE = 1_000
    private const val HAS_PATH_SCORE = 180
    private const val PATH_SEGMENT_SCORE = 35
    private const val NUMERIC_PATH_SCORE = 40
    private const val QUERY_SCORE = 15
    private const val SOCIAL_HOST_PENALTY = 900
    private const val SHALLOW_SOCIAL_LINK_PENALTY = 250
    private val SOCIAL_HOSTS =
        setOf(
            "facebook.com",
            "fb.com",
            "instagram.com",
            "threads.net",
            "twitter.com",
            "x.com",
            "linkedin.com",
            "youtube.com",
            "youtu.be",
            "tiktok.com",
        )
    private val TRACKING_PARAMETERS =
        setOf(
            "fbclid",
            "gclid",
            "gbraid",
            "wbraid",
            "igshid",
            "mc_cid",
            "mc_eid",
            "mkt_tok",
            "ref",
            "ref_src",
            "spm",
            "at_campaign",
            "at_format",
            "at_link_id",
            "at_link_origin",
            "at_link_type",
            "at_medium",
            "at_ptr_name",
            "xtor",
            "_hsenc",
            "_hsmi",
        )
}
