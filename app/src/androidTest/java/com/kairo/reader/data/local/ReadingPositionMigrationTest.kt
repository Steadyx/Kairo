package com.kairo.reader.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingPositionMigrationTest {
    @Test
    fun existingPositionsSurviveMigration() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "position-migration-${UUID.randomUUID()}.db"
        try {
            val original = Room.databaseBuilder(context, KairoDatabase::class.java, name).build()
            original.readingPositionDao().savePosition(ReadingPositionEntity("book", 3, 10, 8, 2))
            original.close()

            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
                database.execSQL("ALTER TABLE reading_positions RENAME TO old_reading_positions")
                database.execSQL(
                    "CREATE TABLE reading_positions (bookId TEXT NOT NULL PRIMARY KEY, " +
                        "chapterIndex INTEGER NOT NULL, tokenIndex INTEGER NOT NULL, " +
                        "wordIndex INTEGER NOT NULL, rsvpResumeCursor INTEGER NOT NULL)"
                )
                database.execSQL(
                    "INSERT INTO reading_positions SELECT bookId, chapterIndex, tokenIndex, " +
                        "wordIndex, rsvpResumeCursor FROM old_reading_positions"
                )
                database.execSQL("DROP TABLE old_reading_positions")
                database.execSQL("DROP TABLE chapters")
                database.execSQL(
                    "CREATE TABLE chapters (bookId TEXT NOT NULL, `index` INTEGER NOT NULL, title TEXT, " +
                        "htmlContent TEXT NOT NULL, plainText TEXT NOT NULL, imagePaths TEXT NOT NULL, " +
                        "wordCount INTEGER NOT NULL, wordCountVersion INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(bookId, `index`))"
                )
                database.execSQL(
                    "INSERT INTO chapters VALUES ('book', 0, 'Chapter', 'html', 'text', '', 1, 1)"
                )
                database.version = 15
            }

            val migrated = Room.databaseBuilder(context, KairoDatabase::class.java, name)
                .addMigrations(MIGRATION_15_16)
                .build()
            try {
                assertEquals(ReadingPositionEntity("book", 3, 10, 8, 2, -1), migrated.readingPositionDao().getPosition("book"))
                assertEquals(true, migrated.bookDao().getChapter("book", 0)?.isLinear)
                migrated.readingPositionDao().savePosition(ReadingPositionEntity("book", 3, 0, -1, -1, 2))
                assertEquals(2, migrated.readingPositionDao().getPosition("book")?.imagePageIndex)
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
