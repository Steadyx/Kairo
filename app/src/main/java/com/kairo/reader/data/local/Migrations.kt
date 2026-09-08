@file:Suppress("MagicNumber", "MaxLineLength")

package com.kairo.reader.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 =
    object : Migration(1, 2) {
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
                """.trimIndent(),
            )
            db.execSQL("DROP INDEX IF EXISTS index_bookmarks_unique_position")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_bookId ON bookmarks(bookId)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_bookmarks_createdAt ON bookmarks(createdAt)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_bookmarks_bookId_chapterIndex_tokenIndex " +
                    "ON bookmarks(bookId, chapterIndex, tokenIndex)",
            )
        }
    }

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Ensure schema matches Room's expected indices (and clean up any older/incorrect index names).
            db.execSQL("DROP INDEX IF EXISTS index_bookmarks_unique_position")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_bookId ON bookmarks(bookId)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_bookmarks_createdAt ON bookmarks(createdAt)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_bookmarks_bookId_chapterIndex_tokenIndex " +
                    "ON bookmarks(bookId, chapterIndex, tokenIndex)",
            )
        }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chapters ADD COLUMN imagePaths TEXT NOT NULL DEFAULT ''")
        }
    }

val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chapters ADD COLUMN wordCount INTEGER NOT NULL DEFAULT 0")
        }
    }

val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE reading_positions ADD COLUMN wordIndex INTEGER NOT NULL DEFAULT -1"
            )
        }
    }

val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN languageTag TEXT")
        }
    }

val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE reading_positions ADD COLUMN rsvpResumeCursor INTEGER NOT NULL DEFAULT -1"
            )
        }
    }

val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0")
        }
    }

val MIGRATION_9_10 =
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN importFingerprint TEXT")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_books_importFingerprint ON books(importFingerprint)",
            )
        }
    }

val MIGRATION_10_11 =
    object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS table_of_contents_entries (
                    bookId TEXT NOT NULL,
                    entryIndex INTEGER NOT NULL,
                    label TEXT NOT NULL,
                    depth INTEGER NOT NULL,
                    chapterIndex INTEGER,
                    characterOffset INTEGER,
                    PRIMARY KEY(bookId, entryIndex)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_table_of_contents_entries_bookId " +
                    "ON table_of_contents_entries(bookId)",
            )
        }
    }

val MIGRATION_11_12 =
    object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS saved_annotations (
                    id TEXT NOT NULL PRIMARY KEY,
                    bookId TEXT NOT NULL,
                    chapterIndex INTEGER NOT NULL,
                    startTokenIndex INTEGER NOT NULL,
                    endTokenIndex INTEGER NOT NULL,
                    selectedText TEXT NOT NULL,
                    note TEXT NOT NULL,
                    color TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_saved_annotations_bookId " +
                    "ON saved_annotations(bookId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_saved_annotations_updatedAt " +
                    "ON saved_annotations(updatedAt)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "index_saved_annotations_bookId_chapterIndex_startTokenIndex " +
                    "ON saved_annotations(bookId, chapterIndex, startTokenIndex)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS reading_sessions (
                    id TEXT NOT NULL PRIMARY KEY,
                    bookId TEXT NOT NULL,
                    mode TEXT NOT NULL,
                    startedAt INTEGER NOT NULL,
                    endedAt INTEGER NOT NULL,
                    activeDurationMs INTEGER NOT NULL,
                    startChapterIndex INTEGER NOT NULL,
                    startTokenIndex INTEGER NOT NULL,
                    endChapterIndex INTEGER NOT NULL,
                    endTokenIndex INTEGER NOT NULL,
                    wordsRead INTEGER NOT NULL,
                    effectiveWpm INTEGER NOT NULL,
                    isWordCountEstimated INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_reading_sessions_bookId " +
                    "ON reading_sessions(bookId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_reading_sessions_startedAt " +
                    "ON reading_sessions(startedAt)",
            )
        }
    }

val MIGRATION_12_13 =
    object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "DELETE FROM saved_annotations WHERE bookId NOT IN (SELECT id FROM books)",
            )
            db.execSQL(
                "DELETE FROM reading_sessions WHERE bookId NOT IN (SELECT id FROM books)",
            )
            migrateSavedAnnotationsWithBookForeignKey(db)
            migrateReadingSessionsWithBookForeignKey(db)
            createReadingSessionCheckpoints(db)
        }
    }

private fun migrateSavedAnnotationsWithBookForeignKey(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE saved_annotations_new (
            id TEXT NOT NULL PRIMARY KEY,
            bookId TEXT NOT NULL,
            chapterIndex INTEGER NOT NULL,
            startTokenIndex INTEGER NOT NULL,
            endTokenIndex INTEGER NOT NULL,
            selectedText TEXT NOT NULL,
            note TEXT NOT NULL,
            color TEXT NOT NULL,
            kind TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        INSERT INTO saved_annotations_new
        SELECT id, bookId, chapterIndex, startTokenIndex, endTokenIndex, selectedText,
               note, color, kind, createdAt, updatedAt
        FROM saved_annotations
        """.trimIndent(),
    )
    db.execSQL("DROP TABLE saved_annotations")
    db.execSQL("ALTER TABLE saved_annotations_new RENAME TO saved_annotations")
    db.execSQL("CREATE INDEX index_saved_annotations_bookId ON saved_annotations(bookId)")
    db.execSQL("CREATE INDEX index_saved_annotations_updatedAt ON saved_annotations(updatedAt)")
    db.execSQL(
        "CREATE INDEX index_saved_annotations_bookId_chapterIndex_startTokenIndex " +
            "ON saved_annotations(bookId, chapterIndex, startTokenIndex)",
    )
}

private fun migrateReadingSessionsWithBookForeignKey(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE reading_sessions_new (
            id TEXT NOT NULL PRIMARY KEY,
            bookId TEXT NOT NULL,
            mode TEXT NOT NULL,
            startedAt INTEGER NOT NULL,
            endedAt INTEGER NOT NULL,
            activeDurationMs INTEGER NOT NULL,
            startChapterIndex INTEGER NOT NULL,
            startTokenIndex INTEGER NOT NULL,
            endChapterIndex INTEGER NOT NULL,
            endTokenIndex INTEGER NOT NULL,
            wordsRead INTEGER NOT NULL,
            effectiveWpm INTEGER NOT NULL,
            isWordCountEstimated INTEGER NOT NULL,
            FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        INSERT INTO reading_sessions_new
        SELECT id, bookId, mode, startedAt, endedAt, activeDurationMs,
               startChapterIndex, startTokenIndex, endChapterIndex, endTokenIndex,
               wordsRead, effectiveWpm, isWordCountEstimated
        FROM reading_sessions
        """.trimIndent(),
    )
    db.execSQL("DROP TABLE reading_sessions")
    db.execSQL("ALTER TABLE reading_sessions_new RENAME TO reading_sessions")
    db.execSQL("CREATE INDEX index_reading_sessions_bookId ON reading_sessions(bookId)")
    db.execSQL("CREATE INDEX index_reading_sessions_startedAt ON reading_sessions(startedAt)")
}

private fun createReadingSessionCheckpoints(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE reading_session_checkpoints (
            id TEXT NOT NULL PRIMARY KEY,
            sessionKey TEXT NOT NULL,
            logicalSessionId TEXT NOT NULL,
            bookId TEXT NOT NULL,
            mode TEXT NOT NULL,
            logicalStartedAt INTEGER NOT NULL,
            dayStartedAt INTEGER NOT NULL,
            startedAt INTEGER NOT NULL,
            endedAt INTEGER NOT NULL,
            activeDurationMs INTEGER NOT NULL,
            startChapterIndex INTEGER NOT NULL,
            startTokenIndex INTEGER NOT NULL,
            endChapterIndex INTEGER NOT NULL,
            endTokenIndex INTEGER NOT NULL,
            wordsRead INTEGER NOT NULL,
            isWordCountEstimated INTEGER NOT NULL,
            lastReaderWordIndex INTEGER,
            FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    db.execSQL(
        "CREATE INDEX index_reading_session_checkpoints_bookId " +
            "ON reading_session_checkpoints(bookId)",
    )
    db.execSQL(
        "CREATE INDEX index_reading_session_checkpoints_sessionKey " +
            "ON reading_session_checkpoints(sessionKey)",
    )
}

val MIGRATION_13_14: Migration =
    object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chapters ADD COLUMN wordCountVersion INTEGER NOT NULL DEFAULT 0")
        }
    }
