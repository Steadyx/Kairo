package com.example.kairo.data.local

import androidx.room.Entity
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
    val plainText: String
)

@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey val bookId: String,
    val chapterIndex: Int,
    val tokenIndex: Int
)
