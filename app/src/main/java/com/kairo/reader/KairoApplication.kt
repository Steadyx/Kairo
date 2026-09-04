package com.kairo.reader

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.kairo.reader.core.dispatchers.DefaultDispatcherProvider
import com.kairo.reader.core.rsvp.ComprehensionRsvpEngine
import com.kairo.reader.core.rsvp.RsvpEngine
import com.kairo.reader.data.annotations.SavedAnnotationRepository
import com.kairo.reader.data.annotations.SavedAnnotationRepositoryImpl
import com.kairo.reader.data.bookmarks.BookmarkRepository
import com.kairo.reader.data.bookmarks.BookmarkRepositoryImpl
import com.kairo.reader.data.books.BookRepository
import com.kairo.reader.data.books.BookRepositoryImpl
import com.kairo.reader.data.books.DocxBookParser
import com.kairo.reader.data.books.EpubBookParser
import com.kairo.reader.data.books.Fb2BookParser
import com.kairo.reader.data.books.MobiBookParser
import com.kairo.reader.data.books.PdfBookParser
import com.kairo.reader.data.books.TextFileBookParser
import com.kairo.reader.data.books.WebArticleExtractor
import com.kairo.reader.data.export.NoteExportService
import com.kairo.reader.data.export.NoteExporter
import com.kairo.reader.data.library.LibraryRepository
import com.kairo.reader.data.library.LibraryRepositoryImpl
import com.kairo.reader.data.local.KairoDatabase
import com.kairo.reader.data.local.MIGRATION_10_11
import com.kairo.reader.data.local.MIGRATION_11_12
import com.kairo.reader.data.local.MIGRATION_12_13
import com.kairo.reader.data.local.MIGRATION_1_2
import com.kairo.reader.data.local.MIGRATION_2_3
import com.kairo.reader.data.local.MIGRATION_3_4
import com.kairo.reader.data.local.MIGRATION_4_5
import com.kairo.reader.data.local.MIGRATION_5_6
import com.kairo.reader.data.local.MIGRATION_6_7
import com.kairo.reader.data.local.MIGRATION_7_8
import com.kairo.reader.data.local.MIGRATION_8_9
import com.kairo.reader.data.local.MIGRATION_9_10
import com.kairo.reader.data.preferences.PreferencesRepository
import com.kairo.reader.data.preferences.PreferencesRepositoryImpl
import com.kairo.reader.data.reading.ReadingPositionRepository
import com.kairo.reader.data.reading.ReadingPositionRepositoryImpl
import com.kairo.reader.data.rsvp.RsvpFrameRepository
import com.kairo.reader.data.rsvp.RsvpFrameRepositoryImpl
import com.kairo.reader.data.search.LibrarySearchRepository
import com.kairo.reader.data.search.LibrarySearchRepositoryImpl
import com.kairo.reader.data.seed.SampleSeeder
import com.kairo.reader.data.sessions.ReadingSessionCoordinator
import com.kairo.reader.data.sessions.ReadingSessionRepository
import com.kairo.reader.data.sessions.ReadingSessionRepositoryImpl
import com.kairo.reader.data.sessions.SystemReadingSessionClock
import com.kairo.reader.data.token.TokenRepository
import com.kairo.reader.data.token.TokenRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KairoApplication : Application() {
    val dispatcherProvider = DefaultDispatcherProvider()
    private val applicationScope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)

    private lateinit var database: KairoDatabase
    lateinit var bookRepository: BookRepository
        private set
    lateinit var tokenRepository: TokenRepository
        private set
    lateinit var readingPositionRepository: ReadingPositionRepository
        private set
    lateinit var bookmarkRepository: BookmarkRepository
        private set
    lateinit var savedAnnotationRepository: SavedAnnotationRepository
        private set
    lateinit var noteExportService: NoteExporter
        private set
    lateinit var readingSessionRepository: ReadingSessionRepository
        private set
    lateinit var readingSessionCoordinator: ReadingSessionCoordinator
        private set
    lateinit var searchRepository: LibrarySearchRepository
        private set
    lateinit var preferencesRepository: PreferencesRepository
        private set
    lateinit var libraryRepository: LibraryRepository
        private set
    val rsvpEngine: RsvpEngine = ComprehensionRsvpEngine()
    lateinit var rsvpFrameRepository: RsvpFrameRepository
        private set
    lateinit var sampleSeeder: SampleSeeder
        private set

    override fun onCreate() {
        super.onCreate()
        database =
            Room
                .databaseBuilder(
                    applicationContext,
                    KairoDatabase::class.java,
                    "kairo.db",
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                )
                .build()

        val parsers =
            listOf(
                EpubBookParser(dispatcherProvider),
                MobiBookParser(dispatcherProvider),
                TextFileBookParser(dispatcherProvider),
                Fb2BookParser(dispatcherProvider),
                DocxBookParser(dispatcherProvider),
                PdfBookParser(dispatcherProvider),
            )

        bookRepository =
            BookRepositoryImpl(
                database.bookDao(),
                database.epubNavigationDao(),
                parsers,
                WebArticleExtractor(dispatcherProvider),
                appContext = applicationContext,
                dispatcherProvider = dispatcherProvider,
            )
        tokenRepository = TokenRepositoryImpl(bookRepository, dispatcherProvider)
        readingPositionRepository = ReadingPositionRepositoryImpl(database.readingPositionDao())
        bookmarkRepository = BookmarkRepositoryImpl(database.bookmarkDao())
        savedAnnotationRepository =
            SavedAnnotationRepositoryImpl(database.savedAnnotationDao())
        noteExportService =
            NoteExportService(
                context = applicationContext,
                annotationRepository = savedAnnotationRepository,
                bookRepository = bookRepository,
                dispatcherProvider = dispatcherProvider,
            )
        readingSessionRepository =
            ReadingSessionRepositoryImpl(database.readingSessionDao())
        readingSessionCoordinator =
            ReadingSessionCoordinator(
                scope = applicationScope,
                repository = readingSessionRepository,
                clock = SystemReadingSessionClock(),
                onError = { failure ->
                    Log.e(READING_SESSION_LOG_TAG, "Reading-session command failed", failure)
                },
            )
        searchRepository =
            LibrarySearchRepositoryImpl(
                searchDao = database.searchDao(),
                annotationDao = database.savedAnnotationDao(),
                dispatcherProvider = dispatcherProvider,
            )
        preferencesRepository = PreferencesRepositoryImpl(this)
        rsvpFrameRepository = RsvpFrameRepositoryImpl(tokenRepository, rsvpEngine, dispatcherProvider)
        libraryRepository =
            LibraryRepositoryImpl(
                bookRepository,
                database,
                database.bookDao(),
                database.readingPositionDao(),
                database.bookmarkDao(),
                database.savedAnnotationDao(),
                database.readingSessionDao(),
                invalidateBookCaches = { bookId ->
                    tokenRepository.invalidateBook(bookId)
                    rsvpFrameRepository.invalidateBook(bookId)
                },
                invalidateAllCaches = {
                    tokenRepository.clearCache()
                    rsvpFrameRepository.clearCache()
                },
                appContext = applicationContext,
                dispatcherProvider = dispatcherProvider,
            )
        sampleSeeder = SampleSeeder(database.bookDao())

        applicationScope.launch { sampleSeeder.seedIfEmpty() }
    }
}

private const val READING_SESSION_LOG_TAG = "ReadingSession"
