@file:Suppress("MagicNumber", "MaxLineLength")

package com.example.kairo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bookmarks (
                id TEXT NOT NULL PRIMARY KEY,
                bookId TEXT NOT NULL,
                chapterIndex INTEGER NOT NULL,
                tokenIndex INTEGER NOT NULL,
                previewText TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("DROP INDEX IF EXISTS index_bookmarks_unique_position")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_bookId ON bookmarks(bookId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_createdAt ON bookmarks(createdAt)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_bookmarks_bookId_chapterIndex_tokenIndex ON bookmarks(bookId, chapterIndex, tokenIndex)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Ensure schema matches Room's expected indices (and clean up any older/incorrect index names).
        db.execSQL("DROP INDEX IF EXISTS index_bookmarks_unique_position")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_bookId ON bookmarks(bookId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_createdAt ON bookmarks(createdAt)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_bookmarks_bookId_chapterIndex_tokenIndex ON bookmarks(bookId, chapterIndex, tokenIndex)"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapters ADD COLUMN imagePaths TEXT NOT NULL DEFAULT ''")
    }
}
