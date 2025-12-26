package com.example.kairo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [BookEntity::class, ChapterEntity::class, ReadingPositionEntity::class, BookmarkEntity::class],
    version = 6,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class KairoDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun readingPositionDao(): ReadingPositionDao

    abstract fun bookmarkDao(): BookmarkDao
}
