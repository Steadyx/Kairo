package com.example.kairo.data.books.epub

import java.io.StringReader
import java.util.Locale
import org.xml.sax.InputSource

internal class EpubContainerParser {
    fun parse(xml: String): ContainerXmlResolution {
        val fallback = "OEBPS/content.opf"
        return runCatching {
            val doc =
                EpubXmlUtils
                    .newSafeFactory()
                    .newDocumentBuilder()
                    .apply { setErrorHandler(EpubXmlUtils.strictXmlErrorHandler) }
                    .parse(InputSource(StringReader(xml)))

            val rootfiles = EpubXmlUtils.findElementsByLocalName(doc, "rootfile")
            val candidates =
                rootfiles
                    .mapNotNull { rootfile ->
                        val fullPath =
                            rootfile
                                .attributes
                                .getNamedItem("full-path")
                                ?.nodeValue
                                ?.trim()
                                ?.takeIf { it.isNotBlank() }
                                ?: return@mapNotNull null
                        val mediaType =
                            rootfile
                                .attributes
                                .getNamedItem("media-type")
                                ?.nodeValue
                                ?.trim()
                        fullPath to mediaType
                    }

            val orderedCandidates = orderRootfileCandidates(candidates)
            val resolvedPath = orderedCandidates.firstOrNull() ?: fallback
            ContainerXmlResolution(
                path = resolvedPath,
                usedLenientFallback = false,
                candidatePaths = orderedCandidates.ifEmpty { listOf(fallback) },
            )
        }.getOrElse {
            val orderedCandidates = orderRootfileCandidates(parseLenientCandidates(xml))
            val resolvedPath = orderedCandidates.firstOrNull() ?: fallback
            ContainerXmlResolution(
                path = resolvedPath,
                usedLenientFallback = true,
                candidatePaths = orderedCandidates.ifEmpty { listOf(fallback) },
            )
        }
    }

    fun parseLenient(xml: String): String? {
        return orderRootfileCandidates(parseLenientCandidates(xml)).firstOrNull()
    }

    private fun parseLenientCandidates(xml: String): List<Pair<String, String?>> {
        val rootfileRegex = Regex("(?is)<(?:[A-Za-z0-9_.-]+:)?rootfile\\b[^>]*(?:>|$)")
        val candidates = mutableListOf<Pair<String, String?>>()
        rootfileRegex.findAll(xml).forEach { match ->
            val attrs = parseTagAttributes(match.value)
            val fullPath = attrs["full-path"]?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
            val mediaType = attrs["media-type"]?.trim()
            candidates.add(fullPath to mediaType)
        }
        return candidates
    }

    private fun orderRootfileCandidates(candidates: List<Pair<String, String?>>): List<String> {
        return candidates
            .asSequence()
            .sortedWith(
                compareBy(
                    { (_, mediaType) ->
                        if (mediaType.equals("application/oebps-package+xml", ignoreCase = true)) {
                            0
                        } else {
                            1
                        }
                    },
                    { (path, _) -> EpubPathResolver.opfFallbackPriority(path.lowercase(Locale.ROOT)) },
                    { (path, _) -> EpubPathResolver.pathDepth(path) },
                    { (path, _) -> path.length },
                    { (path, _) -> path.lowercase(Locale.ROOT) },
                ),
            ).map { (path, _) -> path }
            .distinct()
            .toList()
    }

    private fun parseTagAttributes(tag: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        val attrRegex = Regex("([A-Za-z_][A-Za-z0-9_:.\\-]*)\\s*=\\s*(['\"])(.*?)\\2")
        attrRegex.findAll(tag).forEach { match ->
            val name = match.groupValues[1].lowercase(Locale.ROOT)
            val value = EpubHtmlEntities.decode(match.groupValues[3]).trim()
            if (value.isNotBlank()) {
                attrs[name] = value
            }
        }
        return attrs
    }
}
