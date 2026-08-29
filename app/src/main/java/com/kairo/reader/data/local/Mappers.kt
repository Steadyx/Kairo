package com.kairo.reader.data.local

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Bookmark
import com.kairo.reader.core.model.BookmarkItem
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.HighlightColor
import com.kairo.reader.core.model.ReadingSession
import com.kairo.reader.core.model.ReadingSessionItem
import com.kairo.reader.core.model.ReadingSessionMode
import com.kairo.reader.core.model.SavedAnnotation
import com.kairo.reader.core.model.SavedAnnotationItem
import com.kairo.reader.core.model.SavedAnnotationKind
import com.kairo.reader.core.model.TableOfContentsEntry
import com.kairo.reader.core.model.TableOfContentsTarget

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

fun Book.toEntity(importFingerprint: String? = null): BookEntity =
    BookEntity(
        id = id.value,
        title = title,
        authors = authors,
        languageTag = languageTag,
        coverImage = coverImage,
        isCompleted = isCompleted,
        importFingerprint = importFingerprint,
    )

fun Chapter.toEntity(bookId: BookId): ChapterEntity =
    ChapterEntity(
        bookId = bookId.value,
        index = index,
        title = title,
        htmlContent = htmlContent,
        plainText = plainText,
        imagePaths = encodeImagePaths(imagePaths),
        wordCount = wordCount,
    )

fun BookEntity.toDomain(
    chapters: List<ChapterEntity>,
    tableOfContentsEntries: List<TableOfContentsEntryEntity> = emptyList(),
): Book {
    val sortedChapters = chapters.sortedBy { it.index }
    val tableOfContents =
        tableOfContentsEntries
            .sortedBy { it.entryIndex }
            .map { it.toDomain() }
            .ifEmpty { sortedChapters.map(ChapterEntity::toLegacyTableOfContentsEntry) }
    return Book(
        id = BookId(id),
        title = title,
        authors = authors,
        languageTag = languageTag,
        coverImage = coverImage,
        isCompleted = isCompleted,
        chapters = sortedChapters.map { it.toDomain() },
        tableOfContents = tableOfContents,
    )
}

private fun ChapterEntity.toLegacyTableOfContentsEntry(): TableOfContentsEntry =
    TableOfContentsEntry(
        label = title.orEmpty(),
        depth = 0,
        target = TableOfContentsTarget(chapterIndex = index),
    )

fun TableOfContentsEntry.toEntity(
    bookId: BookId,
    entryIndex: Int,
): TableOfContentsEntryEntity =
    TableOfContentsEntryEntity(
        bookId = bookId.value,
        entryIndex = entryIndex,
        label = label,
        depth = depth,
        chapterIndex = target?.chapterIndex,
        characterOffset = target?.characterOffset,
    )

fun TableOfContentsEntryEntity.toDomain(): TableOfContentsEntry =
    TableOfContentsEntry(
        label = label,
        depth = depth,
        target =
        chapterIndex?.let { resolvedChapterIndex ->
            TableOfContentsTarget(
                chapterIndex = resolvedChapterIndex,
                characterOffset = characterOffset ?: 0,
            )
        },
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

fun ReadingPositionEntity.toDomain(): com.kairo.reader.core.model.ReadingPosition =
    com.kairo.reader.core.model.ReadingPosition(
        bookId = BookId(bookId),
        chapterIndex = chapterIndex,
        tokenIndex = tokenIndex,
        wordIndex = wordIndex,
        rsvpResumeCursor = rsvpResumeCursor,
    )

fun com.kairo.reader.core.model.ReadingPosition.toEntity(): ReadingPositionEntity =
    ReadingPositionEntity(
        bookId = bookId.value,
        chapterIndex = chapterIndex,
        tokenIndex = tokenIndex,
        wordIndex = wordIndex,
        rsvpResumeCursor = rsvpResumeCursor,
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

fun SavedAnnotationEntity.toDomain(): SavedAnnotation =
    SavedAnnotation(
        id = id,
        bookId = BookId(bookId),
        chapterIndex = chapterIndex,
        startTokenIndex = startTokenIndex,
        endTokenIndex = endTokenIndex,
        selectedText = selectedText,
        note = note,
        color = enumValueOrDefault(color, HighlightColor.YELLOW),
        kind = enumValueOrDefault(kind, SavedAnnotationKind.HIGHLIGHT),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun SavedAnnotation.toEntity(): SavedAnnotationEntity =
    SavedAnnotationEntity(
        id = id,
        bookId = bookId.value,
        chapterIndex = chapterIndex,
        startTokenIndex = startTokenIndex,
        endTokenIndex = endTokenIndex,
        selectedText = selectedText,
        note = note,
        color = color.name,
        kind = kind.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun SavedAnnotationWithBookEntity.toDomain(): SavedAnnotationItem =
    SavedAnnotationItem(
        annotation = annotation.toDomain(),
        book = book.toDomain(chapters = emptyList()),
        chapterCount = chapterCount,
    )

fun ReadingSessionEntity.toDomain(): ReadingSession =
    ReadingSession(
        id = id,
        bookId = BookId(bookId),
        mode = enumValueOrDefault(mode, ReadingSessionMode.READER),
        startedAt = startedAt,
        endedAt = endedAt,
        activeDurationMs = activeDurationMs,
        startChapterIndex = startChapterIndex,
        startTokenIndex = startTokenIndex,
        endChapterIndex = endChapterIndex,
        endTokenIndex = endTokenIndex,
        wordsRead = wordsRead,
        effectiveWpm = effectiveWpm,
        isWordCountEstimated = isWordCountEstimated,
    )

fun ReadingSession.toEntity(): ReadingSessionEntity =
    ReadingSessionEntity(
        id = id,
        bookId = bookId.value,
        mode = mode.name,
        startedAt = startedAt,
        endedAt = endedAt,
        activeDurationMs = activeDurationMs,
        startChapterIndex = startChapterIndex,
        startTokenIndex = startTokenIndex,
        endChapterIndex = endChapterIndex,
        endTokenIndex = endTokenIndex,
        wordsRead = wordsRead,
        effectiveWpm = effectiveWpm,
        isWordCountEstimated = isWordCountEstimated,
    )

fun ReadingSessionWithBookEntity.toDomain(): ReadingSessionItem =
    ReadingSessionItem(
        session = session.toDomain(),
        book = book.toDomain(chapters = emptyList()),
    )

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    raw: String,
    fallback: T,
): T = enumValues<T>().firstOrNull { it.name == raw } ?: fallback
