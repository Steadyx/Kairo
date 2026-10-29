package com.kairo.reader.data.seed

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.kairo.reader.data.local.BookDao
import com.kairo.reader.data.local.toEntity
import com.kairo.reader.sample.SampleBooks

// The migration marker must be persisted before changing the database or completing the seed.
@SuppressLint("UseKtx")
class SampleSeeder(private val bookDao: BookDao, private val preferences: SharedPreferences,) {
    suspend fun seedIfEmpty() {
        if (preferences.getBoolean(SEED_COMPLETE, true)) return
        val existing = bookDao.peekBook()
        if (existing == null) {
            // Seed the starter book directly so first-run onboarding has real content to use.
            val sample = SampleBooks.defaultSample()
            bookDao.insertBook(
                book = sample.toEntity(),
                chapters = sample.chapters.map { it.toEntity(sample.id) },
                tableOfContentsEntries =
                sample.tableOfContents.mapIndexed { index, entry ->
                    entry.toEntity(sample.id, index)
                },
            )
        }
        check(preferences.edit().putBoolean(SEED_COMPLETE, true).commit())
    }

    companion object {
        private const val SEED_COMPLETE = "seed_complete"

        fun prepare(preferences: SharedPreferences, databaseExisted: Boolean) {
            if (!preferences.contains(SEED_COMPLETE)) {
                check(preferences.edit().putBoolean(SEED_COMPLETE, databaseExisted).commit())
            }
        }
    }
}
