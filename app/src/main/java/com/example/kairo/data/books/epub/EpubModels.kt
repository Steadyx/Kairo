package com.example.kairo.data.books.epub

internal data class OpfData(
    val title: String?,
    val authors: List<String>,
    val languageTag: String?,
    val coverHref: String?,
    val manifest: Map<String, String>,
    val manifestItems: List<ManifestItem>,
    val spineItems: List<SpineItem>,
)

internal data class SpineItem(
    val idref: String,
)

internal data class ManifestItem(
    val id: String,
    val href: String,
    val mediaType: String?,
    val properties: Set<String>,
)

internal data class ContainerXmlResolution(
    val path: String,
    val usedLenientFallback: Boolean,
    val candidatePaths: List<String> = listOf(path),
)

internal data class OpfParseResult(
    val opfData: OpfData,
    val usedLenientFallback: Boolean,
)
