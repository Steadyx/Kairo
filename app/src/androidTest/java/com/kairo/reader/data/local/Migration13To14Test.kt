package com.kairo.reader.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration13To14Test {
    @Test
    fun migrationPreservesContentAndRepairMarkerSurvivesReopening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-13-14-${UUID.randomUUID()}.db"
        fun helper(version: Int): SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """CREATE TABLE chapters (
                            bookId TEXT NOT NULL, `index` INTEGER NOT NULL, title TEXT,
                            htmlContent TEXT NOT NULL, plainText TEXT NOT NULL,
                            imagePaths TEXT NOT NULL, wordCount INTEGER NOT NULL,
                            PRIMARY KEY(bookId, `index`))"""
                        )
                        db.execSQL("INSERT INTO chapters VALUES ('book', 0, 'Title', '<p>中文</p>', '中文', '', 1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        MIGRATION_13_14.migrate(db)
                    }
                }
            ).build()
        )
        try {
            helper(13).use { it.writableDatabase }
            helper(14).use { helper ->
                val db = helper.writableDatabase
                db.query("SELECT plainText, wordCount, wordCountVersion FROM chapters").use {
                    check(it.moveToFirst())
                    assertEquals("中文", it.getString(0))
                    assertEquals(1, it.getInt(1))
                    assertEquals(0, it.getInt(2))
                }
                db.execSQL("UPDATE chapters SET wordCount = 2, wordCountVersion = 1")
            }
            helper(14).use { helper ->
                helper.writableDatabase.query("SELECT wordCount, wordCountVersion FROM chapters").use {
                    check(it.moveToFirst())
                    assertEquals(2, it.getInt(0))
                    assertEquals(1, it.getInt(1))
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
