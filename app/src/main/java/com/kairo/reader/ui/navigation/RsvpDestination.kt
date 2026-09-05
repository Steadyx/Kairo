@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.rsvp.ReadingPresentationMode
import com.kairo.reader.ui.theme.KairoFocusedReadingTheme
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState

internal data class RsvpDestinationDependencies(
    val container: KairoApplication,
    val navController: NavHostController,
    val prefs: UserPreferences,
    val tutorialState: StartingTutorialOverlayState?,
    val onShowUserMessage: (String) -> Unit,
    val onTutorialNext: () -> Unit,
    val onTutorialPrevious: () -> Unit,
    val onTutorialSkip: () -> Unit,
)

internal fun NavGraphBuilder.rsvpDestination(dependencies: RsvpDestinationDependencies) {
    composable(
        route = KairoRoutes.RSVP,
        arguments =
        listOf(
            navArgument(KairoRoutes.ARG_BOOK_ID) { type = NavType.StringType },
            navArgument(KairoRoutes.ARG_CHAPTER_INDEX) { type = NavType.IntType },
            navArgument(KairoRoutes.ARG_TOKEN_INDEX) { type = NavType.IntType },
            navArgument(KairoRoutes.ARG_TEMPO_MS) {
                type = NavType.LongType
                defaultValue = -1L
            },
        ),
    ) { backStackEntry ->
        KairoFocusedReadingTheme {
            RsvpRoute(
                backStackEntry = backStackEntry,
                container = dependencies.container,
                navController = dependencies.navController,
                prefs = dependencies.prefs,
                tutorialState = dependencies.tutorialState,
                onShowUserMessage = dependencies.onShowUserMessage,
                onTutorialNext = dependencies.onTutorialNext,
                onTutorialPrevious = dependencies.onTutorialPrevious,
                onTutorialSkip = dependencies.onTutorialSkip,
            )
        }
    }
}

internal fun NavGraphBuilder.bionicDestination(dependencies: RsvpDestinationDependencies) {
    composable(
        route = KairoRoutes.BIONIC,
        arguments =
        listOf(
            navArgument(KairoRoutes.ARG_BOOK_ID) { type = NavType.StringType },
            navArgument(KairoRoutes.ARG_CHAPTER_INDEX) { type = NavType.IntType },
            navArgument(KairoRoutes.ARG_TOKEN_INDEX) { type = NavType.IntType },
            navArgument(KairoRoutes.ARG_TEMPO_MS) {
                type = NavType.LongType
                defaultValue = -1L
            },
        ),
    ) { backStackEntry ->
        KairoFocusedReadingTheme {
            RsvpRoute(
                backStackEntry = backStackEntry,
                container = dependencies.container,
                navController = dependencies.navController,
                prefs = dependencies.prefs,
                tutorialState = null,
                onShowUserMessage = dependencies.onShowUserMessage,
                onTutorialNext = {},
                onTutorialPrevious = {},
                onTutorialSkip = {},
                presentationMode = ReadingPresentationMode.BIONIC,
            )
        }
    }
}
