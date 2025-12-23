package com.example.kairo.data.local

import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Bookmark
import com.example.kairo.core.model.BookmarkItem
import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.countWords

private const val IMAGE_PATHS_DELIMITER = "|||"

private fun encodeImagePaths(paths: List<String>): String =
    paths
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(IMAGE_PATHS_DELIMITER)

private fun decodeImagePaths(raw: String): List<String> =
    if (raw.isBlank()) emptyList() else raw.split(IMAGE_PATHS_DELIMITER).filter { it.isNotBlank() }

fun Book.toEntity(): BookEntity =
    BookEntity(
        id = id.value,
        title = title,
        authors = authors,
        coverImage = coverImage,
    )

fun Chapter.toEntity(bookId: BookId): ChapterEntity =
    ChapterEntity(
        bookId = bookId.value,
        index = index,
        title = title,
        htmlContent = htmlContent,
        plainText = plainText,
        imagePaths = encodeImagePaths(imagePaths),
        wordCount = if (wordCount > 0) wordCount else countWords(plainText),
    )

fun BookEntity.toDomain(chapters: List<ChapterEntity>): Book =
    Book(
        id = BookId(id),
        title = title,
        authors = authors,
        coverImage = coverImage,
        chapters = chapters.sortedBy { it.index }.map { it.toDomain() },
    )

fun ChapterEntity.toDomain(): Chapter =
    Chapter(
        index = index,
        title = title,
        htmlContent = htmlContent,
        plainText = plainText,
        imagePaths = decodeImagePaths(imagePaths),
        wordCount = wordCount,
    )

fun ReadingPositionEntity.toDomain(): com.example.kairo.core.model.ReadingPosition =
    com.example.kairo.core.model.ReadingPosition(
        bookId = BookId(bookId),
        chapterIndex = chapterIndex,
        tokenIndex = tokenIndex,
    )

fun com.example.kairo.core.model.ReadingPosition.toEntity(): ReadingPositionEntity =
    ReadingPositionEntity(
        bookId = bookId.value,
        chapterIndex = chapterIndex,
        tokenIndex = tokenIndex,
    )

fun BookmarkEntity.toDomain(): Bookmark =
    Bookmark(
        id = id,
        bookId = BookId(bookId),
        chapterIndex = chapterIndex,
        tokenIndex = tokenIndex,
        previewText = previewText,
        createdAt = createdAt,
    )

fun Bookmark.toEntity(): BookmarkEntity =
    BookmarkEntity(
        id = id,
        bookId = bookId.value,
        chapterIndex = chapterIndex,
        tokenIndex = tokenIndex,
        previewText = previewText,
        createdAt = createdAt,
    )

fun BookmarkWithBookEntity.toDomain(): BookmarkItem =
    BookmarkItem(
        bookmark = bookmark.toDomain(),
        book = book.toDomain(chapters = emptyList()),
        chapterCount = chapterCount,
    )
