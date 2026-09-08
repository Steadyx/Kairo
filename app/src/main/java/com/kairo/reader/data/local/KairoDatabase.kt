package com.kairo.reader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        TableOfContentsEntryEntity::class,
        ReadingPositionEntity::class,
        BookmarkEntity::class,
        SavedAnnotationEntity::class,
        ReadingSessionEntity::class,
        ReadingSessionCheckpointEntity::class,
    ],
    version = 14,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class KairoDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun epubNavigationDao(): EpubNavigationDao

    abstract fun readingPositionDao(): ReadingPositionDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun savedAnnotationDao(): SavedAnnotationDao

    abstract fun readingSessionDao(): ReadingSessionDao

    abstract fun searchDao(): SearchDao
}
