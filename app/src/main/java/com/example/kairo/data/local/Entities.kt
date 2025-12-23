package com.example.kairo.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(@PrimaryKey val id: String, val title: String, val authors: List<String>, val coverImage: ByteArray?,) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BookEntity) return false

        if (id != other.id) return false
        if (title != other.title) return false
        if (authors != other.authors) return false
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
        result = 31 * result + (coverImage?.contentHashCode() ?: 0)
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
)

@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(@PrimaryKey val bookId: String, val chapterIndex: Int, val tokenIndex: Int,)

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
