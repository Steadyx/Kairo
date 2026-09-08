package com.kairo.reader.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["importFingerprint"], unique = true),
    ],
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authors: List<String>,
    val languageTag: String?,
    val coverImage: ByteArray?,
    val isCompleted: Boolean = false,
    val importFingerprint: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BookEntity) return false

        if (id != other.id) return false
        if (title != other.title) return false
        if (authors != other.authors) return false
        if (languageTag != other.languageTag) return false
        if (isCompleted != other.isCompleted) return false
        if (importFingerprint != other.importFingerprint) return false
        if (coverImage != null) {
            if (other.coverImage == null) return false
            if (!coverImage.contentEquals(other.coverImage)) return false
        } else if (other.coverImage != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + authors.hashCode()
        result = 31 * result + (languageTag?.hashCode() ?: 0)
        result = 31 * result + (coverImage?.contentHashCode() ?: 0)
        result = 31 * result + isCompleted.hashCode()
        result = 31 * result + (importFingerprint?.hashCode() ?: 0)
        return result
    }
}

@Entity(tableName = "chapters", primaryKeys = ["bookId", "index"])
data class ChapterEntity(
    val bookId: String,
    val index: Int,
    val title: String?,
    val htmlContent: String,
    val plainText: String,
    val imagePaths: String = "",
    val wordCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val wordCountVersion: Int = 0,
)

@Entity(
    tableName = "table_of_contents_entries",
    primaryKeys = ["bookId", "entryIndex"],
    indices = [Index(value = ["bookId"])],
)
data class TableOfContentsEntryEntity(
    val bookId: String,
    val entryIndex: Int,
    val label: String,
    val depth: Int,
    val chapterIndex: Int?,
    val characterOffset: Int?,
)

@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey val bookId: String,
    val chapterIndex: Int,
    val tokenIndex: Int,
    val wordIndex: Int = -1,
    val rsvpResumeCursor: Int = -1,
)

@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["createdAt"]),
        Index(value = ["bookId", "chapterIndex", "tokenIndex"], unique = true),
    ],
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val tokenIndex: Int,
    val previewText: String,
    val createdAt: Long,
)

@Entity(
    tableName = "saved_annotations",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["bookId", "chapterIndex", "startTokenIndex"]),
    ],
)
data class SavedAnnotationEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val startTokenIndex: Int,
    val endTokenIndex: Int,
    val selectedText: String,
    val note: String,
    val color: String,
    val kind: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["startedAt"]),
    ],
)
data class ReadingSessionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val mode: String,
    val startedAt: Long,
    val endedAt: Long,
    val activeDurationMs: Long,
    val startChapterIndex: Int,
    val startTokenIndex: Int,
    val endChapterIndex: Int,
    val endTokenIndex: Int,
    val wordsRead: Int,
    val effectiveWpm: Int,
    val isWordCountEstimated: Boolean,
)

@Entity(
    tableName = "reading_session_checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["sessionKey"]),
    ],
)
data class ReadingSessionCheckpointEntity(
    @PrimaryKey val id: String,
    val sessionKey: String,
    val logicalSessionId: String,
    val bookId: String,
    val mode: String,
    val logicalStartedAt: Long,
    val dayStartedAt: Long,
    val startedAt: Long,
    val endedAt: Long,
    val activeDurationMs: Long,
    val startChapterIndex: Int,
    val startTokenIndex: Int,
    val endChapterIndex: Int,
    val endTokenIndex: Int,
    val wordsRead: Int,
    val isWordCountEstimated: Boolean,
    val lastReaderWordIndex: Int?,
)
