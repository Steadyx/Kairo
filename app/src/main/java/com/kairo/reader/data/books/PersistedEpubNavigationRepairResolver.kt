package com.kairo.reader.data.books

import com.kairo.reader.data.local.EpubNavigationChapterCandidate

internal data class PersistedEpubNavigationRepair(val candidate: EpubNavigationChapterCandidate, val canonicalHtml: String,)

internal class PersistedEpubNavigationRepairResolver(private val parser: EpubNavigationParser = EpubNavigationParser(),) {
    fun resolve(
        candidates: List<EpubNavigationChapterCandidate>,
        validChapterIndexes: Set<Int>,
    ): PersistedEpubNavigationRepair? {
        val eligible =
            candidates.mapNotNull { candidate ->
                val result = parser.parse(candidate.htmlContent, isNcx = false)
                if (!result.repairEligible) return@mapNotNull null
                val canonicalHtml =
                    EpubReaderNavigationContent.renderPersisted(result, validChapterIndexes)
                        ?: return@mapNotNull null
                PersistedEpubNavigationRepair(candidate, canonicalHtml)
            }
        return eligible.singleOrNull()
    }
}
