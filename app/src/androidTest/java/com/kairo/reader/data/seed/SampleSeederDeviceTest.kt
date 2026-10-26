package com.kairo.reader.data.seed

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.reader.data.local.KairoDatabase
import com.kairo.reader.sample.SampleBooks
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SampleSeederDeviceTest {
    @Test
    fun firstRunSeedsOnceAndDeletionStaysDeleted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferenceName = "seed-test-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        val database = Room.inMemoryDatabaseBuilder(context, KairoDatabase::class.java).build()
        try {
            SampleSeeder.prepare(preferences, databaseExisted = false)
            // A restart after the database is created must not discard a pending first seed.
            SampleSeeder.prepare(preferences, databaseExisted = true)
            val seeder = SampleSeeder(database.bookDao(), preferences)
            seeder.seedIfEmpty()
            assertNotNull(database.bookDao().peekBook())
            val id = SampleBooks.STARTER_BOOK_ID
            database.bookDao().deleteChaptersForBook(id)
            database.bookDao().deleteTableOfContentsForBook(id)
            database.bookDao().deleteBook(id)
            SampleSeeder.prepare(preferences, databaseExisted = true)
            seeder.seedIfEmpty()
            assertNull(database.bookDao().peekBook())
        } finally {
            database.close()
            context.deleteSharedPreferences(preferenceName)
        }
    }

    @Test
    fun existingEmptyLibraryIsNotReseededOnUpgrade() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferenceName = "seed-test-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        val database = Room.inMemoryDatabaseBuilder(context, KairoDatabase::class.java).build()
        try {
            SampleSeeder.prepare(preferences, databaseExisted = true)
            SampleSeeder(database.bookDao(), preferences).seedIfEmpty()
            assertNull(database.bookDao().peekBook())
        } finally {
            database.close()
            context.deleteSharedPreferences(preferenceName)
        }
    }
}
