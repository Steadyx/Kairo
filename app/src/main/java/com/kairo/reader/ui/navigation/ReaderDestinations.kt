@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.theme.KairoFocusedReadingTheme
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState

internal data class ReaderDestinationDependencies(
    val container: KairoApplication,
    val navController: NavHostController,
    val prefs: UserPreferences,
    val estimatedWpm: Int,
    val tutorialActive: Boolean,
    val tutorialState: StartingTutorialOverlayState?,
    val onShowUserMessage: (String) -> Unit,
    val onTutorialNext: () -> Unit,
    val onTutorialPrevious: () -> Unit,
    val onTutorialSkip: () -> Unit,
)

internal fun NavGraphBuilder.readerDestinations(dependencies: ReaderDestinationDependencies) {
    composable(
        route = KairoRoutes.READER,
        arguments =
        listOf(
            navArgument(KairoRoutes.ARG_BOOK_ID) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        KairoReaderDestination(
            dependencies = dependencies,
            backStackEntry = backStackEntry,
        )
    }

    composable(
        route = KairoRoutes.READER_WITH_POSITION,
        arguments =
        listOf(
            navArgument(KairoRoutes.ARG_BOOK_ID) { type = NavType.StringType },
            navArgument(KairoRoutes.ARG_CHAPTER_INDEX) { type = NavType.IntType },
            navArgument(KairoRoutes.ARG_TOKEN_INDEX) { type = NavType.IntType },
            navArgument(KairoRoutes.ARG_SEARCH_CODE_POINT_OFFSET) {
                type = NavType.IntType
                defaultValue = -1
            },
        ),
    ) { backStackEntry ->
        KairoReaderDestination(
            dependencies = dependencies,
            backStackEntry = backStackEntry,
            initialChapterIndex =
            backStackEntry.arguments?.getInt(KairoRoutes.ARG_CHAPTER_INDEX) ?: 0,
            initialTokenIndex =
            backStackEntry.arguments?.getInt(KairoRoutes.ARG_TOKEN_INDEX) ?: 0,
            initialSearchCodePointOffset =
            backStackEntry.arguments
                ?.getInt(KairoRoutes.ARG_SEARCH_CODE_POINT_OFFSET)
                ?.takeIf { it >= 0 },
        )
    }
}

@Composable
private fun KairoReaderDestination(
    dependencies: ReaderDestinationDependencies,
    backStackEntry: NavBackStackEntry,
    initialChapterIndex: Int? = null,
    initialTokenIndex: Int? = null,
    initialSearchCodePointOffset: Int? = null,
) {
    KairoFocusedReadingTheme {
        ReaderRoute(
            ReaderRouteInput(
                backStackEntry = backStackEntry,
                container = dependencies.container,
                navController = dependencies.navController,
                prefs = dependencies.prefs,
                estimatedWpm = dependencies.estimatedWpm,
                tutorialActive = dependencies.tutorialActive,
                tutorialState = dependencies.tutorialState,
                initialChapterIndex = initialChapterIndex,
                initialTokenIndex = initialTokenIndex,
                initialSearchCodePointOffset = initialSearchCodePointOffset,
                onShowUserMessage = dependencies.onShowUserMessage,
                onTutorialNext = dependencies.onTutorialNext,
                onTutorialPrevious = dependencies.onTutorialPrevious,
                onTutorialSkip = dependencies.onTutorialSkip,
            ),
        )
    }
}
