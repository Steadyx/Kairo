package com.kairo.reader.data.local

import androidx.room.Query
import androidx.room.Transaction

/** Metadata and bounded body reads share a transaction, including for existing imports. */
interface ChapterContentQueries : ChapterTextQueries {
    @Query(
        """
        SELECT bookId, `index`, title, '' AS htmlContent, '' AS plainText, imagePaths, wordCount, wordCountVersion
        FROM chapters
        WHERE bookId = :bookId
        ORDER BY `index`
        """,
    )
    suspend fun getChapters(bookId: String): List<ChapterEntity>

    @Query(
        """
        SELECT bookId, `index`, title, '' AS htmlContent, '' AS plainText, imagePaths, wordCount, wordCountVersion
        FROM chapters
        WHERE bookId = :bookId AND `index` = :index
        LIMIT 1
        """,
    )
    suspend fun getChapterMetadata(bookId: String, index: Int): ChapterEntity?

    @Transaction
    suspend fun getChapter(bookId: String, index: Int): ChapterEntity? =
        getChapterMetadata(bookId, index)?.let { withChapterContent(it) }

    @Transaction
    suspend fun getChaptersWithContent(bookId: String): List<ChapterEntity> =
        getChapters(bookId).map { withChapterContent(it) }
}
