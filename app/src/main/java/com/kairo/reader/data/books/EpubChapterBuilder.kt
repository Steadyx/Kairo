package com.kairo.reader.data.books

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.countWords
import com.kairo.reader.data.books.epub.EpubChapterOrdering
import com.kairo.reader.data.books.epub.EpubTextDecoder
import java.util.Locale

internal data class FallbackChapterBuildResult(
    val chapters: List<ParsedChapter>,
    val navigationFilteredCount: Int,
    val navigationFilterSuppressed: Boolean,
)

internal class EpubChapterBuilder(
    private val contentRewriter: EpubContentRewriter,
    private val navigationClassifier: EpubNavigationClassifier,
) {
    private companion object {
        const val MAX_EXACT_WORD_COUNT_CHARS = 120_000
        const val PAGE_BREAK_MARKER = "\u000C"
    }

    fun buildWithResult(
        zipTextEntries: Map<String, ByteArray>,
        imageRelativePathByEpubPathLower: Map<String, String>,
        preferredChapterPathsLower: List<String>,
        preservedNavigationPathsLower: Set<String> = emptySet(),
        htmlOverridesByPathLower: Map<String, String> = emptyMap(),
    ): FallbackChapterBuildResult =
        buildWithResult(
            zipTextEntries = zipTextEntries,
            imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
            preferredChapterPathsLower = preferredChapterPathsLower,
            decodedTextEntries = emptyMap(),
            chapterImageSrcsByPathLower = emptyMap(),
            preservedNavigationPathsLower = preservedNavigationPathsLower,
            htmlOverridesByPathLower = htmlOverridesByPathLower,
        )

    fun buildWithResult(
        zipTextEntries: Map<String, ByteArray>,
        imageRelativePathByEpubPathLower: Map<String, String>,
        preferredChapterPathsLower: List<String>,
        decodedTextEntries: Map<String, String>,
        chapterImageSrcsByPathLower: Map<String, List<String>>,
        preservedNavigationPathsLower: Set<String> = emptySet(),
        htmlOverridesByPathLower: Map<String, String> = emptyMap(),
    ): FallbackChapterBuildResult {
        val normalizedHtmlOverrides =
            htmlOverridesByPathLower.mapKeys { (path, _) -> path.lowercase(Locale.ROOT) }
        val preferredCandidates =
            preferredChapterPathsLower
                .asSequence()
                .map { it.lowercase(Locale.ROOT) }
                .filter(EpubChapterOrdering::isChapterCandidateEntry)
                .filter { zipTextEntries.containsKey(it) }
                .distinct()
                .toList()
        val fallbackCandidatesBase =
            zipTextEntries.keys
                .asSequence()
                .filter(EpubChapterOrdering::isChapterCandidateEntry)
                .filterNot { preferredCandidates.contains(it) }
                .sortedWith(EpubChapterOrdering::comparePathsNaturally)
                .toList()
        val candidates = preferredCandidates + fallbackCandidatesBase
        if (candidates.isEmpty()) {
            return FallbackChapterBuildResult(
                chapters = emptyList(),
                navigationFilteredCount = 0,
                navigationFilterSuppressed = false,
            )
        }

        val parsed =
            candidates.mapNotNull { pathLower ->
                val chapterContent = zipTextEntries[pathLower] ?: return@mapNotNull null
                val htmlOverride = normalizedHtmlOverrides[pathLower]
                val originalHtml =
                    htmlOverride
                        ?: decodedTextEntries[pathLower]
                        ?: EpubTextDecoder.decodeTextEntry(chapterContent)
                val originalDocument = contentRewriter.parseMarkupDocument(originalHtml)
                val chapterDir = pathLower.substringBeforeLast('/', "")
                val imagePaths = contentRewriter.buildChapterImagePaths(
                    html = originalHtml,
                    baseDir = chapterDir,
                    imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                    chapterSrcs =
                    if (htmlOverride == null) {
                        chapterImageSrcsByPathLower[pathLower]
                    } else {
                        emptyList()
                    },
                )
                val resolvedHtml = contentRewriter.rewriteHtmlImageSrcs(
                    html = originalHtml,
                    baseDir = chapterDir,
                    imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                )
                val rawTitle = contentRewriter.extractChapterTitle(originalDocument)
                val fileTitle =
                    pathLower
                        .substringAfterLast('/', pathLower)
                        .substringBeforeLast('.')
                val title = contentRewriter.sanitizeChapterTitle(rawTitle ?: fileTitle)
                val cleanedHtml = contentRewriter.stripNoiseTitleBlocks(resolvedHtml)
                val plainTextContent =
                    if (cleanedHtml == resolvedHtml) {
                        contentRewriter.extractPlainTextWithAnchors(originalDocument)
                    } else {
                        contentRewriter.extractPlainTextWithAnchors(cleanedHtml)
                    }
                val plainText = plainTextContent.text
                val wordCount =
                    if (plainText.length <= MAX_EXACT_WORD_COUNT_CHARS) {
                        countWords(plainText)
                    } else {
                        0
                    }

                if (plainText.isBlank() &&
                    !plainText.contains(PAGE_BREAK_MARKER) &&
                    imagePaths.isEmpty()
                ) {
                    return@mapNotNull null
                }

                ParsedChapter(
                    pathLower = pathLower,
                    baseDir = chapterDir,
                    chapter = Chapter(
                        index = 0,
                        title = title,
                        htmlContent = cleanedHtml,
                        plainText = plainText,
                        imagePaths = imagePaths,
                        wordCount = wordCount,
                    ),
                    anchorOffsets = plainTextContent.anchorOffsets,
                )
            }

        val navigationFilterResult =
            navigationClassifier.filter(
                parsed = parsed,
                preservedPathsLower =
                preservedNavigationPathsLower
                    .asSequence()
                    .map { it.lowercase(Locale.ROOT) }
                    .toSet(),
            )
        val reIndexedChapters = navigationFilterResult.chapters.mapIndexed { index, entry ->
            entry.copy(chapter = entry.chapter.copy(index = index))
        }
        return FallbackChapterBuildResult(
            chapters = reIndexedChapters,
            navigationFilteredCount = navigationFilterResult.filteredCount,
            navigationFilterSuppressed = navigationFilterResult.suppressed,
        )
    }
}
