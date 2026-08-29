package com.kairo.reader.ui.reader

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.data.books.BookImportResult
import com.kairo.reader.data.books.BookRepository
import com.kairo.reader.data.books.TextImportRequest
import com.kairo.reader.data.token.TokenRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelConcurrencyTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadAndPreload_doNotCrash() = runTest(testDispatcher) {
        val chapters =
            listOf(
                Chapter(index = 0, title = "One", htmlContent = "", plainText = "Hello world"),
                Chapter(index = 1, title = "Two", htmlContent = "", plainText = "Next chapter"),
            )
        val book =
            Book(
                id = BookId("book-1"),
                title = "Test Book",
                authors = listOf("Author"),
                chapters = chapters,
                coverImage = null,
            )
        val repository = FakeBookRepository(book, chapters)
        val tokenRepository = FakeTokenRepository()
        val dispatcherProvider =
            object : DispatcherProvider {
                override val default = testDispatcher
                override val io = testDispatcher
            }

        val viewModel = ReaderViewModel(repository, tokenRepository, dispatcherProvider)

        viewModel.loadBook(book, initialChapterIndex = 0, initialFocusIndex = 0)
        advanceUntilIdle()
        viewModel.loadChapter(0)
        viewModel.loadChapter(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.chapterData)
    }

    @Test
    fun loadBook_restoresInitialFocusIndex() = runTest(testDispatcher) {
        val chapters =
            listOf(
                Chapter(index = 0, title = "One", htmlContent = "", plainText = "Hello bright world"),
            )
        val book =
            Book(
                id = BookId("book-1"),
                title = "Test Book",
                authors = listOf("Author"),
                chapters = chapters,
                coverImage = null,
            )
        val repository = FakeBookRepository(book, chapters)
        val tokenRepository = FakeTokenRepository()
        val dispatcherProvider =
            object : DispatcherProvider {
                override val default = testDispatcher
                override val io = testDispatcher
            }
        val viewModel = ReaderViewModel(repository, tokenRepository, dispatcherProvider)

        viewModel.loadBook(book, initialChapterIndex = 0, initialFocusIndex = 1)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.focusIndex)
    }

    @Test
    fun loadBook_convertsInitialSearchCodePointOffsetBeforeResolvingFocus() =
        runTest(testDispatcher) {
            val plainText = "😀😀😀 abc needle"
            val chapter =
                Chapter(index = 0, title = "One", htmlContent = "", plainText = plainText)
            val book =
                Book(
                    id = BookId("book-1"),
                    title = "Test Book",
                    authors = listOf("Author"),
                    chapters = listOf(chapter),
                    coverImage = null,
                )
            val tokens =
                listOf(
                    Token(text = "😀", type = TokenType.WORD),
                    Token(text = "😀", type = TokenType.WORD),
                    Token(text = "😀", type = TokenType.WORD),
                    Token(text = "abc", type = TokenType.WORD),
                    Token(text = "needle", type = TokenType.WORD),
                )
            val dispatcherProvider =
                object : DispatcherProvider {
                    override val default = testDispatcher
                    override val io = testDispatcher
                }
            val viewModel =
                ReaderViewModel(
                    FakeBookRepository(book, listOf(chapter)),
                    FakeTokenRepository(tokens),
                    dispatcherProvider,
                )

            viewModel.loadBook(
                book = book,
                initialChapterIndex = 0,
                initialSearchCodePointOffset = 8,
            )
            advanceUntilIdle()

            assertEquals(4, viewModel.uiState.value.focusIndex)
        }

    @Test
    fun paginationMetricsSetBeforeLoadBookApplyToInitialChapterPages() = runTest(testDispatcher) {
        val tokens = List(400) { Token(text = "word", type = TokenType.WORD) }
        val chapter =
            Chapter(
                index = 0,
                title = "One",
                htmlContent = "",
                plainText = List(400) { "word" }.joinToString(" "),
                wordCount = 400,
            )
        val book =
            Book(
                id = BookId("book-1"),
                title = "Test Book",
                authors = listOf("Author"),
                chapters = listOf(chapter),
                coverImage = null,
            )
        val dispatcherProvider =
            object : DispatcherProvider {
                override val default = testDispatcher
                override val io = testDispatcher
            }
        val chapterProcessor = ReaderChapterProcessor()
        val resolvedTarget = chapterProcessor.wordsPerPage(fontSizeSp = 36f, viewportHeightDp = 480)
        val expectedPages = buildChapterPages(tokens, wordsPerPage = resolvedTarget)
        val defaultPages = buildChapterPages(tokens, wordsPerPage = DEFAULT_WORDS_PER_PAGE)
        val viewModel =
            ReaderViewModel(
                bookRepository = FakeBookRepository(book, listOf(chapter)),
                tokenRepository = FakeTokenRepository(tokens),
                dispatcherProvider = dispatcherProvider,
                chapterProcessor = chapterProcessor,
            )

        viewModel.updatePaginationMetrics(fontSizeSp = 36f, viewportHeightDp = 480)
        viewModel.loadBook(book)
        advanceUntilIdle()

        assertFalse(expectedPages == defaultPages)
        assertEquals(expectedPages, viewModel.uiState.value.chapterData?.pages)
    }

    @Test
    fun imageBoundsAreResolvedBeforeChapterDataIsPublished() = runTest(testDispatcher) {
        val imagePath = "kairo_epub_assets/book-1/images/illustration.png"
        val chapter =
            Chapter(
                index = 0,
                title = "One",
                htmlContent = "<p>Hello world</p><img src=\"$imagePath\">",
                plainText = "Hello world",
                imagePaths = listOf(imagePath),
                wordCount = 2,
            )
        val book =
            Book(
                id = BookId("book-1"),
                title = "Test Book",
                authors = listOf("Author"),
                chapters = listOf(chapter),
                coverImage = null,
            )
        val dispatcherProvider =
            object : DispatcherProvider {
                override val default = testDispatcher
                override val io = testDispatcher
            }
        val boundsResolutionStarted = CompletableDeferred<Unit>()
        val allowBoundsResolution = CompletableDeferred<Unit>()
        val imageBoundsResolver =
            ReaderImageBoundsResolver { requestedPath ->
                assertEquals(imagePath, requestedPath)
                boundsResolutionStarted.complete(Unit)
                allowBoundsResolution.await()
                ReaderImageSize(widthPx = 1200f, heightPx = 800f)
            }
        val viewModel =
            ReaderViewModel(
                bookRepository = FakeBookRepository(book, listOf(chapter)),
                tokenRepository = FakeTokenRepository(),
                dispatcherProvider = dispatcherProvider,
                imageBoundsResolver = imageBoundsResolver,
            )

        viewModel.loadBook(book, initialChapterIndex = 0, initialFocusIndex = 0)
        runCurrent()

        assertTrue(boundsResolutionStarted.isCompleted)
        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.chapterData)

        allowBoundsResolution.complete(Unit)
        advanceUntilIdle()

        val publishedState = viewModel.uiState.value
        assertFalse(publishedState.isLoading)
        val imageBlock =
            requireNotNull(publishedState.chapterData)
                .blocks
                .filterIsInstance<ReaderImageBlock>()
                .single()
        assertEquals(ReaderImageSize(widthPx = 1200f, heightPx = 800f), imageBlock.imageSize)
    }

    @Test
    fun staleImageBoundsResolutionDoesNotPublishSupersededChapter() = runTest(testDispatcher) {
        val firstImagePath = "kairo_epub_assets/book-1/images/first.png"
        val secondImagePath = "kairo_epub_assets/book-1/images/second.png"
        val chapters =
            listOf(
                imageChapter(index = 0, imagePath = firstImagePath),
                imageChapter(index = 1, imagePath = secondImagePath),
            )
        val book =
            Book(
                id = BookId("book-1"),
                title = "Test Book",
                authors = listOf("Author"),
                chapters = chapters,
                coverImage = null,
            )
        val dispatcherProvider =
            object : DispatcherProvider {
                override val default = testDispatcher
                override val io = testDispatcher
            }
        val firstResolutionStarted = CompletableDeferred<Unit>()
        val allowFirstResolution = CompletableDeferred<Unit>()
        val imageBoundsResolver =
            ReaderImageBoundsResolver { requestedPath ->
                when (requestedPath) {
                    firstImagePath -> {
                        firstResolutionStarted.complete(Unit)
                        allowFirstResolution.await()
                        ReaderImageSize(widthPx = 1200f, heightPx = 800f)
                    }
                    secondImagePath -> ReaderImageSize(widthPx = 900f, heightPx = 600f)
                    else -> error("Unexpected image path: $requestedPath")
                }
            }
        val viewModel =
            ReaderViewModel(
                bookRepository = FakeBookRepository(book, chapters),
                tokenRepository = FakeTokenRepository(),
                dispatcherProvider = dispatcherProvider,
                imageBoundsResolver = imageBoundsResolver,
            )

        viewModel.loadBook(book, initialChapterIndex = 0, initialFocusIndex = 0)
        runCurrent()
        assertTrue(firstResolutionStarted.isCompleted)

        viewModel.loadChapter(chapterIndex = 1)
        runCurrent()

        assertEquals(1, viewModel.uiState.value.chapterIndex)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(
            ReaderImageSize(widthPx = 900f, heightPx = 600f),
            viewModel.uiState.value.chapterData
                ?.blocks
                ?.filterIsInstance<ReaderImageBlock>()
                ?.single()
                ?.imageSize,
        )

        allowFirstResolution.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.chapterIndex)
        assertEquals(secondImagePath, viewModel.uiState.value.chapterData?.imagePaths?.single())
    }

    @Test
    fun everyDistinctIncompleteImageIsResolvedBeforePublication() = runTest(testDispatcher) {
        val distinctImagePaths = List(33) { index -> "kairo_epub_assets/book-1/images/$index.png" }
        val imagePaths = distinctImagePaths + distinctImagePaths.last()
        val chapter =
            Chapter(
                index = 0,
                title = "Images",
                htmlContent = "",
                plainText = "",
                imagePaths = imagePaths,
                wordCount = 1,
            )
        val book =
            Book(
                id = BookId("book-1"),
                title = "Test Book",
                authors = listOf("Author"),
                chapters = listOf(chapter),
                coverImage = null,
            )
        val requestedPaths = mutableListOf<String>()
        val viewModel =
            ReaderViewModel(
                bookRepository = FakeBookRepository(book, listOf(chapter)),
                tokenRepository = FakeTokenRepository(tokens = emptyList()),
                dispatcherProvider =
                    object : DispatcherProvider {
                        override val default = testDispatcher
                        override val io = testDispatcher
                    },
                imageBoundsResolver = ReaderImageBoundsResolver { imagePath ->
                    requestedPaths += imagePath
                    ReaderImageSize(widthPx = 640f, heightPx = 480f)
                },
            )

        viewModel.loadBook(book)
        advanceUntilIdle()

        assertEquals(distinctImagePaths, requestedPaths)
        val imageBlocks =
            requireNotNull(viewModel.uiState.value.chapterData)
                .blocks
                .filterIsInstance<ReaderImageBlock>()
        assertTrue(imageBlocks.all { it.imageSize?.hasCompleteValidImageSize() == true })
    }

    @Test
    fun overLimitAdjacentChapterIsNotCachedAndDirectLoadShowsSafeError() = runTest(testDispatcher) {
        val chapters =
            listOf(
                textChapter(index = 0, title = "Safe chapter"),
                overLimitImageChapter(index = 1),
            )
        val book =
            Book(
                id = BookId("book-1"),
                title = "Test Book",
                authors = listOf("Author"),
                chapters = chapters,
                coverImage = null,
            )
        val repository = FakeBookRepository(book, chapters)
        val requestedImagePaths = mutableListOf<String>()
        val viewModel =
            ReaderViewModel(
                bookRepository = repository,
                tokenRepository = FakeTokenRepository(),
                dispatcherProvider =
                    object : DispatcherProvider {
                        override val default = testDispatcher
                        override val io = testDispatcher
                    },
                imageBoundsResolver = ReaderImageBoundsResolver { imagePath ->
                    requestedImagePaths += imagePath
                    ReaderImageSize(widthPx = 640f, heightPx = 480f)
                },
            )

        viewModel.loadBook(book)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.chapterIndex)
        assertEquals("Hello world", viewModel.uiState.value.chapterData?.plainText)
        assertNull(viewModel.uiState.value.chapterLoadError)
        assertEquals(1, repository.chapterRequests.count { it.second == 1 })
        assertTrue(requestedImagePaths.isEmpty())

        viewModel.loadChapter(chapterIndex = 1)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.chapterIndex)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.chapterData)
        assertEquals(READER_CHAPTER_IMAGE_LIMIT_MESSAGE, viewModel.uiState.value.chapterLoadError)
        assertEquals(2, repository.chapterRequests.count { it.second == 1 })
        assertTrue(requestedImagePaths.isEmpty())
    }

    @Test
    fun staleAdjacentPreloadCannotRepopulateCacheAfterLoadingAnotherBook() =
        assertStaleAdjacentPreloadIsIsolated(
            firstBookId = BookId("book-a"),
            secondBookId = BookId("book-b"),
        )

    @Test
    fun staleAdjacentPreloadCannotRepopulateCacheAfterReloadingSameBookId() =
        assertStaleAdjacentPreloadIsIsolated(
            firstBookId = BookId("same-book"),
            secondBookId = BookId("same-book"),
        )

    private fun assertStaleAdjacentPreloadIsIsolated(
        firstBookId: BookId,
        secondBookId: BookId,
    ) =
        runTest(testDispatcher) {
            val firstBookImagePath = "kairo_epub_assets/book-a/images/chapter-2.png"
            val secondBookImagePath = "kairo_epub_assets/book-b/images/chapter-2.png"
            val firstBookChapters =
                listOf(
                    textChapter(index = 0, title = "A One"),
                    imageChapter(index = 1, imagePath = firstBookImagePath),
                )
            val secondBookChapters =
                listOf(
                    textChapter(index = 0, title = "B One"),
                    imageChapter(index = 1, imagePath = secondBookImagePath),
                )
            val firstBook =
                Book(
                    id = firstBookId,
                    title = "First Book",
                    authors = listOf("Author"),
                    chapters = firstBookChapters,
                    coverImage = null,
                )
            val secondBook =
                Book(
                    id = secondBookId,
                    title = "Second Book",
                    authors = listOf("Author"),
                    chapters = secondBookChapters,
                    coverImage = null,
                )
            val chaptersByBookId = mutableMapOf(firstBookId to firstBookChapters)
            val repository =
                FakeBookRepository(
                    book = firstBook,
                    chapters = firstBookChapters,
                    chaptersByBookId = chaptersByBookId,
                )
            val dispatcherProvider =
                object : DispatcherProvider {
                    override val default = testDispatcher
                    override val io = testDispatcher
                }
            val firstPreloadStarted = CompletableDeferred<Unit>()
            val allowFirstPreload = CompletableDeferred<Unit>()
            val secondPreloadStarted = CompletableDeferred<Unit>()
            val allowSecondResolution = CompletableDeferred<Unit>()
            var secondBookResolutionCount = 0
            val imageBoundsResolver =
                ReaderImageBoundsResolver { requestedPath ->
                    when (requestedPath) {
                        firstBookImagePath -> {
                            firstPreloadStarted.complete(Unit)
                            allowFirstPreload.await()
                            ReaderImageSize(widthPx = 1200f, heightPx = 800f)
                        }
                        secondBookImagePath -> {
                            secondBookResolutionCount += 1
                            secondPreloadStarted.complete(Unit)
                            allowSecondResolution.await()
                            ReaderImageSize(widthPx = 900f, heightPx = 600f)
                        }
                        else -> error("Unexpected image path: $requestedPath")
                    }
                }
            val viewModel =
                ReaderViewModel(
                    bookRepository = repository,
                    tokenRepository = FakeTokenRepository(),
                    dispatcherProvider = dispatcherProvider,
                    imageBoundsResolver = imageBoundsResolver,
                )

            viewModel.loadBook(firstBook, initialChapterIndex = 0, initialFocusIndex = 0)
            runCurrent()
            assertTrue(firstPreloadStarted.isCompleted)

            chaptersByBookId[secondBookId] = secondBookChapters
            viewModel.loadBook(secondBook, initialChapterIndex = 0, initialFocusIndex = 0)
            runCurrent()
            assertTrue(secondPreloadStarted.isCompleted)

            allowFirstPreload.complete(Unit)
            runCurrent()

            viewModel.loadChapter(chapterIndex = 1)
            runCurrent()

            assertTrue(viewModel.uiState.value.isLoading)
            assertNull(viewModel.uiState.value.chapterData)
            assertEquals(2, secondBookResolutionCount)

            allowSecondResolution.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(1, viewModel.uiState.value.chapterIndex)
            assertEquals(secondBookImagePath, viewModel.uiState.value.chapterData?.imagePaths?.single())
            assertEquals(
                ReaderImageSize(widthPx = 900f, heightPx = 600f),
                viewModel.uiState.value.chapterData
                    ?.blocks
                    ?.filterIsInstance<ReaderImageBlock>()
                    ?.single()
                    ?.imageSize,
            )
        }
}

private fun imageChapter(
    index: Int,
    imagePath: String,
): Chapter =
    Chapter(
        index = index,
        title = "Chapter ${index + 1}",
        htmlContent = "<p>Hello world</p><img src=\"$imagePath\">",
        plainText = "Hello world",
        imagePaths = listOf(imagePath),
        wordCount = 2,
    )

private fun textChapter(
    index: Int,
    title: String,
): Chapter =
    Chapter(
        index = index,
        title = title,
        htmlContent = "<p>Hello world</p>",
        plainText = "Hello world",
        wordCount = 2,
    )

private fun overLimitImageChapter(index: Int): Chapter =
    Chapter(
        index = index,
        title = "Over limit",
        htmlContent =
            buildString {
                repeat(257) { imageIndex ->
                    append("<img src='kairo_epub_assets/book/images/$imageIndex.png'>")
                }
            },
        plainText = "Hello world",
        wordCount = 2,
    )

private class FakeBookRepository(
    private val book: Book,
    chapters: List<Chapter>,
    private val chaptersByBookId: Map<BookId, List<Chapter>> = mapOf(book.id to chapters),
) : BookRepository {
    val chapterRequests = mutableListOf<Pair<BookId, Int>>()

    override suspend fun importBook(uri: android.net.Uri): BookImportResult =
        BookImportResult(book = book, alreadyImported = false)

    override suspend fun importUrl(rawUrl: String): BookImportResult =
        BookImportResult(book = book, alreadyImported = false)

    override suspend fun importText(request: TextImportRequest): BookImportResult =
        BookImportResult(book = book, alreadyImported = false)

    override suspend fun getBook(bookId: BookId): Book = book

    override suspend fun getChapter(
        bookId: BookId,
        chapterIndex: Int,
    ): Chapter {
        chapterRequests += bookId to chapterIndex
        return requireNotNull(chaptersByBookId[bookId])[chapterIndex]
    }

    override suspend fun updateChapterWordCount(
        bookId: BookId,
        chapterIndex: Int,
        wordCount: Int,
    ) = Unit

    override suspend fun getBookLanguageTag(bookId: BookId): String? = null

    override fun observeBooks(): Flow<List<Book>> = flowOf(listOf(book))
}

private class FakeTokenRepository(
    private val tokens: List<Token> =
    listOf(
        Token(text = "Hello", type = TokenType.WORD),
        Token(text = "world", type = TokenType.WORD),
    ),
) : TokenRepository {
    override suspend fun getTokens(
        bookId: BookId,
        chapterIndex: Int,
        chapter: Chapter?,
    ): List<Token> = tokens
}
