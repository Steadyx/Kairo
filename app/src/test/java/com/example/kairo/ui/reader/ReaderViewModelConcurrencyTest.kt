package com.example.kairo.ui.reader

import com.example.kairo.core.dispatchers.DispatcherProvider
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.data.books.BookRepository
import com.example.kairo.data.token.TokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}

private class FakeBookRepository(
    private val book: Book,
    private val chapters: List<Chapter>,
) : BookRepository {
    override suspend fun importBook(uri: android.net.Uri): Book = book

    override suspend fun getBook(bookId: BookId): Book = book

    override suspend fun getChapter(
        bookId: BookId,
        chapterIndex: Int,
    ): Chapter = chapters[chapterIndex]

    override suspend fun updateChapterWordCount(
        bookId: BookId,
        chapterIndex: Int,
        wordCount: Int,
    ) = Unit

    override suspend fun getBookLanguageTag(bookId: BookId): String? = null

    override fun observeBooks(): Flow<List<Book>> = flowOf(listOf(book))
}

private class FakeTokenRepository : TokenRepository {
    override suspend fun getTokens(
        bookId: BookId,
        chapterIndex: Int,
        chapter: Chapter?,
    ): List<Token> =
        listOf(
            Token(text = "Hello", type = TokenType.WORD),
            Token(text = "world", type = TokenType.WORD),
        )
}
