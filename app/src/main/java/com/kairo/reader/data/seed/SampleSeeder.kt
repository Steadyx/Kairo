package com.kairo.reader.data.seed

import com.kairo.reader.data.local.BookDao
import com.kairo.reader.data.local.toEntity
import com.kairo.reader.sample.SampleBooks

class SampleSeeder(private val bookDao: BookDao,) {
    suspend fun seedIfEmpty() {
        val existing = bookDao.peekBook()
        if (existing != null) return
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
}
