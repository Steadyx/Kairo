package com.kairo.reader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kairo.reader.KairoApplication
import com.kairo.reader.R
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.data.books.SharedTextImport
import com.kairo.reader.ui.export.NoteExportProgressDialog
import com.kairo.reader.ui.export.rememberNoteExportCoordinator
import com.kairo.reader.ui.importing.rememberImportCoordinator
import com.kairo.reader.ui.tutorial.rememberStartingTutorialCoordinator
import com.kairo.reader.ui.updates.InAppUpdateCheckResult
import com.kairo.reader.ui.updates.InAppUpdateUiBindings

@Suppress("FunctionNaming")
@Composable
internal fun KairoNavHost(
    container: KairoApplication,
    prefs: UserPreferences,
    externalImportUri: Uri?,
    externalArticleUrl: String?,
    externalSharedText: SharedTextImport?,
    onExternalImportUriConsumed: (Uri) -> Unit,
    onExternalArticleUrlConsumed: (String) -> Unit,
    onExternalSharedTextConsumed: (SharedTextImport) -> Unit,
    inAppUpdateUi: InAppUpdateUiBindings,
) {
    val navController = rememberNavController()
    val dispatcherProvider = container.dispatcherProvider
    val messageController = rememberKairoUserMessageController()
    val noteExportUi =
        rememberNoteExportCoordinator(
            container = container,
            onShowUserMessage = messageController::show,
        )
    val upToDateMessage = stringResource(R.string.update_check_up_to_date)
    val updateCheckFailedMessage = stringResource(R.string.update_check_failed)

    val importCoordinator =
        rememberImportCoordinator(
            container = container,
            navController = navController,
            externalImportUri = externalImportUri,
            externalArticleUrl = externalArticleUrl,
            externalSharedText = externalSharedText,
            onExternalImportUriConsumed = onExternalImportUriConsumed,
            onExternalArticleUrlConsumed = onExternalArticleUrlConsumed,
            onExternalSharedTextConsumed = onExternalSharedTextConsumed,
            onShowUserMessage = { message, duration ->
                messageController.show(message, duration)
            },
        )

    val paceState =
        rememberKairoNavPaceState(
            config = prefs.rsvpConfig,
            dispatcherProvider = dispatcherProvider,
        )
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val tutorialCoordinator =
        rememberStartingTutorialCoordinator(
            container = container,
            navController = navController,
            prefs = prefs,
            currentRoute = currentRoute,
            externalImportUri = externalImportUri,
            externalArticleUrl = externalArticleUrl,
            externalSharedText = externalSharedText,
            isImporting = importCoordinator.state.isImporting,
        )
    KairoNavChrome(
        prefs = prefs,
        currentRoute = currentRoute,
        messageController = messageController,
        inAppUpdateUi = inAppUpdateUi,
    ) {
        NavHost(navController = navController, startDestination = KairoRoutes.LIBRARY) {
            kairoNavGraph(
                KairoNavGraphDependencies(
                    library =
                    LibraryDestinationDependencies(
                        container = container,
                        navController = navController,
                        prefs = prefs,
                        selectedWpm = paceState.selectedWpm,
                        importState = importCoordinator.state,
                        onImportFile = importCoordinator.importFile,
                        onImportUrl = importCoordinator.importUrl,
                        onImportText = importCoordinator.importText,
                        tutorialState = tutorialCoordinator.libraryState,
                        noteExportUi = noteExportUi,
                        onTutorialNext = tutorialCoordinator.next,
                        onTutorialPrevious = tutorialCoordinator.previous,
                        onTutorialSkip = tutorialCoordinator.skip,
                    ),
                    reader =
                    ReaderDestinationDependencies(
                        container = container,
                        navController = navController,
                        prefs = prefs,
                        estimatedWpm = paceState.estimatedWpm,
                        tutorialActive = tutorialCoordinator.active,
                        tutorialState = tutorialCoordinator.readerState,
                        onShowUserMessage = { message -> messageController.show(message) },
                        onTutorialNext = tutorialCoordinator.next,
                        onTutorialPrevious = tutorialCoordinator.previous,
                        onTutorialSkip = tutorialCoordinator.skip,
                    ),
                    rsvp =
                    RsvpDestinationDependencies(
                        container = container,
                        navController = navController,
                        prefs = prefs,
                        tutorialState = tutorialCoordinator.rsvpState,
                        onShowUserMessage = { message -> messageController.show(message) },
                        onTutorialNext = tutorialCoordinator.next,
                        onTutorialPrevious = tutorialCoordinator.previous,
                        onTutorialSkip = tutorialCoordinator.skip,
                    ),
                    settings =
                    SettingsRouteDependencies(
                        container = container,
                        navController = navController,
                        prefs = prefs,
                        tutorialState = tutorialCoordinator.settingsState,
                        onCheckForUpdates = {
                            inAppUpdateUi.onCheckForUpdates { result ->
                                when (result) {
                                    InAppUpdateCheckResult.AVAILABLE -> Unit
                                    InAppUpdateCheckResult.UP_TO_DATE -> {
                                        messageController.show(upToDateMessage)
                                    }
                                    InAppUpdateCheckResult.FAILED -> {
                                        messageController.show(updateCheckFailedMessage)
                                    }
                                }
                            }
                        },
                        onOpenStartingTutorial = tutorialCoordinator.start,
                        onTutorialNext = tutorialCoordinator.next,
                        onTutorialPrevious = tutorialCoordinator.previous,
                        onTutorialSkip = tutorialCoordinator.skip,
                    ),
                )
            )
        }
    }
    NoteExportProgressDialog(
        phase = noteExportUi.state.phase,
        onCancelAwaiting = noteExportUi.cancelPending,
    )
}
