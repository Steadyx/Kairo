package com.kairo.reader.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.LibrarySearchResult
import com.kairo.reader.data.books.EpubReaderNavigationContent
import com.kairo.reader.data.search.LibrarySearchRepositoryImpl
import com.kairo.reader.data.search.toSqlLikePattern
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceIntegrityTest {
    private lateinit var database: KairoDatabase

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    KairoDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun literalLikeCharactersDoNotActAsWildcards() =
        runBlocking {
            insertBook()
            val annotations =
                listOf(
                    annotation("percent", "100% complete"),
                    annotation("percent-noise", "100x complete"),
                    annotation("underscore", "under_score"),
                    annotation("underscore-noise", "underXscore"),
                    annotation("slash", "path\\name"),
                    annotation("slash-noise", "pathXname"),
                )
            annotations.forEach { assertTrue(database.savedAnnotationDao().upsert(it)) }

            assertEquals(
                listOf("percent"),
                searchSaved("%").map { it.annotation.id },
            )
            assertEquals(
                listOf("underscore"),
                searchSaved("_").map { it.annotation.id },
            )
            assertEquals(
                listOf("slash"),
                searchSaved("\\").map { it.annotation.id },
            )
        }

    @Test
    fun reimportPreservesDependentRowsAndCompletionStateWithoutLoadingCoverBlobs() =
        runBlocking {
            insertBook(coverImage = ByteArray(32) { 7 })
            database.bookDao().setCompleted(BOOK_ID, true)
            assertTrue(database.savedAnnotationDao().upsert(annotation("saved", "passage")))
            assertTrue(database.readingSessionDao().insert(session("session")))

            insertBook(title = "Updated title", coverImage = ByteArray(32) { 9 })

            assertEquals("Updated title", database.bookDao().getBook(BOOK_ID)?.title)
            assertTrue(database.bookDao().getBook(BOOK_ID)?.isCompleted == true)
            assertEquals(1, rowCount("saved_annotations"))
            assertEquals(1, rowCount("reading_sessions"))
            assertNull(searchSaved("passage").single().book.coverImage)
        }

    @Test
    fun markerlessNavigationCandidatesAreBroadBoundedAndSkipCanonicalMarkup() =
        runBlocking {
            insertLegacyNavigationBook()

            assertEquals(
                listOf(0),
                database.epubNavigationDao().getMarkerlessNavigationCandidates(
                    bookId = BOOK_ID,
                    canonicalMarker = EpubReaderNavigationContent.MARKER,
                    maxHtmlCharacters = MAX_NAVIGATION_HTML_CHARACTERS,
                    limit = 17,
                ).map { candidate -> candidate.chapterIndex },
            )

            insertBookWithHtml(
                "<html><body><nav ${EpubReaderNavigationContent.MARKER} epub:type=\"toc\">" +
                    "<ol><li><a href=\"chapter.xhtml\">Chapter</a></li></ol></nav></body></html>",
            )
            assertTrue(
                database.epubNavigationDao().getMarkerlessNavigationCandidates(
                    bookId = BOOK_ID,
                    canonicalMarker = EpubReaderNavigationContent.MARKER,
                    maxHtmlCharacters = MAX_NAVIGATION_HTML_CHARACTERS,
                    limit = 17,
                ).isEmpty(),
            )

            insertBookWithHtml(
                "<html><body><script>const fake = '<nav>text</nav>';</script>" +
                    "<nav><ol><li><a href=\"next.xhtml\">Next</a></li></ol></nav></body></html>",
            )
            assertEquals(
                listOf(0),
                database.epubNavigationDao().getMarkerlessNavigationCandidates(
                    bookId = BOOK_ID,
                    canonicalMarker = EpubReaderNavigationContent.MARKER,
                    maxHtmlCharacters = MAX_NAVIGATION_HTML_CHARACTERS,
                    limit = 17,
                ).map { candidate -> candidate.chapterIndex },
            )
        }

    @Test
    fun canonicalNavigationUpdatePreservesCoordinatesAndResetsActivePosition() =
        runBlocking {
            insertLegacyNavigationBook()
            database.bookDao().setCompleted(BOOK_ID, true)
            database.readingPositionDao().savePosition(
                ReadingPositionEntity(
                    bookId = BOOK_ID,
                    chapterIndex = 0,
                    tokenIndex = 9,
                    wordIndex = 7,
                    rsvpResumeCursor = 4,
                ),
            )
            val beforeBook = requireNotNull(database.bookDao().getBook(BOOK_ID))
            val beforeToc = database.bookDao().getTableOfContentsEntries(BOOK_ID)
            val beforeStory = requireNotNull(database.bookDao().getChapter(BOOK_ID, 1))
            val candidate = legacyNavigationCandidate()

            val updated = canonicalizeNavigation(candidate)

            assertTrue(updated)
            val navigation = requireNotNull(database.bookDao().getChapter(BOOK_ID, 0))
            assertEquals(0, navigation.index)
            assertEquals("Contents", navigation.title)
            assertEquals(CANONICAL_NAVIGATION_HTML, navigation.htmlContent)
            assertEquals(CANONICAL_NAVIGATION_TEXT, navigation.plainText)
            assertEquals(CANONICAL_NAVIGATION_WORD_COUNT, navigation.wordCount)
            assertEquals(LEGACY_NAVIGATION_IMAGE_PATHS, navigation.imagePaths)
            assertEquals(beforeBook, database.bookDao().getBook(BOOK_ID))
            assertEquals(beforeToc, database.bookDao().getTableOfContentsEntries(BOOK_ID))
            assertEquals(beforeStory, database.bookDao().getChapter(BOOK_ID, 1))
            assertEquals(
                ReadingPositionEntity(
                    bookId = BOOK_ID,
                    chapterIndex = 0,
                    tokenIndex = 0,
                    wordIndex = 0,
                    rsvpResumeCursor = -1,
                ),
                database.readingPositionDao().getPosition(BOOK_ID),
            )
        }

    @Test
    fun canonicalNavigationUpdateSkipsBookmarkAndSavedAnnotationCoordinates() =
        runBlocking {
            insertLegacyNavigationBook()
            database.bookmarkDao().upsert(
                BookmarkEntity(
                    id = "navigation-bookmark",
                    bookId = BOOK_ID,
                    chapterIndex = 0,
                    tokenIndex = 2,
                    previewText = "Chapter",
                    createdAt = 1L,
                ),
            )

            assertFalse(canonicalizeNavigation(legacyNavigationCandidate()))
            assertEquals(LEGACY_NAVIGATION_HTML, database.bookDao().getChapter(BOOK_ID, 0)?.htmlContent)

            database.bookmarkDao().delete("navigation-bookmark")
            assertTrue(database.savedAnnotationDao().upsert(annotation("navigation-note", "Chapter")))

            assertFalse(canonicalizeNavigation(legacyNavigationCandidate()))
            assertEquals(LEGACY_NAVIGATION_HTML, database.bookDao().getChapter(BOOK_ID, 0)?.htmlContent)
        }

    @Test
    fun canonicalNavigationUpdateSkipsTableOfContentsCoordinates() =
        runBlocking {
            insertLegacyNavigationBook()
            database.epubNavigationDao().insertTableOfContentsEntries(
                listOf(
                    TableOfContentsEntryEntity(
                        bookId = BOOK_ID,
                        entryIndex = 1,
                        label = "Contents",
                        depth = 0,
                        chapterIndex = 0,
                        characterOffset = 2,
                    ),
                ),
            )

            assertCanonicalNavigationBlocked()
        }

    @Test
    fun canonicalNavigationUpdateSkipsReadingSessionStartCoordinates() =
        runBlocking {
            insertLegacyNavigationBook()
            assertTrue(
                database.readingSessionDao().insert(
                    session(
                        id = "starts-in-navigation",
                        startChapterIndex = 0,
                        endChapterIndex = 1,
                    ),
                ),
            )

            assertCanonicalNavigationBlocked()
        }

    @Test
    fun canonicalNavigationUpdateSkipsReadingSessionEndCoordinates() =
        runBlocking {
            insertLegacyNavigationBook()
            assertTrue(
                database.readingSessionDao().insert(
                    session(
                        id = "ends-in-navigation",
                        startChapterIndex = 1,
                        endChapterIndex = 0,
                    ),
                ),
            )

            assertCanonicalNavigationBlocked()
        }

    @Test
    fun canonicalNavigationUpdateSkipsCheckpointStartAndReaderEndCoordinates() =
        runBlocking {
            insertLegacyNavigationBook()
            assertTrue(
                database.readingSessionDao().replaceCheckpoints(
                    SESSION_KEY,
                    listOf(
                        checkpoint(
                            id = "starts-in-navigation",
                            startChapterIndex = 0,
                            endChapterIndex = 1,
                            lastReaderWordIndex = null,
                        ),
                    ),
                ),
            )
            assertCanonicalNavigationBlocked()

            assertTrue(
                database.readingSessionDao().replaceCheckpoints(
                    SESSION_KEY,
                    listOf(
                        checkpoint(
                            id = "reader-progress-in-navigation",
                            startChapterIndex = 1,
                            endChapterIndex = 0,
                            lastReaderWordIndex = 7,
                        ),
                    ),
                ),
            )
            assertCanonicalNavigationBlocked()
        }

    @Test
    fun canonicalNavigationUpdateRevalidatesExactTargetHtml() =
        runBlocking {
            insertLegacyNavigationBook()
            val candidate = legacyNavigationCandidate()
            assertEquals(
                1,
                database.epubNavigationDao().updateCanonicalNavigationChapter(
                    bookId = BOOK_ID,
                    chapterIndex = candidate.chapterIndex,
                    expectedHtmlContent = candidate.htmlContent,
                    htmlContent = candidate.htmlContent + " ",
                    plainText = "changed",
                    wordCount = 1,
                ),
            )

            assertFalse(canonicalizeNavigation(candidate))
            assertEquals(LEGACY_NAVIGATION_HTML + " ", database.bookDao().getChapter(BOOK_ID, 0)?.htmlContent)
        }

    @Test
    fun tocOnlyReplacementPreservesAllBookCoordinatesAndDependentRows() =
        runBlocking {
            insertLegacyNavigationBook()
            database.bookmarkDao().upsert(
                BookmarkEntity(
                    id = "bookmark",
                    bookId = BOOK_ID,
                    chapterIndex = 0,
                    tokenIndex = 3,
                    previewText = "Chapter One",
                    createdAt = 4L,
                ),
            )
            assertTrue(database.savedAnnotationDao().upsert(annotation("annotation", "Chapter One")))
            database.readingPositionDao().savePosition(
                ReadingPositionEntity(BOOK_ID, chapterIndex = 1, tokenIndex = 7, wordIndex = 6),
            )
            assertTrue(database.readingSessionDao().insert(session("session")))
            assertTrue(database.readingSessionDao().replaceCheckpoints(SESSION_KEY, listOf(checkpoint())))

            val beforeBook = requireNotNull(database.bookDao().getBook(BOOK_ID))
            val beforeChapters = database.bookDao().getChaptersWithContent(BOOK_ID)
            val beforeBookmarks = database.bookmarkDao().observeForBook(BOOK_ID).first()
            val beforeAnnotations = database.savedAnnotationDao().observeForBook(BOOK_ID).first()
            val beforePosition = database.readingPositionDao().getPosition(BOOK_ID)
            val beforeSessions = database.readingSessionDao().observeWithBook().first().map { it.session }
            val beforeCheckpoints = database.readingSessionDao().getAllCheckpoints()
            val coordinates = database.epubNavigationDao().getChapterCoordinates(BOOK_ID)
            val replacement =
                listOf(
                    TableOfContentsEntryEntity(
                        bookId = BOOK_ID,
                        entryIndex = 0,
                        label = "Part One",
                        depth = 0,
                        chapterIndex = null,
                        characterOffset = null,
                    ),
                    TableOfContentsEntryEntity(
                        bookId = BOOK_ID,
                        entryIndex = 1,
                        label = "Chapter One",
                        depth = 1,
                        chapterIndex = 1,
                        characterOffset = 2,
                    ),
                )

            assertTrue(
                database.epubNavigationDao().replaceTableOfContentsIfCoordinatesMatch(
                    bookId = BOOK_ID,
                    expectedCoordinates = coordinates,
                    entries = replacement,
                ),
            )

            assertEquals(replacement, database.bookDao().getTableOfContentsEntries(BOOK_ID))
            assertEquals(beforeBook, database.bookDao().getBook(BOOK_ID))
            assertEquals(beforeChapters, database.bookDao().getChaptersWithContent(BOOK_ID))
            assertEquals(beforeBookmarks, database.bookmarkDao().observeForBook(BOOK_ID).first())
            assertEquals(beforeAnnotations, database.savedAnnotationDao().observeForBook(BOOK_ID).first())
            assertEquals(beforePosition, database.readingPositionDao().getPosition(BOOK_ID))
            assertEquals(beforeSessions, database.readingSessionDao().observeWithBook().first().map { it.session })
            assertEquals(beforeCheckpoints, database.readingSessionDao().getAllCheckpoints())
        }

    @Test
    fun tocOnlyReplacementRejectsCoordinateChangesWithoutDeletingExistingEntries() =
        runBlocking {
            insertLegacyNavigationBook()
            val before = database.bookDao().getTableOfContentsEntries(BOOK_ID)
            val staleCoordinates =
                database.epubNavigationDao().getChapterCoordinates(BOOK_ID).map { coordinate ->
                    if (coordinate.chapterIndex == 1) coordinate.copy(plainText = "changed") else coordinate
                }

            assertFalse(
                database.epubNavigationDao().replaceTableOfContentsIfCoordinatesMatch(
                    bookId = BOOK_ID,
                    expectedCoordinates = staleCoordinates,
                    entries = emptyList(),
                ),
            )
            assertEquals(before, database.bookDao().getTableOfContentsEntries(BOOK_ID))
        }

    @Test
    fun lateWritesRequireAParentAndDeletingBookCascadesNewChildren() =
        runBlocking {
            assertFalse(database.savedAnnotationDao().upsert(annotation("orphan", "passage")))
            assertFalse(database.readingSessionDao().insert(session("orphan-session")))

            insertBook()
            assertTrue(database.savedAnnotationDao().upsert(annotation("saved", "passage")))
            assertTrue(database.readingSessionDao().insert(session("session")))
            assertTrue(
                database.readingSessionDao().replaceCheckpoints(
                    sessionKey = "reader:$BOOK_ID",
                    entities = listOf(checkpoint()),
                )
            )

            database.bookDao().deleteBook(BOOK_ID)

            assertEquals(0, rowCount("saved_annotations"))
            assertEquals(0, rowCount("reading_sessions"))
            assertEquals(0, rowCount("reading_session_checkpoints"))
        }

    @Test
    fun replaceCheckpointsRejectsInvalidBatchesWithoutDeletingExistingData() =
        runBlocking {
            insertBook()
            insertBook(bookId = OTHER_BOOK_ID, title = "Other book")
            val sessionDao = database.readingSessionDao()
            val existing = checkpoint(id = "existing")
            assertTrue(sessionDao.replaceCheckpoints(SESSION_KEY, listOf(existing)))

            val invalidBatches =
                listOf(
                    listOf(checkpoint(id = "wrong-key", sessionKey = "reader:wrong")),
                    listOf(
                        checkpoint(id = "book-a"),
                        checkpoint(
                            id = "book-b",
                            bookId = OTHER_BOOK_ID,
                            sessionKey = SESSION_KEY,
                        ),
                    ),
                    listOf(
                        checkpoint(id = "mode-a"),
                        checkpoint(id = "mode-b", mode = "RSVP"),
                    ),
                    listOf(
                        checkpoint(
                            id = "wrong-book-a",
                            bookId = OTHER_BOOK_ID,
                            sessionKey = SESSION_KEY,
                        ),
                        checkpoint(
                            id = "wrong-book-b",
                            bookId = OTHER_BOOK_ID,
                            sessionKey = SESSION_KEY,
                        ),
                    ),
                    listOf(
                        checkpoint(id = "wrong-mode-a", mode = "RSVP"),
                        checkpoint(id = "wrong-mode-b", mode = "RSVP"),
                    ),
                )

            invalidBatches.forEach { invalidBatch ->
                assertFalse(sessionDao.replaceCheckpoints(SESSION_KEY, invalidBatch))
                assertEquals(listOf(existing), sessionDao.getAllCheckpoints())
            }
        }

    @Test
    fun finalizeCheckpointsRejectsInvalidBatchesWithoutMutatingData() =
        runBlocking {
            insertBook()
            insertBook(bookId = OTHER_BOOK_ID, title = "Other book")
            val sessionDao = database.readingSessionDao()
            val existingCheckpoint = checkpoint(id = "existing")
            assertTrue(sessionDao.replaceCheckpoints(SESSION_KEY, listOf(existingCheckpoint)))
            assertTrue(sessionDao.insert(session("existing-session")))

            val invalidBatches =
                listOf(
                    listOf(
                        session("book-a"),
                        session("book-b", bookId = OTHER_BOOK_ID),
                    ),
                    listOf(
                        session("mode-a"),
                        session("mode-b", mode = "RSVP"),
                    ),
                    listOf(
                        session("wrong-book-a", bookId = OTHER_BOOK_ID),
                        session("wrong-book-b", bookId = OTHER_BOOK_ID),
                    ),
                    listOf(
                        session("wrong-mode-a", mode = "RSVP"),
                        session("wrong-mode-b", mode = "RSVP"),
                    ),
                )

            invalidBatches.forEach { invalidBatch ->
                assertFalse(sessionDao.finalizeCheckpoints(SESSION_KEY, invalidBatch))
                assertEquals(1, rowCount("reading_sessions"))
                assertEquals(listOf(existingCheckpoint), sessionDao.getAllCheckpoints())
            }
        }

    @Test
    fun finalizeCheckpointsPreservesDataWhenTheBookIsMissing() =
        runBlocking {
            insertBook()
            val sessionDao = database.readingSessionDao()
            val existingSession = session("existing-session")
            val existingCheckpoint = checkpoint(id = "existing")
            assertTrue(sessionDao.insert(existingSession))
            assertTrue(sessionDao.replaceCheckpoints(SESSION_KEY, listOf(existingCheckpoint)))
            assertTrue(sessionDao.getCheckpoints(MISSING_BOOK_SESSION_KEY).isEmpty())

            assertFalse(
                sessionDao.finalizeCheckpoints(
                    MISSING_BOOK_SESSION_KEY,
                    listOf(session("missing-book-session", bookId = MISSING_BOOK_ID)),
                )
            )
            assertEquals(1, rowCount("reading_sessions"))
            assertEquals(listOf(existingCheckpoint), sessionDao.getAllCheckpoints())
        }

    @Test
    fun emptyFinalizationStillClearsValidCheckpoints() =
        runBlocking {
            insertBook()
            val sessionDao = database.readingSessionDao()
            assertTrue(sessionDao.replaceCheckpoints(SESSION_KEY, listOf(checkpoint())))

            assertTrue(sessionDao.finalizeCheckpoints(SESSION_KEY, emptyList()))

            assertTrue(sessionDao.getAllCheckpoints().isEmpty())
        }

    @Test
    fun passageSearchFindsLateOffsetsAndReturnsOnlyABoundedSnippet() =
        runBlocking {
            val prefix = "x".repeat(275_000)
            val plainText = "$prefix needle followed by a short ending"
            insertBook(plainText = plainText)

            val match = searchPassages("needle").single()

            assertEquals(prefix.length + 1, match.matchStartCodePointOffset)
            assertTrue(match.snippet.contains("needle"))
            assertTrue(match.snippet.length <= MAX_SEARCH_SNIPPET_LENGTH)
        }

    @Test
    fun passageSearchReportsOffsetsAndLengthsInUnicodeCodePoints() =
        runBlocking {
            val plainText = "😀😀😀 abc needle and 😀query"
            insertBook(plainText = plainText)

            val needle = searchPassages("needle").single()
            val supplementaryQuery = searchPassages("😀query").single()

            assertEquals(8, needle.matchStartCodePointOffset)
            assertEquals(11, plainText.indexOf("needle"))
            assertEquals(6, needle.matchLengthCodePoints)
            assertEquals(19, supplementaryQuery.matchStartCodePointOffset)
            assertEquals(6, supplementaryQuery.matchLengthCodePoints)
        }

    @Test
    fun passageSearchUsesOriginalOffsetsAfterExpandingLowercaseCharacter() =
        runBlocking {
            val plainText = "İ before needle and after"
            insertBook(plainText = plainText)

            val match = searchPassages("needle").single()

            assertEquals(9, match.matchStartCodePointOffset)
            assertEquals(6, match.matchLengthCodePoints)
            assertEquals(plainText, match.snippet)
        }

    @Test
    fun passageSearchMatchesGreekFinalSigmaUsingOriginalText() =
        runBlocking {
            val plainText = "Before ΟΣ after"
            insertBook(plainText = plainText)

            val match = searchPassages("οσ").single()

            assertEquals(7, match.matchStartCodePointOffset)
            assertEquals(2, match.matchLengthCodePoints)
            assertEquals(plainText, match.snippet)
        }

    @Test
    fun passageSearchPagesEachBookInStableGlobalOrder() =
        runBlocking {
            insertBookWithChapters(
                bookId = SAME_TITLE_BOOK_B,
                title = "Same Title",
                chapterTexts = (0 until 8).map { "no match $it" } + "needle in b",
            )
            insertBookWithChapters(
                bookId = SAME_TITLE_BOOK_A,
                title = "same title",
                chapterTexts = (0 until 8).map { "no match $it" } + "needle in a",
            )

            val matches = searchPassages("needle", bookId = null)

            assertEquals(
                listOf(SAME_TITLE_BOOK_A, SAME_TITLE_BOOK_B),
                matches.map { it.bookId.value },
            )
            assertEquals(listOf(8, 8), matches.map { it.chapterIndex })
        }

    private suspend fun insertBook(
        bookId: String = BOOK_ID,
        title: String = "Book",
        coverImage: ByteArray? = null,
        plainText: String = "Chapter",
    ) =
        insertBookWithChapters(
            bookId = bookId,
            title = title,
            coverImage = coverImage,
            chapterTexts = listOf(plainText),
        )

    private suspend fun insertBookWithChapters(
        bookId: String,
        title: String,
        chapterTexts: List<String>,
        coverImage: ByteArray? = null,
    ) {
        database.bookDao().insertBook(
            book =
            BookEntity(
                id = bookId,
                title = title,
                authors = listOf("Author"),
                languageTag = "en",
                coverImage = coverImage,
            ),
            chapters =
            chapterTexts.mapIndexed { chapterIndex, chapterText ->
                ChapterEntity(
                    bookId = bookId,
                    index = chapterIndex,
                    title = "Chapter $chapterIndex",
                    htmlContent = "<p>Chapter $chapterIndex</p>",
                    plainText = chapterText,
                )
            },
            tableOfContentsEntries = emptyList(),
        )
    }

    private suspend fun insertBookWithHtml(htmlContent: String) {
        database.bookDao().insertBook(
            book =
            BookEntity(
                id = BOOK_ID,
                title = "Book",
                authors = listOf("Author"),
                languageTag = "en",
                coverImage = null,
            ),
            chapters =
            listOf(
                ChapterEntity(
                    bookId = BOOK_ID,
                    index = 0,
                    title = "Contents",
                    htmlContent = htmlContent,
                    plainText = "Chapter",
                ),
            ),
            tableOfContentsEntries = emptyList(),
        )
    }

    private suspend fun insertLegacyNavigationBook() {
        val chapters =
            buildList {
                add(
                    ChapterEntity(
                        bookId = BOOK_ID,
                        index = 0,
                        title = "Contents",
                        htmlContent = LEGACY_NAVIGATION_HTML,
                        plainText = "Contents\n\nChapter One\n\n1",
                        imagePaths = LEGACY_NAVIGATION_IMAGE_PATHS,
                        wordCount = 4,
                    ),
                )
                add(
                    ChapterEntity(
                        bookId = BOOK_ID,
                        index = 1,
                        title = "Chapter One",
                        htmlContent = "<h1>Chapter One</h1><p>Story text.</p>",
                        plainText = "Chapter One\n\nStory text.",
                        imagePaths = "kairo_epub_assets/$BOOK_ID/images/story.png",
                        wordCount = 4,
                    ),
                )
            }
        database.bookDao().insertBook(
            book =
            BookEntity(
                id = BOOK_ID,
                title = "Book",
                authors = listOf("Author"),
                languageTag = "en",
                coverImage = ByteArray(16) { 3 },
            ),
            chapters = chapters,
            tableOfContentsEntries =
            listOf(
                TableOfContentsEntryEntity(
                    bookId = BOOK_ID,
                    entryIndex = 0,
                    label = "Chapter One",
                    depth = 0,
                    chapterIndex = 1,
                    characterOffset = 0,
                ),
            ),
        )
    }

    private suspend fun legacyNavigationCandidate(): EpubNavigationChapterCandidate =
        database.epubNavigationDao()
            .getMarkerlessNavigationCandidates(
                bookId = BOOK_ID,
                canonicalMarker = EpubReaderNavigationContent.MARKER,
                maxHtmlCharacters = MAX_NAVIGATION_HTML_CHARACTERS,
                limit = 17,
            ).single()

    private suspend fun canonicalizeNavigation(candidate: EpubNavigationChapterCandidate): Boolean =
        database.epubNavigationDao().canonicalizeLegacyNavigationChapter(
            bookId = BOOK_ID,
            chapterIndex = candidate.chapterIndex,
            expectedHtmlContent = candidate.htmlContent,
            htmlContent = CANONICAL_NAVIGATION_HTML,
            plainText = CANONICAL_NAVIGATION_TEXT,
            wordCount = CANONICAL_NAVIGATION_WORD_COUNT,
        )

    private suspend fun assertCanonicalNavigationBlocked() {
        assertFalse(canonicalizeNavigation(legacyNavigationCandidate()))
        assertEquals(LEGACY_NAVIGATION_HTML, database.bookDao().getChapter(BOOK_ID, 0)?.htmlContent)
    }

    private suspend fun searchSaved(query: String): List<SavedAnnotationWithBookEntity> =
        database.savedAnnotationDao().searchWithBook(query.toSqlLikePattern(), limit = 20)

    private suspend fun searchPassages(
        query: String,
        bookId: String? = BOOK_ID,
    ): List<LibrarySearchResult> =
        LibrarySearchRepositoryImpl(
            searchDao = database.searchDao(),
            annotationDao = database.savedAnnotationDao(),
            dispatcherProvider = AndroidTestDispatcherProvider,
        ).search(query, bookId = bookId)

    private fun rowCount(table: String): Int =
        database.query("SELECT COUNT(*) FROM $table", emptyArray()).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun annotation(
        id: String,
        selectedText: String,
    ): SavedAnnotationEntity =
        SavedAnnotationEntity(
            id = id,
            bookId = BOOK_ID,
            chapterIndex = 0,
            startTokenIndex = 0,
            endTokenIndex = 1,
            selectedText = selectedText,
            note = "",
            color = "YELLOW",
            kind = "HIGHLIGHT",
            createdAt = 1L,
            updatedAt = 1L,
        )

    private fun session(
        id: String,
        bookId: String = BOOK_ID,
        mode: String = "READER",
        startChapterIndex: Int = 0,
        endChapterIndex: Int = 0,
    ): ReadingSessionEntity =
        ReadingSessionEntity(
            id = id,
            bookId = bookId,
            mode = mode,
            startedAt = 1L,
            endedAt = 300_001L,
            activeDurationMs = 300_000L,
            startChapterIndex = startChapterIndex,
            startTokenIndex = 0,
            endChapterIndex = endChapterIndex,
            endTokenIndex = 20,
            wordsRead = 20,
            effectiveWpm = 4,
            isWordCountEstimated = true,
        )

    private fun checkpoint(
        id: String = "checkpoint",
        bookId: String = BOOK_ID,
        sessionKey: String = "reader:$bookId",
        mode: String = "READER",
        startChapterIndex: Int = 0,
        endChapterIndex: Int = 0,
        lastReaderWordIndex: Int? = 1,
    ): ReadingSessionCheckpointEntity =
        ReadingSessionCheckpointEntity(
            id = id,
            sessionKey = sessionKey,
            logicalSessionId = "logical",
            bookId = bookId,
            mode = mode,
            logicalStartedAt = 1L,
            dayStartedAt = 0L,
            startedAt = 1L,
            endedAt = 2L,
            activeDurationMs = 1L,
            startChapterIndex = startChapterIndex,
            startTokenIndex = 0,
            endChapterIndex = endChapterIndex,
            endTokenIndex = 1,
            wordsRead = 1,
            isWordCountEstimated = true,
            lastReaderWordIndex = lastReaderWordIndex,
        )

    private companion object {
        const val BOOK_ID = "book"
        const val OTHER_BOOK_ID = "other-book"
        const val SESSION_KEY = "reader:$BOOK_ID"
        const val MISSING_BOOK_ID = "missing-book"
        const val MISSING_BOOK_SESSION_KEY = "reader:$MISSING_BOOK_ID"
        const val SAME_TITLE_BOOK_A = "same-title-a"
        const val SAME_TITLE_BOOK_B = "same-title-b"
        const val MAX_SEARCH_SNIPPET_LENGTH = 120
        const val MAX_NAVIGATION_HTML_CHARACTERS = 5 * 1024 * 1024
        const val LEGACY_NAVIGATION_IMAGE_PATHS = "kairo_epub_assets/book/images/toc.png"
        const val CANONICAL_NAVIGATION_TEXT = "Contents\n\nChapter One"
        const val CANONICAL_NAVIGATION_WORD_COUNT = 3
        val LEGACY_NAVIGATION_HTML =
            """
            <html><body>
              <nav epub:type="toc">
                <h1>Contents</h1>
                <ol><li><a href="kairo://chapter/1">Chapter One</a></li></ol>
              </nav>
              <nav epub:type="page-list">
                <ol><li><a href="kairo://chapter/1#page-1">1</a></li></ol>
              </nav>
            </body></html>
            """.trimIndent()
        val CANONICAL_NAVIGATION_HTML =
            "<html><body><nav ${EpubReaderNavigationContent.MARKER}>" +
                "<h1>Contents</h1><ol><li data-kairo-depth=\"0\">" +
                "<a href=\"kairo://chapter/1\">Chapter One</a></li></ol></nav></body></html>"
    }
}

private object AndroidTestDispatcherProvider : DispatcherProvider {
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
}
