package com.kairo.reader.ui.tutorial

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.data.books.SharedTextImport
import com.kairo.reader.sample.SampleBooks
import com.kairo.reader.ui.navigation.KairoRoutes
import com.kairo.reader.ui.updates.releaseNotes
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal data class StartingTutorialCoordinator(
    val active: Boolean,
    val libraryState: StartingTutorialOverlayState?,
    val settingsState: StartingTutorialOverlayState?,
    val readerState: StartingTutorialOverlayState?,
    val rsvpState: StartingTutorialOverlayState?,
    val start: () -> Unit,
    val startReleaseTour: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val skip: () -> Unit,
)

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun rememberStartingTutorialCoordinator(
    container: KairoApplication,
    navController: NavHostController,
    prefs: UserPreferences,
    currentRoute: String?,
    externalImportUri: Uri?,
    externalArticleUrl: String?,
    externalSharedText: SharedTextImport?,
    isImporting: Boolean,
    onViewChangelog: () -> Unit,
): StartingTutorialCoordinator {
    val coroutineScope = rememberCoroutineScope()
    var isReleaseTour by rememberSaveable { mutableStateOf(false) }
    var tutorialLaunchContext by rememberSaveable(stateSaver = StartingTutorialLaunchContextSaver) {
        mutableStateOf<StartingTutorialLaunchContext?>(null)
    }
    val tutorialSteps = startingTutorialSteps(includeReaderAndRsvp = tutorialLaunchContext != null)
    var tutorialStepIndex by rememberSaveable { mutableIntStateOf(0) }
    var tutorialActive by rememberSaveable { mutableStateOf(false) }
    var tutorialAutoStarted by rememberSaveable { mutableStateOf(false) }
    val sessionSteps = if (isReleaseTour) releaseNotes.first().tour else tutorialSteps
    val safeTutorialStepIndex = clampTutorialStepIndex(tutorialStepIndex, sessionSteps)
    val availableTutorialLaunchContext by produceState<StartingTutorialLaunchContext?>(
        initialValue = null,
        prefs.hasSeenStartingTutorial,
        tutorialAutoStarted,
    ) {
        if (prefs.hasSeenStartingTutorial || tutorialAutoStarted) {
            value = null
            return@produceState
        }
        combine(
            container.libraryRepository.observeLibrary(),
            container.readingPositionRepository.observePositions(),
        ) { books, positions ->
            resolveStartingTutorialLaunchContext(books, positions)
        }
            .distinctUntilChanged()
            .collect { context ->
                value = context
            }
    }

    LaunchedEffect(sessionSteps.size, tutorialStepIndex) {
        if (safeTutorialStepIndex != null && safeTutorialStepIndex != tutorialStepIndex) {
            tutorialStepIndex = safeTutorialStepIndex
        }
    }

    fun resolveCurrentTutorialRoute(route: String?): StartingTutorialRoute? =
        when (route) {
            KairoRoutes.LIBRARY,
            KairoRoutes.LIBRARY_WITH_TAB,
            -> StartingTutorialRoute.LIBRARY
            KairoRoutes.SETTINGS -> StartingTutorialRoute.SETTINGS
            KairoRoutes.READER,
            KairoRoutes.READER_WITH_POSITION,
            ->
                StartingTutorialRoute.READER
            KairoRoutes.RSVP -> StartingTutorialRoute.RSVP
            else -> null
        }

    fun navigateToTutorialRoute(route: StartingTutorialRoute) {
        if (resolveCurrentTutorialRoute(currentRoute) == route) return
        when (route) {
            StartingTutorialRoute.LIBRARY -> {
                navController.navigate(KairoRoutes.LIBRARY) {
                    popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
                    launchSingleTop = true
                }
            }

            StartingTutorialRoute.SETTINGS -> {
                navController.navigate(KairoRoutes.SETTINGS) {
                    popUpTo(KairoRoutes.SETTINGS) { inclusive = false }
                    launchSingleTop = true
                }
            }

            StartingTutorialRoute.READER -> {
                val context = tutorialLaunchContext ?: return
                navController.navigate(
                    KairoRoutes.reader(
                        context.bookId,
                        context.chapterIndex,
                        context.tokenIndex,
                    )
                ) {
                    launchSingleTop = true
                }
            }

            StartingTutorialRoute.RSVP -> {
                val context = tutorialLaunchContext ?: return
                navController.navigate(
                    KairoRoutes.rsvp(
                        context.bookId,
                        context.chapterIndex,
                        context.tokenIndex,
                    )
                ) {
                    launchSingleTop = true
                }
            }
        }
    }

    fun markStartingTutorialSeenIfNeeded() {
        if (prefs.hasSeenStartingTutorial) return
        coroutineScope.launch {
            container.preferencesRepository.updateHasSeenStartingTutorial(true)
        }
    }

    fun startReleaseTour() {
        val firstStep = releaseNotes.first().tour.firstOrNull() ?: return
        isReleaseTour = true
        tutorialStepIndex = 0
        tutorialActive = true
        navigateToTutorialRoute(firstStep.route)
    }

    fun startStartingTutorial() {
        isReleaseTour = false
        val sessionSteps =
            startingTutorialSteps(includeReaderAndRsvp = availableTutorialLaunchContext != null)
        tutorialLaunchContext = availableTutorialLaunchContext
        tutorialStepIndex = 0
        tutorialActive = true
        tutorialAutoStarted = true
        navigateToTutorialRoute(sessionSteps.first().route)
    }

    fun dismissStartingTutorial(markAsSeen: Boolean = true) {
        tutorialActive = false
        if (markAsSeen && !isReleaseTour) {
            markStartingTutorialSeenIfNeeded()
        }
    }

    fun finishStartingTutorial() {
        dismissStartingTutorial(markAsSeen = true)
        navigateToTutorialRoute(StartingTutorialRoute.LIBRARY)
    }

    fun moveStartingTutorial(stepDelta: Int) {
        val nextIndex = tutorialStepIndex + stepDelta
        if (nextIndex !in sessionSteps.indices) {
            finishStartingTutorial()
            return
        }
        val previousRoute = sessionSteps.getOrNull(tutorialStepIndex)?.route
        tutorialStepIndex = nextIndex
        val nextRoute = sessionSteps[nextIndex].route
        if (nextRoute != previousRoute) {
            navigateToTutorialRoute(nextRoute)
        }
    }

    val tutorialOverlayState =
        if (tutorialActive && safeTutorialStepIndex != null) {
            StartingTutorialOverlayState(
                step = sessionSteps[safeTutorialStepIndex],
                index = safeTutorialStepIndex,
                totalSteps = sessionSteps.size,
                onViewChangelog = if (isReleaseTour) {
                    {
                        dismissStartingTutorial()
                        onViewChangelog()
                    }
                } else {
                    null
                },
            )
        } else {
            null
        }

    BackHandler(enabled = tutorialActive) {
        if (tutorialStepIndex == 0) {
            dismissStartingTutorial()
        } else {
            moveStartingTutorial(-1)
        }
    }

    LaunchedEffect(
        prefs.hasSeenStartingTutorial,
        externalImportUri,
        externalArticleUrl,
        externalSharedText,
        isImporting,
        availableTutorialLaunchContext,
    ) {
        val hasNoPendingExternalImport =
            externalImportUri == null && externalArticleUrl == null && externalSharedText == null
        val shouldAutoStart =
            !prefs.hasSeenStartingTutorial &&
                !tutorialAutoStarted &&
                hasNoPendingExternalImport &&
                !isImporting &&
                availableTutorialLaunchContext != null
        if (shouldAutoStart) {
            startStartingTutorial()
        }
    }

    return StartingTutorialCoordinator(
        active = tutorialActive,
        libraryState =
        tutorialOverlayState?.takeIf { it.step.route == StartingTutorialRoute.LIBRARY },
        settingsState =
        tutorialOverlayState?.takeIf { it.step.route == StartingTutorialRoute.SETTINGS },
        readerState =
        tutorialOverlayState?.takeIf { it.step.route == StartingTutorialRoute.READER },
        rsvpState =
        tutorialOverlayState?.takeIf { it.step.route == StartingTutorialRoute.RSVP },
        start = ::startStartingTutorial,
        startReleaseTour = ::startReleaseTour,
        next = { moveStartingTutorial(1) },
        previous = { moveStartingTutorial(-1) },
        skip = { dismissStartingTutorial() },
    )
}

internal fun clampTutorialStepIndex(
    index: Int,
    steps: List<StartingTutorialStep>,
): Int? =
    if (steps.isEmpty()) {
        null
    } else {
        index.coerceIn(0, steps.lastIndex)
    }

internal data class StartingTutorialLaunchContext(val bookId: String, val chapterIndex: Int, val tokenIndex: Int,)

internal val StartingTutorialLaunchContextSaver = listSaver<StartingTutorialLaunchContext?, Any>(
    save = { context ->
        context?.let { listOf(it.bookId, it.chapterIndex, it.tokenIndex) }.orEmpty()
    },
    restore = { values ->
        StartingTutorialLaunchContext(values[0] as String, values[1] as Int, values[2] as Int)
    },
)

private fun resolveStartingTutorialLaunchContext(
    books: List<Book>,
    positions: List<ReadingPosition>,
): StartingTutorialLaunchContext? {
    val book =
        books.firstOrNull { it.id.value == SampleBooks.STARTER_BOOK_ID }
            ?: books.firstOrNull()
            ?: return null
    if (book.chapters.isEmpty()) {
        return StartingTutorialLaunchContext(
            bookId = book.id.value,
            chapterIndex = 0,
            tokenIndex = 0,
        )
    }
    val savedPosition = positions.firstOrNull { it.bookId == book.id }
    val fallbackChapterIndex =
        book.chapters.indexOfFirst { it.plainText.isNotBlank() }.let { index ->
            if (index >= 0) index else 0
        }
    val chapterIndex =
        savedPosition?.chapterIndex?.coerceIn(0, book.chapters.lastIndex) ?: fallbackChapterIndex
    val tokenIndex = savedPosition?.tokenIndex?.coerceAtLeast(0) ?: 0
    return StartingTutorialLaunchContext(
        bookId = book.id.value,
        chapterIndex = chapterIndex,
        tokenIndex = tokenIndex,
    )
}
