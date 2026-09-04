@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.data.books.TextImportRequest
import com.kairo.reader.ui.export.NoteExportUiBindings
import com.kairo.reader.ui.library.ImportUiState
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState

internal data class LibraryDestinationDependencies(
    val container: KairoApplication,
    val navController: NavHostController,
    val prefs: UserPreferences,
    val selectedWpm: Int,
    val importState: ImportUiState,
    val onImportFile: (Uri) -> Unit,
    val onImportUrl: (String) -> Unit,
    val onImportText: (TextImportRequest) -> Unit,
    val tutorialState: StartingTutorialOverlayState?,
    val noteExportUi: NoteExportUiBindings,
    val onTutorialNext: () -> Unit,
    val onTutorialPrevious: () -> Unit,
    val onTutorialSkip: () -> Unit,
)

internal fun NavGraphBuilder.libraryDestinations(dependencies: LibraryDestinationDependencies) {
    composable(KairoRoutes.LIBRARY) {
        KairoLibraryDestination(dependencies)
    }

    composable(
        route = KairoRoutes.LIBRARY_WITH_TAB,
        arguments =
        listOf(
            navArgument(KairoRoutes.ARG_LIBRARY_TAB) {
                type = NavType.StringType
                defaultValue = KairoRoutes.TAB_LIBRARY
            },
        ),
    ) { backStackEntry ->
        KairoLibraryDestination(
            dependencies = dependencies,
            initialTabRouteValue =
            backStackEntry.arguments?.getString(KairoRoutes.ARG_LIBRARY_TAB)
                ?: KairoRoutes.TAB_LIBRARY,
        )
    }
}

@Composable
private fun KairoLibraryDestination(
    dependencies: LibraryDestinationDependencies,
    initialTabRouteValue: String? = null,
) {
    LibraryRoute(
        LibraryRouteInput(
            container = dependencies.container,
            navController = dependencies.navController,
            prefs = dependencies.prefs,
            selectedWpm = dependencies.selectedWpm,
            importState = dependencies.importState,
            initialTabRouteValue = initialTabRouteValue,
            onImportFile = dependencies.onImportFile,
            onImportUrl = dependencies.onImportUrl,
            onImportText = dependencies.onImportText,
            tutorialState = dependencies.tutorialState,
            noteExportUi = dependencies.noteExportUi,
            onTutorialNext = dependencies.onTutorialNext,
            onTutorialPrevious = dependencies.onTutorialPrevious,
            onTutorialSkip = dependencies.onTutorialSkip,
        ),
    )
}
