package com.example.kairo.data.books.epub

import java.util.Locale

internal enum class ChapterOrderSource {
    SPINE,
    SPINE_PLUS_MANIFEST,
    MANIFEST,
    ZIP_FALLBACK,
}

internal data class ChapterOrderResolution(
    val paths: List<String>,
    val source: ChapterOrderSource,
    val unresolvedSpineCount: Int,
)

internal object EpubChapterOrdering {
    private val navigationFileBasenames =
        setOf(
            "nav",
            "navigation",
            "toc",
            "tableofcontents",
            "table-of-contents",
        )

    fun resolveChapterOrder(
        opfData: OpfData,
        opfDir: String,
        availableEntriesLower: Set<String>,
        availableTextEntriesLower: Set<String>,
    ): ChapterOrderResolution {
        val manifestChapterPaths =
            resolveManifestChapterPaths(
                manifestItems = opfData.manifestItems,
                opfDir = opfDir,
                availableEntriesLower = availableEntriesLower,
            )

        val resolvedSpinePaths = mutableListOf<String>()
        var unresolvedSpineCount = 0
        opfData.spineItems.forEach { spineItem ->
            val manifestItem = resolveManifestItem(opfData.manifestItems, spineItem.idref)
            val path =
                if (manifestItem != null && isReadingManifestItem(manifestItem)) {
                    EpubPathResolver.resolveZipEntryKey(opfDir, manifestItem.href, availableEntriesLower)
                } else {
                    null
                }
            if (path != null) {
                resolvedSpinePaths.add(path)
            } else {
                unresolvedSpineCount += 1
            }
        }
        val spineChapterPaths = resolvedSpinePaths

        if (opfData.spineItems.isNotEmpty() && spineChapterPaths.isNotEmpty()) {
            if (unresolvedSpineCount <= 0 || manifestChapterPaths.isEmpty()) {
                return ChapterOrderResolution(
                    paths = spineChapterPaths,
                    source = ChapterOrderSource.SPINE,
                    unresolvedSpineCount = unresolvedSpineCount,
                )
            }
            return ChapterOrderResolution(
                paths = appendUniquePaths(spineChapterPaths, manifestChapterPaths),
                source = ChapterOrderSource.SPINE_PLUS_MANIFEST,
                unresolvedSpineCount = unresolvedSpineCount,
            )
        }
        if (manifestChapterPaths.isNotEmpty()) {
            return ChapterOrderResolution(
                paths = manifestChapterPaths,
                source = ChapterOrderSource.MANIFEST,
                unresolvedSpineCount = unresolvedSpineCount,
            )
        }

        val zipHtmlPaths =
            availableTextEntriesLower
                .asSequence()
                .filter(::isChapterCandidateEntry)
                .sortedWith(::comparePathsNaturally)
                .toList()
        return ChapterOrderResolution(
            paths = zipHtmlPaths,
            source = ChapterOrderSource.ZIP_FALLBACK,
            unresolvedSpineCount = unresolvedSpineCount,
        )
    }

    fun resolveManifestChapterPaths(
        manifestItems: List<ManifestItem>,
        opfDir: String,
        availableEntriesLower: Set<String>,
    ): List<String> {
        return manifestItems
            .asSequence()
            .filter(::isReadingManifestItem)
            .mapNotNull { item ->
                EpubPathResolver.resolveZipEntryKey(opfDir, item.href, availableEntriesLower)
            }.distinct()
            .toList()
    }

    fun resolveManifestItem(
        manifestItems: List<ManifestItem>,
        idref: String,
    ): ManifestItem? {
        val normalizedIdRef = EpubPathResolver.normalizeIdRef(idref)
        if (normalizedIdRef.isBlank()) return null
        return manifestItems.firstOrNull { it.id.equals(normalizedIdRef, ignoreCase = true) }
    }

    fun appendUniquePaths(
        primary: List<String>,
        secondary: List<String>,
    ): List<String> {
        if (primary.isEmpty()) return secondary.distinct()
        if (secondary.isEmpty()) return primary
        val out = ArrayList<String>(primary.size + secondary.size)
        val seen = LinkedHashSet<String>(primary.size + secondary.size)
        primary.forEach { path ->
            out.add(path)
            seen.add(path)
        }
        secondary.forEach { path ->
            if (seen.add(path)) out.add(path)
        }
        return out
    }

    fun isReadingManifestItem(item: ManifestItem): Boolean {
        if (!isContentDocument(item.mediaType, item.href)) return false
        return !item.properties.contains("nav")
    }

    fun isLikelyNavigationHtmlPath(pathLower: String): Boolean {
        val normalized = pathLower.lowercase(Locale.ROOT)
        val fileName = normalized.substringAfterLast('/').substringBeforeLast('.')
        return navigationFileBasenames.contains(fileName)
    }

    fun comparePathsNaturally(
        left: String,
        right: String,
    ): Int {
        var leftIndex = 0
        var rightIndex = 0

        while (leftIndex < left.length && rightIndex < right.length) {
            val leftChar = left[leftIndex]
            val rightChar = right[rightIndex]

            if (leftChar.isDigit() && rightChar.isDigit()) {
                val leftStart = leftIndex
                while (leftIndex < left.length && left[leftIndex].isDigit()) leftIndex += 1
                val rightStart = rightIndex
                while (rightIndex < right.length && right[rightIndex].isDigit()) rightIndex += 1

                val numericComparison =
                    compareNumericSegments(
                        left.substring(leftStart, leftIndex),
                        right.substring(rightStart, rightIndex),
                    )
                if (numericComparison != 0) return numericComparison
                continue
            }

            if (leftChar != rightChar) return leftChar.code - rightChar.code
            leftIndex += 1
            rightIndex += 1
        }

        return left.length - right.length
    }

    fun compareNumericSegments(
        leftDigits: String,
        rightDigits: String,
    ): Int {
        val leftTrimmed = leftDigits.trimStart('0').ifEmpty { "0" }
        val rightTrimmed = rightDigits.trimStart('0').ifEmpty { "0" }

        if (leftTrimmed.length != rightTrimmed.length) {
            return leftTrimmed.length - rightTrimmed.length
        }

        val byValue = leftTrimmed.compareTo(rightTrimmed)
        if (byValue != 0) return byValue

        return leftDigits.length - rightDigits.length
    }

    fun isHtmlEntry(pathLower: String): Boolean {
        val normalized = pathLower.lowercase(Locale.ROOT)
        return normalized.endsWith(".xhtml") ||
            normalized.endsWith(".html") ||
            normalized.endsWith(".htm")
    }

    fun isChapterCandidateEntry(pathLower: String): Boolean {
        val normalized = pathLower.lowercase(Locale.ROOT)
        if (isHtmlEntry(normalized)) return true
        if (!normalized.endsWith(".xml")) return false
        if (normalized.startsWith("meta-inf/")) return false
        val fileName = normalized.substringAfterLast('/')
        return fileName != "container.xml"
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
}
