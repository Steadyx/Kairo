package com.kairo.reader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

data class EpubNavigationChapterCandidate(val chapterIndex: Int, val title: String?, val htmlContent: String,)

data class EpubChapterCoordinate(val chapterIndex: Int, val plainText: String,)

@Dao
interface EpubNavigationDao {
    @Query(
        """
        SELECT `index` AS chapterIndex, title, htmlContent
        FROM chapters
        WHERE bookId = :bookId
          AND instr(htmlContent, :canonicalMarker) = 0
          AND instr(lower(htmlContent), '<nav') > 0
          AND length(htmlContent) <= :maxHtmlCharacters
        ORDER BY `index`
        LIMIT :limit
        """,
    )
    suspend fun getMarkerlessNavigationCandidates(
        bookId: String,
        canonicalMarker: String,
        maxHtmlCharacters: Int,
        limit: Int,
    ): List<EpubNavigationChapterCandidate>

    @Query(
        """
        SELECT `index` AS chapterIndex, plainText
        FROM chapters
        WHERE bookId = :bookId
        ORDER BY `index`
        """,
    )
    suspend fun getChapterCoordinates(bookId: String): List<EpubChapterCoordinate>

    @Query(
        """
        SELECT htmlContent
        FROM chapters
        WHERE bookId = :bookId AND `index` = :chapterIndex
        LIMIT 1
        """,
    )
    suspend fun getChapterHtmlContent(
        bookId: String,
        chapterIndex: Int,
    ): String?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM table_of_contents_entries
            WHERE bookId = :bookId AND chapterIndex = :chapterIndex
            UNION ALL
            SELECT 1 FROM bookmarks
            WHERE bookId = :bookId AND chapterIndex = :chapterIndex
            UNION ALL
            SELECT 1 FROM saved_annotations
            WHERE bookId = :bookId AND chapterIndex = :chapterIndex
            UNION ALL
            SELECT 1 FROM reading_sessions
            WHERE bookId = :bookId
              AND (startChapterIndex = :chapterIndex OR endChapterIndex = :chapterIndex)
            UNION ALL
            SELECT 1 FROM reading_session_checkpoints
            WHERE bookId = :bookId
              AND (startChapterIndex = :chapterIndex OR endChapterIndex = :chapterIndex)
        )
        """,
    )
    suspend fun hasDurableNavigationCoordinates(
        bookId: String,
        chapterIndex: Int,
    ): Boolean

    @Query(
        """
        UPDATE chapters
        SET htmlContent = :htmlContent,
            plainText = :plainText,
            wordCount = :wordCount
        WHERE bookId = :bookId
          AND `index` = :chapterIndex
          AND htmlContent = :expectedHtmlContent
        """,
    )
    suspend fun updateCanonicalNavigationChapter(
        bookId: String,
        chapterIndex: Int,
        expectedHtmlContent: String,
        htmlContent: String,
        plainText: String,
        wordCount: Int,
    ): Int

    @Query(
        """
        UPDATE reading_positions
        SET tokenIndex = 0,
            wordIndex = 0,
            rsvpResumeCursor = -1
        WHERE bookId = :bookId AND chapterIndex = :chapterIndex
        """,
    )
    suspend fun resetNavigationReadingPosition(
        bookId: String,
        chapterIndex: Int,
    )

    @Query("DELETE FROM table_of_contents_entries WHERE bookId = :bookId")
    suspend fun deleteTableOfContentsEntries(bookId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTableOfContentsEntries(entries: List<TableOfContentsEntryEntity>)

    @Transaction
    suspend fun canonicalizeLegacyNavigationChapter(
        bookId: String,
        chapterIndex: Int,
        expectedHtmlContent: String,
        htmlContent: String,
        plainText: String,
        wordCount: Int,
    ): Boolean {
        if (getChapterHtmlContent(bookId, chapterIndex) != expectedHtmlContent) return false
        if (hasDurableNavigationCoordinates(bookId, chapterIndex)) return false
        val updated =
            updateCanonicalNavigationChapter(
                bookId = bookId,
                chapterIndex = chapterIndex,
                expectedHtmlContent = expectedHtmlContent,
                htmlContent = htmlContent,
                plainText = plainText,
                wordCount = wordCount,
            )
        if (updated != 1) return false
        resetNavigationReadingPosition(bookId, chapterIndex)
        return true
    }

    @Transaction
    suspend fun replaceTableOfContentsIfCoordinatesMatch(
        bookId: String,
        expectedCoordinates: List<EpubChapterCoordinate>,
        entries: List<TableOfContentsEntryEntity>,
    ): Boolean {
        val currentCoordinates = getChapterCoordinates(bookId)
        if (currentCoordinates != expectedCoordinates) return false
        val textByIndex = currentCoordinates.associate { it.chapterIndex to it.plainText }
        if (textByIndex.size != currentCoordinates.size) return false
        if (entries.any { entry ->
                entry.bookId != bookId ||
                    entry.chapterIndex?.let { chapterIndex ->
                        val text = textByIndex[chapterIndex] ?: return@let true
                        val offset = entry.characterOffset ?: 0
                        offset !in 0..text.length
                    } == true
            }
        ) {
            return false
        }
        deleteTableOfContentsEntries(bookId)
        if (entries.isNotEmpty()) insertTableOfContentsEntries(entries)
        return true
    }
}
