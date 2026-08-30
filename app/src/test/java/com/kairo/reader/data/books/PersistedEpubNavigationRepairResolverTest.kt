package com.kairo.reader.data.books

import com.kairo.reader.data.local.EpubNavigationChapterCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistedEpubNavigationRepairResolverTest {
    private val resolver = PersistedEpubNavigationRepairResolver()

    @Test
    fun resolvesExactlyOneExplicitNavigationOnlyTocAmongBroadCandidates() {
        val result =
            resolver.resolve(
                candidates =
                listOf(
                    candidate(
                        index = 0,
                        html =
                        "<html><body><p>Prose</p><nav epub:type=\"toc\"><ol>" +
                            "<li><a href=\"kairo://chapter/1\">Mixed</a></li>" +
                            "</ol></nav></body></html>",
                    ),
                    candidate(
                        index = 1,
                        html =
                        "<html><body><nav epub:type=\"toc\"><ol>" +
                            "<li><a href=\"kairo://chapter/2\">Contents</a></li>" +
                            "</ol></nav></body></html>",
                    ),
                    candidate(
                        index = 2,
                        html =
                        "<html><body><nav><ol>" +
                            "<li><a href=\"kairo://chapter/2\">Fallback</a></li>" +
                            "</ol></nav></body></html>",
                    ),
                ),
                validChapterIndexes = setOf(0, 1, 2),
            )

        assertEquals(1, requireNotNull(result).candidate.chapterIndex)
        assertTrue(result.canonicalHtml.contains("href=\"kairo://chapter/2\""))
    }

    @Test
    fun rejectsMultipleExplicitNavigationOnlyTocs() {
        val first =
            candidate(
                index = 0,
                html =
                "<nav epub:type=\"toc\"><ol>" +
                    "<li><a href=\"kairo://chapter/1\">One</a></li></ol></nav>",
            )
        val second = first.copy(chapterIndex = 1, title = "Other contents")

        assertNull(resolver.resolve(listOf(first, second), setOf(0, 1)))
    }

    private fun candidate(
        index: Int,
        html: String,
    ): EpubNavigationChapterCandidate =
        EpubNavigationChapterCandidate(
            chapterIndex = index,
            title = "Contents",
            htmlContent = html,
        )
}
