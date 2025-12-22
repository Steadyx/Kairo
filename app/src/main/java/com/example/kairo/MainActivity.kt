package com.example.kairo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Bookmark
import com.example.kairo.core.model.ReadingPosition
import com.example.kairo.core.model.UserPreferences
import com.example.kairo.core.model.nearestWordIndex
import com.example.kairo.ui.LocalDispatcherProvider
import com.example.kairo.ui.focus.FocusModeSideEffects
import com.example.kairo.ui.focus.SystemBarsStyleSideEffect
import com.example.kairo.ui.library.LibraryScreen
import com.example.kairo.ui.library.LibraryTab
import com.example.kairo.ui.reader.ReaderScreen
import com.example.kairo.ui.reader.ReaderViewModel
import com.example.kairo.ui.rsvp.RsvpBookContext
import com.example.kairo.ui.rsvp.RsvpBookmarkCallbacks
import com.example.kairo.ui.rsvp.RsvpLayoutBias
import com.example.kairo.ui.rsvp.RsvpPlaybackCallbacks
import com.example.kairo.ui.rsvp.RsvpPreferenceCallbacks
import com.example.kairo.ui.rsvp.RsvpProfileContext
import com.example.kairo.ui.rsvp.RsvpScreen
import com.example.kairo.ui.rsvp.RsvpScreenCallbacks
import com.example.kairo.ui.rsvp.RsvpScreenDependencies
import com.example.kairo.ui.rsvp.RsvpScreenState
import com.example.kairo.ui.rsvp.RsvpTextStyle
import com.example.kairo.ui.rsvp.RsvpThemeCallbacks
import com.example.kairo.ui.rsvp.RsvpUiCallbacks
import com.example.kairo.ui.rsvp.RsvpUiPreferences
import com.example.kairo.ui.settings.FocusSettingsScreen
import com.example.kairo.ui.settings.ReaderSettingsScreen
import com.example.kairo.ui.settings.RsvpSettingsScreen
import com.example.kairo.ui.settings.SettingsHomeScreen
import com.example.kairo.ui.theme.KairoTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val container = (application as KairoApplication).container

        setContent {
            val prefs by container.preferencesRepository.preferences.collectAsState(
                initial = UserPreferences(),
            )

            CompositionLocalProvider(
                LocalDispatcherProvider provides container.dispatcherProvider
            ) {
                KairoTheme(readerTheme = prefs.readerTheme) {
                    SystemBarsStyleSideEffect(readerTheme = prefs.readerTheme)
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        KairoNavHost(container, prefs)
                    }
                }
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod")
@Composable
private fun KairoNavHost(
    container: AppContainer,
    prefs: UserPreferences,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val libraryFlow = container.libraryRepository.observeLibrary()
    val books by libraryFlow.collectAsState(initial = emptyList())
    val bookmarksFlow = container.bookmarkRepository.observeBookmarks()
    val bookmarks by bookmarksFlow.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val dispatcherProvider = container.dispatcherProvider

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val focusEnabledForRoute =
        prefs.focusModeEnabled &&
            when (currentRoute) {
                "settings" -> true
                "reader/{bookId}" -> prefs.focusApplyInReader
                "reader/{bookId}/{chapterIndex}/{tokenIndex}" -> prefs.focusApplyInReader
                "rsvp/{bookId}/{chapterIndex}/{tokenIndex}" -> prefs.focusApplyInRsvp
                else -> false
            }
    FocusModeSideEffects(
        enabled = focusEnabledForRoute,
        hideStatusBar = prefs.focusHideStatusBar,
        pauseNotifications = prefs.focusPauseNotifications,
    )

    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                books = books,
                bookmarks = bookmarks,
                initialTab = LibraryTab.Library,
                onOpen = { book ->
                    // Navigate to reader - saved position will be restored there
                    navController.navigate("reader/${book.id.value}")
                },
                onOpenBookmark = { bookId, chapterIndex, tokenIndex ->
                    coroutineScope.launch(dispatcherProvider.io) {
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(BookId(bookId), chapterIndex, tokenIndex),
                        )
                    }
                    navController.navigate("reader/$bookId/$chapterIndex/$tokenIndex")
                },
                onDeleteBookmark = { bookmarkId ->
                    coroutineScope.launch { container.bookmarkRepository.delete(bookmarkId) }
                },
                onImportFile = { uri ->
                    coroutineScope.launch {
                        val result = runCatching { container.libraryRepository.import(uri) }
                        result.onSuccess { book ->
                            val message = "Imported: ${book.title} (${book.chapters.size} chapters)"
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                        result.onFailure { error ->
                            val message =
                                error.message?.let { "Import failed: $it" }
                                    ?: "Import failed: Unknown error"
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onSettings = { navController.navigate("settings") },
                onDelete = { book ->
                    coroutineScope.launch { container.libraryRepository.delete(book.id.value) }
                },
            )
        }

        composable(
            route = "library?tab={tab}",
            arguments =
            listOf(
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = "library"
                },
            ),
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab") ?: "library"
            val initialTab =
                if (tab.lowercase() == "bookmarks") {
                    LibraryTab.Bookmarks
                } else {
                    LibraryTab.Library
                }
            LibraryScreen(
                books = books,
                bookmarks = bookmarks,
                initialTab = initialTab,
                onOpen = { book ->
                    navController.navigate("reader/${book.id.value}")
                },
                onOpenBookmark = { bookId, chapterIndex, tokenIndex ->
                    coroutineScope.launch(dispatcherProvider.io) {
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(BookId(bookId), chapterIndex, tokenIndex),
                        )
                    }
                    navController.navigate("reader/$bookId/$chapterIndex/$tokenIndex")
                },
                onDeleteBookmark = { bookmarkId ->
                    coroutineScope.launch { container.bookmarkRepository.delete(bookmarkId) }
                },
                onImportFile = { uri ->
                    coroutineScope.launch {
                        val result = runCatching { container.libraryRepository.import(uri) }
                        result.onSuccess { book ->
                            val message = "Imported: ${book.title} (${book.chapters.size} chapters)"
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                        result.onFailure { error ->
                            val message =
                                error.message?.let { "Import failed: $it" }
                                    ?: "Import failed: Unknown error"
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onSettings = { navController.navigate("settings") },
                onDelete = { book ->
                    coroutineScope.launch { container.libraryRepository.delete(book.id.value) }
                },
            )
        }

        composable(
            route = "reader/{bookId}",
            arguments =
            listOf(
                navArgument("bookId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable

            // Load book once
            val initialBook: Book? = null
            val bookState =
                produceState(
                    initialValue = initialBook,
                    bookId,
                ) {
                    value =
                        runCatching { container.bookRepository.getBook(BookId(bookId)) }.getOrNull()
                }
            val book = bookState.value
            if (book == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@composable
            }

            // Use ViewModel for chapter caching and preloading
            val readerViewModel: ReaderViewModel =
                viewModel(
                    factory =
                    ReaderViewModel.factory(
                        container.bookRepository,
                        container.tokenRepository,
                        dispatcherProvider,
                    ),
                )
            val uiState by readerViewModel.uiState.collectAsState()

            // Resume index returned from RSVP. Use it immediately to avoid focus "jump".
            val rsvpResultFlow =
                remember(backStackEntry) {
                    backStackEntry.savedStateHandle.getStateFlow("rsvp_result_token_index", -1)
                }
            val rsvpResultIndex by rsvpResultFlow.collectAsState(initial = -1)
            val safeRsvpResultIndex =
                if (rsvpResultIndex >= 0) {
                    val tokens = uiState.chapterData?.tokens
                    if (tokens != null && tokens.isNotEmpty()) {
                        tokens.nearestWordIndex(rsvpResultIndex)
                    } else {
                        rsvpResultIndex
                    }
                } else {
                    rsvpResultIndex
                }
            val effectiveUiState =
                if (safeRsvpResultIndex >=
                    0
                ) {
                    uiState.copy(focusIndex = safeRsvpResultIndex)
                } else {
                    uiState
                }

            LaunchedEffect(safeRsvpResultIndex) {
                if (safeRsvpResultIndex >= 0) {
                    if (safeRsvpResultIndex != uiState.focusIndex) {
                        readerViewModel.applyFocusIndex(safeRsvpResultIndex)
                    }
                    coroutineScope.launch(dispatcherProvider.io) {
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(
                                BookId(bookId),
                                uiState.chapterIndex,
                                safeRsvpResultIndex,
                            ),
                        )
                    }
                    backStackEntry.savedStateHandle["rsvp_result_token_index"] = -1
                }
            }

            // Track if we've done initial load
            var hasInitialized by rememberSaveable { mutableStateOf(false) }

            // Load book with saved position on first entry
            LaunchedEffect(book) {
                if (!hasInitialized || uiState.chapterData == null) {
                    val savedPosition = container.readingPositionRepository.getPosition(
                        BookId(bookId)
                    )
                    val initialChapter = savedPosition?.chapterIndex ?: 0
                    val initialFocus = savedPosition?.tokenIndex ?: 0
                    readerViewModel.loadBook(book, initialChapter, initialFocus)
                    hasInitialized = true
                }
            }

            // Sync focus from storage when returning from RSVP (lifecycle resumes)
            val lifecycleOwner = LocalLifecycleOwner.current
            val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

            LaunchedEffect(lifecycleState) {
                if (lifecycleState == Lifecycle.State.RESUMED && hasInitialized) {
                    // If a resume index from RSVP is pending, don't overwrite it.
                    if (safeRsvpResultIndex >= 0) return@LaunchedEffect
                    val savedPosition = container.readingPositionRepository.getPosition(
                        BookId(bookId)
                    )
                    if (savedPosition != null &&
                        savedPosition.chapterIndex == uiState.chapterIndex
                    ) {
                        val tokens = uiState.chapterData?.tokens
                        if (tokens != null && savedPosition.tokenIndex != uiState.focusIndex) {
                            readerViewModel.applyFocusIndex(
                                savedPosition.tokenIndex.coerceIn(0, tokens.lastIndex)
                            )
                        }
                    }
                }
            }

            LaunchedEffect(uiState.chapterIndex, uiState.chapterData) {
                if (!hasInitialized) return@LaunchedEffect
                val tokens = uiState.chapterData?.tokens ?: return@LaunchedEffect
                if (tokens.isEmpty()) return@LaunchedEffect
                val safeIndex =
                    tokens.nearestWordIndex(uiState.focusIndex).coerceIn(0, tokens.lastIndex)
                withContext(dispatcherProvider.io) {
                    container.readingPositionRepository.savePosition(
                        ReadingPosition(BookId(bookId), uiState.chapterIndex, safeIndex),
                    )
                }
            }

            val focusEnabledInReader = prefs.focusModeEnabled && prefs.focusApplyInReader
            ReaderScreen(
                book = book,
                uiState = effectiveUiState,
                fontSizeSp = prefs.readerFontSizeSp,
                invertedScroll = prefs.invertedScroll,
                readerTheme = prefs.readerTheme,
                textBrightness = prefs.readerTextBrightness,
                onFontSizeChange = { size ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateFontSize(size)
                    }
                },
                onThemeChange = { theme ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateTheme(theme.name)
                    }
                },
                onTextBrightnessChange = { brightness ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateReaderTextBrightness(brightness)
                    }
                },
                onInvertedScrollChange = { enabled ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateInvertedScroll(enabled)
                    }
                },
                focusModeEnabled = focusEnabledInReader,
                onFocusModeEnabledChange = { enabled ->
                    coroutineScope.launch {
                        if (enabled) {
                            if (!prefs.focusModeEnabled) {
                                container.preferencesRepository.updateFocusModeEnabled(true)
                            }
                            container.preferencesRepository.updateFocusApplyInReader(true)
                        } else {
                            container.preferencesRepository.updateFocusApplyInReader(false)
                        }
                    }
                },
                onAddBookmark = { chapterIndex, tokenIndex, previewText ->
                    coroutineScope.launch {
                        val id = "$bookId:$chapterIndex:$tokenIndex"
                        container.bookmarkRepository.add(
                            Bookmark(
                                id = id,
                                bookId = BookId(bookId),
                                chapterIndex = chapterIndex,
                                tokenIndex = tokenIndex,
                                previewText = previewText,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                        Toast.makeText(context, "Bookmark added", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenBookmarks = {
                    navController.navigate("library?tab=bookmarks")
                },
                onFocusChange = { newFocusIndex ->
                    readerViewModel.setFocusIndex(newFocusIndex)
                    // Save position when focus changes
                    coroutineScope.launch {
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(BookId(bookId), uiState.chapterIndex, newFocusIndex),
                        )
                    }
                },
                onStartRsvp = { start ->
                    // Save current position before navigating to RSVP
                    coroutineScope.launch {
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(BookId(bookId), uiState.chapterIndex, start),
                        )
                    }
                    navController.navigate("rsvp/$bookId/${uiState.chapterIndex}/$start")
                },
                onChapterChange = { newIndex ->
                    readerViewModel.loadChapter(newIndex)
                },
            )
        }

        composable(
            route = "reader/{bookId}/{chapterIndex}/{tokenIndex}",
            arguments =
            listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("chapterIndex") { type = NavType.IntType },
                navArgument("tokenIndex") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            val initialChapterIndex = backStackEntry.arguments?.getInt("chapterIndex") ?: 0
            val initialTokenIndex = backStackEntry.arguments?.getInt("tokenIndex") ?: 0

            val initialBook: Book? = null
            val bookState =
                produceState(
                    initialValue = initialBook,
                    bookId,
                ) {
                    value =
                        runCatching {
                            container.bookRepository.getBook(BookId(bookId))
                        }.getOrNull()
                }
            val book = bookState.value
            if (book == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@composable
            }

            val readerViewModel: ReaderViewModel =
                viewModel(
                    factory =
                    ReaderViewModel.factory(
                        container.bookRepository,
                        container.tokenRepository,
                        dispatcherProvider,
                    ),
                )
            val uiState by readerViewModel.uiState.collectAsState()

            // Resume index returned from RSVP. Use it immediately to avoid focus "jump".
            val rsvpResultFlow =
                remember(backStackEntry) {
                    backStackEntry.savedStateHandle.getStateFlow("rsvp_result_token_index", -1)
                }
            val rsvpResultIndex by rsvpResultFlow.collectAsState(initial = -1)
            val safeRsvpResultIndex =
                if (rsvpResultIndex >= 0) {
                    val tokens = uiState.chapterData?.tokens
                    if (tokens != null && tokens.isNotEmpty()) {
                        tokens.nearestWordIndex(rsvpResultIndex)
                    } else {
                        rsvpResultIndex
                    }
                } else {
                    rsvpResultIndex
                }
            val effectiveUiState =
                if (safeRsvpResultIndex >= 0) {
                    uiState.copy(focusIndex = safeRsvpResultIndex)
                } else {
                    uiState
                }

            LaunchedEffect(safeRsvpResultIndex) {
                if (safeRsvpResultIndex >= 0) {
                    if (safeRsvpResultIndex != uiState.focusIndex) {
                        readerViewModel.applyFocusIndex(safeRsvpResultIndex)
                    }
                    coroutineScope.launch(dispatcherProvider.io) {
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(
                                BookId(bookId),
                                uiState.chapterIndex,
                                safeRsvpResultIndex,
                            ),
                        )
                    }
                    backStackEntry.savedStateHandle["rsvp_result_token_index"] = -1
                }
            }

            // Track if we've done initial load
            var hasInitialized by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(book) {
                if (!hasInitialized || uiState.chapterData == null) {
                    val savedPosition =
                        if (hasInitialized) {
                            container.readingPositionRepository.getPosition(
                                BookId(bookId)
                            )
                        } else {
                            null
                        }
                    val initialChapter =
                        if (savedPosition != null) {
                            savedPosition.chapterIndex
                        } else {
                            initialChapterIndex
                        }
                    val initialFocus =
                        if (savedPosition != null) {
                            savedPosition.tokenIndex
                        } else {
                            initialTokenIndex
                        }
                    readerViewModel.loadBook(book, initialChapter, initialFocus)
                    hasInitialized = true
                }
            }

            // Sync focus from storage when returning from RSVP (lifecycle resumes)
            val lifecycleOwner = LocalLifecycleOwner.current
            val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

            LaunchedEffect(lifecycleState) {
                if (lifecycleState == Lifecycle.State.RESUMED && hasInitialized) {
                    if (safeRsvpResultIndex >= 0) return@LaunchedEffect
                    val savedPosition = container.readingPositionRepository.getPosition(
                        BookId(bookId)
                    )
                    if (savedPosition != null &&
                        savedPosition.chapterIndex == uiState.chapterIndex
                    ) {
                        val tokens = uiState.chapterData?.tokens
                        if (tokens != null && savedPosition.tokenIndex != uiState.focusIndex) {
                            readerViewModel.applyFocusIndex(
                                savedPosition.tokenIndex.coerceIn(0, tokens.lastIndex)
                            )
                        }
                    }
                }
            }

            LaunchedEffect(uiState.chapterIndex, uiState.chapterData) {
                if (!hasInitialized) return@LaunchedEffect
                val tokens = uiState.chapterData?.tokens ?: return@LaunchedEffect
                if (tokens.isEmpty()) return@LaunchedEffect
                val safeIndex =
                    tokens.nearestWordIndex(uiState.focusIndex).coerceIn(0, tokens.lastIndex)
                withContext(dispatcherProvider.io) {
                    container.readingPositionRepository.savePosition(
                        ReadingPosition(BookId(bookId), uiState.chapterIndex, safeIndex),
                    )
                }
            }

            val focusEnabledInReader = prefs.focusModeEnabled && prefs.focusApplyInReader
            ReaderScreen(
                book = book,
                uiState = effectiveUiState,
                fontSizeSp = prefs.readerFontSizeSp,
                invertedScroll = prefs.invertedScroll,
                readerTheme = prefs.readerTheme,
                textBrightness = prefs.readerTextBrightness,
                onFontSizeChange = { size ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateFontSize(size)
                    }
                },
                onThemeChange = { theme ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateTheme(theme.name)
                    }
                },
                onTextBrightnessChange = { brightness ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateReaderTextBrightness(brightness)
                    }
                },
                onInvertedScrollChange = { enabled ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateInvertedScroll(enabled)
                    }
                },
                focusModeEnabled = focusEnabledInReader,
                onFocusModeEnabledChange = { enabled ->
                    coroutineScope.launch {
                        if (enabled) {
                            if (!prefs.focusModeEnabled) {
                                container.preferencesRepository.updateFocusModeEnabled(true)
                            }
                            container.preferencesRepository.updateFocusApplyInReader(true)
                        } else {
                            container.preferencesRepository.updateFocusApplyInReader(false)
                        }
                    }
                },
                onAddBookmark = { chapterIndex, tokenIndex, previewText ->
                    coroutineScope.launch {
                        val id = "$bookId:$chapterIndex:$tokenIndex"
                        container.bookmarkRepository.add(
                            Bookmark(
                                id = id,
                                bookId = BookId(bookId),
                                chapterIndex = chapterIndex,
                                tokenIndex = tokenIndex,
                                previewText = previewText,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                        Toast.makeText(context, "Bookmark added", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenBookmarks = {
                    navController.navigate("library?tab=bookmarks")
                },
                onFocusChange = { newFocusIndex ->
                    readerViewModel.setFocusIndex(newFocusIndex)
                    coroutineScope.launch {
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(BookId(bookId), uiState.chapterIndex, newFocusIndex),
                        )
                    }
                },
                onStartRsvp = { start ->
                    coroutineScope.launch {
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(BookId(bookId), uiState.chapterIndex, start),
                        )
                    }
                    navController.navigate("rsvp/$bookId/${uiState.chapterIndex}/$start")
                },
                onChapterChange = { newIndex ->
                    readerViewModel.loadChapter(newIndex)
                },
            )
        }

        composable(
            route = "rsvp/{bookId}/{chapterIndex}/{tokenIndex}",
            arguments =
            listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("chapterIndex") { type = NavType.IntType },
                navArgument("tokenIndex") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            val chapterIndex = backStackEntry.arguments?.getInt("chapterIndex") ?: 0
            val startIndex = backStackEntry.arguments?.getInt("tokenIndex") ?: 0

            val tokensState =
                produceState(
                    initialValue = emptyList(),
                    bookId,
                    chapterIndex,
                ) {
                    value =
                        runCatching {
                            container.tokenRepository.getTokens(BookId(bookId), chapterIndex)
                        }.getOrElse { emptyList() }
                }
            val tokens = tokensState.value

            val focusEnabledInRsvp = prefs.focusModeEnabled && prefs.focusApplyInRsvp
            val bookIdValue = BookId(bookId)
            val safeStartIndex = startIndex.coerceAtLeast(0)
            val rsvpState =
                RsvpScreenState(
                    book =
                    RsvpBookContext(
                        bookId = bookIdValue,
                        chapterIndex = chapterIndex,
                        tokens = tokens,
                        startIndex = safeStartIndex,
                    ),
                    profile =
                    RsvpProfileContext(
                        config = prefs.rsvpConfig,
                        selectedProfileId = prefs.rsvpSelectedProfileId,
                        customProfiles = prefs.rsvpCustomProfiles,
                    ),
                    uiPrefs =
                    RsvpUiPreferences(
                        extremeSpeedUnlocked = prefs.unlockExtremeSpeed,
                        readerTheme = prefs.readerTheme,
                        focusModeEnabled = focusEnabledInRsvp,
                    ),
                    textStyle =
                    RsvpTextStyle(
                        fontSizeSp = prefs.rsvpFontSizeSp,
                        fontFamily = prefs.rsvpFontFamily,
                        fontWeight = prefs.rsvpFontWeight,
                        textBrightness = prefs.rsvpTextBrightness,
                    ),
                    layoutBias =
                    RsvpLayoutBias(
                        verticalBias = prefs.rsvpVerticalBias,
                        horizontalBias = prefs.rsvpHorizontalBias,
                    ),
                )
            val rsvpCallbacks =
                RsvpScreenCallbacks(
                    bookmarks =
                    RsvpBookmarkCallbacks(
                        onAddBookmark = { tokenIndex, previewText ->
                            coroutineScope.launch {
                                val id = "$bookId:$chapterIndex:$tokenIndex"
                                container.bookmarkRepository.add(
                                    Bookmark(
                                        id = id,
                                        bookId = bookIdValue,
                                        chapterIndex = chapterIndex,
                                        tokenIndex = tokenIndex,
                                        previewText = previewText,
                                        createdAt = System.currentTimeMillis(),
                                    ),
                                )
                                Toast.makeText(
                                    context,
                                    "Bookmark added",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onOpenBookmarks = {
                            navController.navigate("library?tab=bookmarks") {
                                popUpTo("library") { inclusive = false }
                            }
                        },
                    ),
                    playback =
                    RsvpPlaybackCallbacks(
                        onFinished = { lastIndex ->
                            val resumeIndex =
                                if (tokens.isNotEmpty()) {
                                    lastIndex.coerceIn(0, tokens.lastIndex)
                                } else {
                                    lastIndex.coerceAtLeast(0)
                                }
                            coroutineScope.launch {
                                container.readingPositionRepository.savePosition(
                                    ReadingPosition(bookIdValue, chapterIndex, resumeIndex),
                                )
                            }
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("rsvp_result_token_index", resumeIndex)
                            navController.popBackStack()
                        },
                        onPositionChanged = { currentIndex ->
                            val safeIndex =
                                if (tokens.isNotEmpty()) {
                                    currentIndex.coerceIn(0, tokens.lastIndex)
                                } else {
                                    0
                                }
                            coroutineScope.launch(dispatcherProvider.io) {
                                container.readingPositionRepository.savePosition(
                                    ReadingPosition(bookIdValue, chapterIndex, safeIndex),
                                )
                            }
                        },
                        onTempoChange = { tempoMsPerWord ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpConfig {
                                    it.copy(tempoMsPerWord = tempoMsPerWord)
                                }
                            }
                        },
                        onExit = { index ->
                            val resumeIndex =
                                if (tokens.isNotEmpty()) {
                                    index.coerceIn(0, tokens.lastIndex)
                                } else {
                                    index.coerceAtLeast(0)
                                }
                            coroutineScope.launch(dispatcherProvider.io) {
                                container.readingPositionRepository.savePosition(
                                    ReadingPosition(bookIdValue, chapterIndex, resumeIndex),
                                )
                            }
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("rsvp_result_token_index", resumeIndex)
                            navController.popBackStack()
                        },
                    ),
                    preferences =
                    RsvpPreferenceCallbacks(
                        onExtremeSpeedUnlockedChange = { enabled ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateUnlockExtremeSpeed(
                                    enabled
                                )
                            }
                        },
                        onSelectProfile = { profileId ->
                            coroutineScope.launch {
                                container.preferencesRepository.selectRsvpProfile(profileId)
                            }
                        },
                        onSaveCustomProfile = { name, config ->
                            coroutineScope.launch {
                                container.preferencesRepository.saveRsvpCustomProfile(
                                    name,
                                    config
                                )
                            }
                        },
                        onDeleteCustomProfile = { profileId ->
                            coroutineScope.launch {
                                container.preferencesRepository.deleteRsvpCustomProfile(
                                    profileId
                                )
                            }
                        },
                        onRsvpConfigChange = { updated ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpConfig { updated }
                            }
                        },
                    ),
                    ui =
                    RsvpUiCallbacks(
                        onFocusModeEnabledChange = { enabled ->
                            coroutineScope.launch {
                                if (enabled) {
                                    if (!prefs.focusModeEnabled) {
                                        container.preferencesRepository.updateFocusModeEnabled(
                                            true
                                        )
                                    }
                                    container.preferencesRepository.updateFocusApplyInRsvp(true)
                                } else {
                                    container.preferencesRepository.updateFocusApplyInRsvp(
                                        false
                                    )
                                }
                            }
                        },
                        onRsvpFontSizeChange = { size ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpFontSize(size)
                            }
                        },
                        onRsvpTextBrightnessChange = { brightness ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpTextBrightness(
                                    brightness
                                )
                            }
                        },
                        onRsvpFontWeightChange = { weight ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpFontWeight(weight)
                            }
                        },
                        onRsvpFontFamilyChange = { family ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpFontFamily(family)
                            }
                        },
                    ),
                    theme =
                    RsvpThemeCallbacks(
                        onThemeChange = { theme ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateTheme(theme.name)
                            }
                        },
                        onVerticalBiasChange = { bias ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpVerticalBias(bias)
                            }
                        },
                        onHorizontalBiasChange = { bias ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpHorizontalBias(bias)
                            }
                        },
                    ),
                )
            val rsvpDependencies =
                RsvpScreenDependencies(
                    frameRepository = container.rsvpFrameRepository,
                )

            RsvpScreen(
                state = rsvpState,
                callbacks = rsvpCallbacks,
                dependencies = rsvpDependencies,
            )
        }

        composable("settings") {
            SettingsHomeScreen(
                onOpenRsvp = { navController.navigate("settings/rsvp") },
                onOpenReader = { navController.navigate("settings/reader") },
                onOpenFocus = { navController.navigate("settings/focus") },
                onReset = {
                    coroutineScope.launch {
                        container.preferencesRepository.reset()
                    }
                },
                onClose = { navController.popBackStack() },
            )
        }

        composable("settings/rsvp") {
            RsvpSettingsScreen(
                preferences = prefs,
                onSelectRsvpProfile = { profileId ->
                    coroutineScope.launch {
                        container.preferencesRepository.selectRsvpProfile(profileId)
                    }
                },
                onSaveRsvpProfile = { name, config ->
                    coroutineScope.launch {
                        container.preferencesRepository.saveRsvpCustomProfile(name, config)
                    }
                },
                onDeleteRsvpProfile = { profileId ->
                    coroutineScope.launch {
                        container.preferencesRepository.deleteRsvpCustomProfile(profileId)
                    }
                },
                onRsvpConfigChange = { config ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpConfig { config }
                    }
                },
                onUnlockExtremeSpeedChange = { enabled ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateUnlockExtremeSpeed(enabled)
                    }
                },
                onRsvpFontSizeChange = { size ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpFontSize(size)
                    }
                },
                onRsvpTextBrightnessChange = { brightness ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpTextBrightness(brightness)
                    }
                },
                onRsvpFontWeightChange = { weight ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpFontWeight(weight)
                    }
                },
                onRsvpFontFamilyChange = { family ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpFontFamily(family)
                    }
                },
                onRsvpVerticalBiasChange = { bias ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpVerticalBias(bias)
                    }
                },
                onRsvpHorizontalBiasChange = { bias ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpHorizontalBias(bias)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/reader") {
            ReaderSettingsScreen(
                preferences = prefs,
                onFontSizeChange = { size ->
                    coroutineScope.launch { container.preferencesRepository.updateFontSize(size) }
                },
                onThemeChange = { theme ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateTheme(theme.name)
                    }
                },
                onTextBrightnessChange = { brightness ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateReaderTextBrightness(brightness)
                    }
                },
                onInvertedScrollChange = { enabled ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateInvertedScroll(enabled)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/focus") {
            FocusSettingsScreen(
                preferences = prefs,
                onFocusModeEnabledChange = { enabled ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateFocusModeEnabled(enabled)
                    }
                },
                onFocusHideStatusBarChange = { enabled ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateFocusHideStatusBar(enabled)
                    }
                },
                onFocusPauseNotificationsChange = { enabled ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateFocusPauseNotifications(enabled)
                    }
                },
                onFocusApplyInReaderChange = { enabled ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateFocusApplyInReader(enabled)
                    }
                },
                onFocusApplyInRsvpChange = { enabled ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateFocusApplyInRsvp(enabled)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
