package com.kairo.reader.data.local

import androidx.room.Query
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ChapterTextChunk(val bytes: ByteArray?)

/** Read chapter bodies below CursorWindow limits without changing stored reading coordinates. */
interface ChapterTextQueries {
    @Query(
        """
        SELECT substr(CAST(htmlContent AS BLOB), :offset, :byteCount) AS bytes
        FROM chapters WHERE bookId = :bookId AND `index` = :chapterIndex
        """,
    )
    suspend fun getChapterHtmlChunk(bookId: String, chapterIndex: Int, offset: Int, byteCount: Int): ChapterTextChunk?

    @Query(
        """
        SELECT substr(CAST(plainText AS BLOB), :offset, :byteCount) AS bytes
        FROM chapters WHERE bookId = :bookId AND `index` = :chapterIndex
        """,
    )
    suspend fun getChapterPlainTextChunk(bookId: String, chapterIndex: Int, offset: Int, byteCount: Int): ChapterTextChunk?
}

internal suspend fun ChapterTextQueries.readChapterHtml(bookId: String, chapterIndex: Int): String? =
    readChapterText { offset -> getChapterHtmlChunk(bookId, chapterIndex, offset, CHAPTER_TEXT_CHUNK_BYTES) }

internal suspend fun ChapterTextQueries.readChapterPlainText(bookId: String, chapterIndex: Int): String? =
    readChapterText { offset -> getChapterPlainTextChunk(bookId, chapterIndex, offset, CHAPTER_TEXT_CHUNK_BYTES) }

internal suspend fun ChapterTextQueries.withChapterContent(chapter: ChapterEntity): ChapterEntity =
    chapter.copy(
        htmlContent = requireNotNull(readChapterHtml(chapter.bookId, chapter.index)),
        plainText = requireNotNull(readChapterPlainText(chapter.bookId, chapter.index)),
    )

private suspend fun readChapterText(readChunk: suspend (Int) -> ChapterTextChunk?): String? {
    val bytes = ByteArrayOutputStream()
    var offset = 1 // SQLite substr positions are one-based.
    do {
        currentCoroutineContext().ensureActive()
        val row = readChunk(offset) ?: return null
        // Android's SQLite bridge can expose an empty BLOB as null. The row wrapper
        // distinguishes that valid empty body from a chapter that does not exist.
        val chunk = row.bytes ?: byteArrayOf()
        bytes.write(chunk)
        offset += chunk.size
    } while (chunk.size == CHAPTER_TEXT_CHUNK_BYTES)
    // Decode after reassembly: a UTF-8 character may straddle a byte boundary.
    return bytes.toString(Charsets.UTF_8.name())
}

private const val CHAPTER_TEXT_CHUNK_BYTES = 128 * 1024
