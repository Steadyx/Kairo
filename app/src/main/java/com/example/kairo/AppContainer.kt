package com.example.kairo

import android.content.Context
import androidx.room.Room
import com.example.kairo.core.rsvp.DefaultRsvpEngine
import com.example.kairo.core.rsvp.RsvpEngine
import com.example.kairo.core.tokenization.Tokenizer
import com.example.kairo.data.books.BookRepository
import com.example.kairo.data.books.BookRepositoryImpl
import com.example.kairo.data.books.EpubBookParser
import com.example.kairo.data.books.MobiBookParser
import com.example.kairo.data.library.LibraryRepository
import com.example.kairo.data.library.LibraryRepositoryImpl
import com.example.kairo.data.local.KairoDatabase
import com.example.kairo.data.preferences.PreferencesRepository
import com.example.kairo.data.preferences.PreferencesRepositoryImpl
import com.example.kairo.data.reading.ReadingPositionRepository
import com.example.kairo.data.reading.ReadingPositionRepositoryImpl
import com.example.kairo.data.seed.SampleSeeder
import com.example.kairo.data.token.TokenRepository
import com.example.kairo.data.token.TokenRepositoryImpl

class AppContainer(private val context: Context) {
    private val database: KairoDatabase = Room.databaseBuilder(
        context.applicationContext,
        KairoDatabase::class.java,
        "kairo.db"
    ).build()

    private val parsers = listOf(EpubBookParser(), MobiBookParser())
    private val tokenizer = Tokenizer()

    val bookRepository: BookRepository =
        BookRepositoryImpl(database.bookDao(), parsers, context.applicationContext)
    val tokenRepository: TokenRepository =
        TokenRepositoryImpl(bookRepository, tokenizer)
    val readingPositionRepository: ReadingPositionRepository =
        ReadingPositionRepositoryImpl(database.readingPositionDao())
    val preferencesRepository: PreferencesRepository = PreferencesRepositoryImpl(context)
    val libraryRepository: LibraryRepository =
        LibraryRepositoryImpl(bookRepository, database.bookDao(), database.readingPositionDao())
    val rsvpEngine: RsvpEngine = DefaultRsvpEngine()
    val sampleSeeder = SampleSeeder(database.bookDao())
}
