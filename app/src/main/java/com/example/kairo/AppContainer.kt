package com.example.kairo

import android.content.Context
import androidx.room.Room
import com.example.kairo.data.bookmarks.BookmarkRepository
import com.example.kairo.data.bookmarks.BookmarkRepositoryImpl
import com.example.kairo.core.rsvp.ComprehensionRsvpEngine
import com.example.kairo.core.rsvp.RsvpEngine
import com.example.kairo.data.books.BookRepository
import com.example.kairo.data.books.BookRepositoryImpl
import com.example.kairo.data.books.EpubBookParser
import com.example.kairo.data.books.MobiBookParser
import com.example.kairo.data.library.LibraryRepository
import com.example.kairo.data.library.LibraryRepositoryImpl
import com.example.kairo.data.local.KairoDatabase
import com.example.kairo.data.local.MIGRATION_1_2
import com.example.kairo.data.local.MIGRATION_2_3
import com.example.kairo.data.local.MIGRATION_3_4
import com.example.kairo.data.preferences.PreferencesRepository
import com.example.kairo.data.preferences.PreferencesRepositoryImpl
import com.example.kairo.data.reading.ReadingPositionRepository
import com.example.kairo.data.reading.ReadingPositionRepositoryImpl
import com.example.kairo.data.rsvp.RsvpFrameRepository
import com.example.kairo.data.rsvp.RsvpFrameRepositoryImpl
import com.example.kairo.data.seed.SampleSeeder
import com.example.kairo.data.token.TokenRepository
import com.example.kairo.data.token.TokenRepositoryImpl

class AppContainer(private val context: Context) {
    private val database: KairoDatabase = Room.databaseBuilder(
        context.applicationContext,
        KairoDatabase::class.java,
        "kairo.db"
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()

    private val parsers = listOf(EpubBookParser(), MobiBookParser())

    val bookRepository: BookRepository =
        BookRepositoryImpl(database.bookDao(), parsers, context.applicationContext)
    val tokenRepository: TokenRepository =
        TokenRepositoryImpl(bookRepository)
    val readingPositionRepository: ReadingPositionRepository =
        ReadingPositionRepositoryImpl(database.readingPositionDao())
    val bookmarkRepository: BookmarkRepository =
        BookmarkRepositoryImpl(database.bookmarkDao())
    val preferencesRepository: PreferencesRepository = PreferencesRepositoryImpl(context)
    val libraryRepository: LibraryRepository =
        LibraryRepositoryImpl(
            bookRepository,
            database.bookDao(),
            database.readingPositionDao(),
            database.bookmarkDao(),
            context.applicationContext
        )
    val rsvpEngine: RsvpEngine = ComprehensionRsvpEngine()
    val rsvpFrameRepository: RsvpFrameRepository =
        RsvpFrameRepositoryImpl(tokenRepository, rsvpEngine)
    val sampleSeeder = SampleSeeder(database.bookDao())
}
