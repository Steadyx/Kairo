package com.example.kairo.data.books.epub

import java.io.StringReader
import java.util.Locale
import org.w3c.dom.Document
import org.xml.sax.InputSource

internal class EpubOpfParser {
    companion object {
        private const val DC_NAMESPACE = "http://purl.org/dc/elements/1.1/"
    }

    fun parseWithResult(xml: String): OpfParseResult {
        return runCatching {
            OpfParseResult(opfData = parseStrict(xml), usedLenientFallback = false)
        }.getOrElse {
            val lenient = parseLenient(xml)
            OpfParseResult(opfData = lenient, usedLenientFallback = true)
        }
    }

    fun parseLenient(xml: String): OpfData {
        val title = extractElementTextsLenient(xml, "title").firstOrNull()
        val authors = extractElementTextsLenient(xml, "creator")
        val languageTag = extractElementTextsLenient(xml, "language").firstOrNull()
        val coverId = extractCoverIdLenient(xml)
        val hasPackageTag = Regex("(?is)<(?:[A-Za-z0-9_.-]+:)?package\\b").containsMatchIn(xml)
        val hasSpineTag = Regex("(?is)<(?:[A-Za-z0-9_.-]+:)?spine\\b").containsMatchIn(xml)

        val manifestItems = extractManifestItemsLenient(xml)
        val manifest = LinkedHashMap<String, String>(manifestItems.size)
        var coverHref: String? = null
        manifestItems.forEach { item ->
            manifest[item.id] = item.href
            val isImage = isImageManifestItem(item.mediaType, item.href)
            if (
                coverHref == null &&
                coverId != null &&
                item.id.equals(coverId, ignoreCase = true) &&
                isImage
            ) {
                coverHref = item.href
            }
            if (coverHref == null && item.properties.contains("cover-image") && isImage) {
                coverHref = item.href
            }
            if (
                coverHref == null &&
                isImage &&
                (item.id.contains("cover", ignoreCase = true) ||
                    item.href.contains("cover", ignoreCase = true))
            ) {
                coverHref = item.href
            }
        }

        val spineItems = extractSpineItemsLenient(xml)
        val resolvedSpine =
            when {
                spineItems.isNotEmpty() -> spineItems
                hasPackageTag && !hasSpineTag -> manifestItems.filter(::isReadingManifestItem).map { SpineItem(it.id) }
                else -> emptyList()
            }

        return OpfData(title, authors, languageTag, coverHref, manifest, manifestItems, resolvedSpine)
    }

    private fun parseStrict(xml: String): OpfData {
        val doc =
            EpubXmlUtils
                .newSafeFactory()
                .newDocumentBuilder()
                .apply { setErrorHandler(EpubXmlUtils.strictXmlErrorHandler) }
                .parse(InputSource(StringReader(xml)))

        val title = extractMetadataTexts(doc, "title").firstOrNull()
        val authors = extractMetadataTexts(doc, "creator")
        val languageTag = extractMetadataTexts(doc, "language").firstOrNull()

        val packageElement =
            doc.documentElement
                ?.takeIf { EpubXmlUtils.localNameOf(it).equals("package", ignoreCase = true) }
                ?: EpubXmlUtils.findElementsByLocalName(doc, "package").firstOrNull()
        val metadataElement = packageElement?.let { EpubXmlUtils.findDirectChildByLocalName(it, "metadata") }

        var coverHref: String? = null
        val metaNodes =
            metadataElement?.let { EpubXmlUtils.findDirectChildrenByLocalName(it, "meta") }
                ?: EpubXmlUtils.findElementsByLocalName(doc, "meta")
        var coverId: String? = null
        for (meta in metaNodes) {
            val name = meta.attributes.getNamedItem("name")?.nodeValue
            if (name == "cover") {
                coverId = meta.attributes.getNamedItem("content")?.nodeValue
                break
            }
        }

        val manifest = mutableMapOf<String, String>()
        val manifestNodes =
            packageElement
                ?.let { EpubXmlUtils.findDirectChildByLocalName(it, "manifest") }
                ?.let { EpubXmlUtils.findDirectChildrenByLocalName(it, "item") }
                ?: EpubXmlUtils.findElementsByParentLocalName(doc, "manifest", "item")
        val manifestItems = mutableListOf<ManifestItem>()
        for (item in manifestNodes) {
            val id =
                item.attributes
                    .getNamedItem("id")
                    ?.nodeValue
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: continue
            val hrefRaw = item.attributes.getNamedItem("href")?.nodeValue ?: continue
            val href = EpubPathResolver.normalizeHrefValue(hrefRaw)
            if (href.isBlank()) continue
            val mediaType = item.attributes.getNamedItem("media-type")?.nodeValue
            val properties =
                item.attributes
                    .getNamedItem("properties")
                    ?.nodeValue
                    ?.split(Regex("\\s+"))
                    ?.mapNotNull { token ->
                        token.trim().lowercase(Locale.ROOT).takeIf { it.isNotBlank() }
                    }?.toSet()
                    .orEmpty()

            manifest[id] = href
            manifestItems.add(
                ManifestItem(
                    id = id,
                    href = href,
                    mediaType = mediaType,
                    properties = properties,
                ),
            )
            val isImage = isImageManifestItem(mediaType, href)

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

            if (properties.contains("cover-image") && isImage) {
                coverHref = href
            }
        }

        val spineItems = mutableListOf<SpineItem>()
        val spineNodes =
            packageElement
                ?.let { EpubXmlUtils.findDirectChildByLocalName(it, "spine") }
                ?.let { EpubXmlUtils.findDirectChildrenByLocalName(it, "itemref") }
                ?: EpubXmlUtils.findElementsByParentLocalName(doc, "spine", "itemref")
        for (itemref in spineNodes) {
            val idref =
                EpubPathResolver
                    .normalizeIdRef(itemref.attributes.getNamedItem("idref")?.nodeValue.orEmpty())
                    .takeIf { it.isNotBlank() }
                    ?: continue
            val linear = itemref.attributes.getNamedItem("linear")?.nodeValue
            if (!linear.equals("no", ignoreCase = true)) {
                spineItems.add(SpineItem(idref))
            }
        }

        val resolvedSpine = spineItems.ifEmpty { manifestItems.filter(::isReadingManifestItem).map { SpineItem(it.id) } }
        return OpfData(title, authors, languageTag, coverHref, manifest, manifestItems, resolvedSpine)
    }

    private fun extractCoverIdLenient(xml: String): String? {
        val metaRegex = Regex("(?is)<meta\\b[^>]*>")
        metaRegex.findAll(xml).forEach { match ->
            val attrs = parseTagAttributes(match.value)
            if (attrs["name"]?.equals("cover", ignoreCase = true) == true) {
                return EpubPathResolver.normalizeIdRef(attrs["content"].orEmpty()).takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun extractManifestItemsLenient(xml: String): List<ManifestItem> {
        val itemRegex = Regex("(?is)<(?:[A-Za-z0-9_.-]+:)?item\\b[^>]*>")
        val items = mutableListOf<ManifestItem>()
        val seenIds = LinkedHashSet<String>()
        val sections = extractXmlSectionBodiesLenient(xml, "manifest").ifEmpty { listOf(xml) }
        sections.forEach { section ->
            itemRegex.findAll(section).forEach { match ->
                val attrs = parseTagAttributes(match.value)
                val id = EpubPathResolver.normalizeIdRef(attrs["id"].orEmpty()).takeIf { it.isNotBlank() } ?: return@forEach
                val idKey = id.lowercase(Locale.ROOT)
                if (!seenIds.add(idKey)) return@forEach
                val hrefRaw = attrs["href"] ?: return@forEach
                val href = EpubPathResolver.normalizeHrefValue(hrefRaw)
                if (href.isBlank()) return@forEach
                val mediaType = attrs["media-type"]
                val properties =
                    attrs["properties"]
                        ?.split(Regex("\\s+"))
                        ?.mapNotNull { token ->
                            token.trim().lowercase(Locale.ROOT).takeIf { it.isNotBlank() }
                        }?.toSet()
                        .orEmpty()
                items.add(
                    ManifestItem(
                        id = id,
                        href = href,
                        mediaType = mediaType,
                        properties = properties,
                    ),
                )
            }
        }
        return items
    }

    private fun extractSpineItemsLenient(xml: String): List<SpineItem> {
        val itemRefRegex = Regex("(?is)<(?:[A-Za-z0-9_.-]+:)?itemref\\b[^>]*>")
        val spineItems = mutableListOf<SpineItem>()
        val sections = extractXmlSectionBodiesLenient(xml, "spine").ifEmpty { listOf(xml) }
        sections.forEach { section ->
            itemRefRegex.findAll(section).forEach { match ->
                val attrs = parseTagAttributes(match.value)
                val idref =
                    EpubPathResolver.normalizeIdRef(attrs["idref"].orEmpty()).takeIf { it.isNotBlank() } ?: return@forEach
                val linear = attrs["linear"]
                if (!linear.equals("no", ignoreCase = true)) {
                    spineItems.add(SpineItem(idref))
                }
            }
        }
        return spineItems
    }

    private fun extractXmlSectionBodiesLenient(
        xml: String,
        sectionName: String,
    ): List<String> {
        val escaped = Regex.escape(sectionName)
        val fullSectionRegex =
            Regex(
                "(?is)<(?:[a-zA-Z0-9_.-]+:)?$escaped\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_.-]+:)?$escaped>",
            )
        val fullMatches = fullSectionRegex.findAll(xml).map { it.groupValues[1] }.toList()
        if (fullMatches.isNotEmpty()) return fullMatches

        val startRegex = Regex("(?is)<(?:[a-zA-Z0-9_.-]+:)?$escaped\\b[^>]*>")
        val start = startRegex.find(xml) ?: return emptyList()
        val contentStart = start.range.last + 1
        if (contentStart >= xml.length) return emptyList()
        return listOf(xml.substring(contentStart))
    }

    private fun extractElementTextsLenient(
        xml: String,
        localName: String,
    ): List<String> {
        val pattern = Regex("(?is)<(?:[a-zA-Z0-9_.-]+:)?$localName\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_.-]+:)?$localName>")
        val values = mutableListOf<String>()
        val allTagsRegex = Regex("<[^>]+>")
        val whitespaceRegex = Regex("\\s+")
        pattern.findAll(xml).forEach { match ->
            val raw = match.groupValues.getOrNull(1).orEmpty()
            val withoutTags = allTagsRegex.replace(raw, " ")
            val normalized =
                EpubHtmlEntities
                    .decode(withoutTags)
                    .replace(whitespaceRegex, " ")
                    .trim()
            if (normalized.isNotBlank()) {
                values.add(normalized)
            }
        }
        return values
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

    private fun isImageManifestItem(
        mediaType: String?,
        href: String,
    ): Boolean {
        return mediaType?.startsWith("image/") == true ||
            href.endsWith(".jpg", ignoreCase = true) ||
            href.endsWith(".jpeg", ignoreCase = true) ||
            href.endsWith(".png", ignoreCase = true) ||
            href.endsWith(".gif", ignoreCase = true) ||
            href.endsWith(".webp", ignoreCase = true) ||
            href.endsWith(".svg", ignoreCase = true)
    }

    private fun isReadingManifestItem(item: ManifestItem): Boolean {
        if (!isContentDocument(item.mediaType, item.href)) return false
        return !item.properties.contains("nav")
    }

    private fun isContentDocument(
        mediaType: String?,
        href: String,
    ): Boolean {
        if (mediaType != null) {
            val normalized = mediaType.lowercase(Locale.ROOT)
            if (normalized.contains("xhtml") || normalized.contains("html")) {
                return true
            }
        }
        return href.endsWith(".xhtml", ignoreCase = true) ||
            href.endsWith(".html", ignoreCase = true) ||
            href.endsWith(".htm", ignoreCase = true)
    }

    private fun extractMetadataTexts(
        doc: Document,
        localName: String,
    ): List<String> {
        val candidates =
            listOf(
                doc.getElementsByTagName("dc:$localName"),
                doc.getElementsByTagNameNS(DC_NAMESPACE, localName),
                doc.getElementsByTagName(localName),
            )

        for (nodes in candidates) {
            val texts = EpubXmlUtils.extractTextContents(nodes)
            if (texts.isNotEmpty()) return texts
        }
        return emptyList()
    }
}
