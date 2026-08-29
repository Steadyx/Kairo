package com.kairo.reader.data.books

import com.kairo.reader.core.model.TableOfContentsEntry
import com.kairo.reader.core.model.TableOfContentsTarget
import com.kairo.reader.data.books.epub.EpubNavigationReference
import com.kairo.reader.data.books.epub.EpubPathResolver

internal class EpubTableOfContentsResolver(private val contentRewriter: EpubContentRewriter,) {
    fun resolve(
        references: List<EpubNavigationReference>,
        navigationPathLower: String,
        chapters: List<ParsedChapter>,
    ): List<TableOfContentsEntry> {
        if (references.isEmpty() || chapters.isEmpty()) return emptyList()
        val chapterByPathLower = chapters.associateBy(ParsedChapter::pathLower)
        val navigationBaseDir = navigationPathLower.substringBeforeLast('/', "")

        return references.map { reference ->
            TableOfContentsEntry(
                label = reference.label,
                depth = reference.depth.coerceAtLeast(0),
                target =
                reference.href?.let { href ->
                    resolveTarget(
                        href = href,
                        navigationPathLower = navigationPathLower,
                        navigationBaseDir = navigationBaseDir,
                        chapterByPathLower = chapterByPathLower,
                    )
                },
            )
        }
    }

    private fun resolveTarget(
        href: String,
        navigationPathLower: String,
        navigationBaseDir: String,
        chapterByPathLower: Map<String, ParsedChapter>,
    ): TableOfContentsTarget? {
        if (isExternalHref(href)) return null
        val hrefParts = contentRewriter.splitHrefParts(href)
        val decodedPath = contentRewriter.decodeUrlPath(hrefParts.path).trim()
        val targetPath =
            when {
                decodedPath.isNotBlank() ->
                    EpubPathResolver.resolveZipEntryKey(
                        baseDir = navigationBaseDir,
                        rawHref = decodedPath,
                        availableEntriesLower = chapterByPathLower.keys,
                    )
                hrefParts.fragment.isNotBlank() -> navigationPathLower
                else -> null
            } ?: return null
        val chapter = chapterByPathLower[targetPath] ?: return null
        val fragment =
            contentRewriter
                .decodeUrlPath(hrefParts.fragment)
                .let(contentRewriter::decodeHtmlEntities)
                .trim()
        val characterOffset =
            fragment
                .takeIf(String::isNotBlank)
                ?.let { anchorId -> resolveAnchorOffset(chapter.anchorOffsets, anchorId) }
                ?: 0
        return TableOfContentsTarget(
            chapterIndex = chapter.chapter.index,
            characterOffset = characterOffset,
        )
    }

    private fun resolveAnchorOffset(
        anchors: Map<String, Int>,
        anchorId: String,
    ): Int =
        anchors[anchorId]
            ?: anchors.entries.firstOrNull { (id, _) -> id.equals(anchorId, ignoreCase = true) }?.value
            ?: 0

    private fun isExternalHref(href: String): Boolean {
        val trimmed = href.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("mailto:", ignoreCase = true) ||
            trimmed.startsWith("data:", ignoreCase = true)
    }
}
