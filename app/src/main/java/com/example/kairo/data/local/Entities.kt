package com.example.kairo.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authors: List<String>,
    val coverImage: ByteArray?
)

@Entity(tableName = "chapters", primaryKeys = ["bookId", "index"])
data class ChapterEntity(
    val bookId: String,
    val index: Int,
    val title: String?,
    val htmlContent: String,
    val plainText: String,
    val imagePaths: String = ""
)

@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey val bookId: String,
    val chapterIndex: Int,
    val tokenIndex: Int
)

@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["createdAt"]),
        Index(value = ["bookId", "chapterIndex", "tokenIndex"], unique = true)
    ]
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val tokenIndex: Int,
    val previewText: String,
    val createdAt: Long
)
