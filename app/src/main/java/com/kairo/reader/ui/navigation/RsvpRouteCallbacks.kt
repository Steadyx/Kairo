@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.ui.navigation

import android.content.res.Resources
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.R
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Bookmark
import com.kairo.reader.core.model.ReadingSessionMode
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.rsvp.RsvpConfigResolver
import com.kairo.reader.data.sessions.ReadingSessionLocation
import com.kairo.reader.data.sessions.completedFrameProgress
import com.kairo.reader.ui.rsvp.BionicReadingCallbacks
import com.kairo.reader.ui.rsvp.RsvpBookmarkCallbacks
import com.kairo.reader.ui.rsvp.RsvpPlaybackCallbacks
import com.kairo.reader.ui.rsvp.RsvpPreferenceCallbacks
import com.kairo.reader.ui.rsvp.RsvpScreenCallbacks
import com.kairo.reader.ui.rsvp.RsvpThemeCallbacks
import com.kairo.reader.ui.rsvp.RsvpUiCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class RsvpRouteCallbackDependencies(
    val container: KairoApplication,
    val navController: NavHostController,
    val backStackEntry: NavBackStackEntry,
    val prefs: UserPreferences,
    val bookId: String,
    val bookIdValue: BookId,
    val sessionMode: ReadingSessionMode,
    val chapterIndex: Int,
    val chapterCount: Int,
    val tokens: List<Token>,
    val wordCountByToken: IntArray,
    val languageTag: String?,
    val coroutineScope: CoroutineScope,
    val resources: Resources,
    val onShowUserMessage: (String) -> Unit,
    val onSessionFinished: (endTokenIndex: Int) -> Unit,
    val onSessionActiveChanged: (Boolean) -> Unit,
    val saveRsvpPosition: (
        targetChapterIndex: Int,
        targetTokenIndex: Int,
        targetWordIndex: Int,
        targetResumeCursor: Int,
    ) -> Unit,
)

internal fun buildRsvpRouteCallbacks(
    dependencies: RsvpRouteCallbackDependencies,
): RsvpScreenCallbacks =
    RsvpScreenCallbacks(
        bookmarks = buildRsvpBookmarkCallbacks(dependencies),
        playback = buildRsvpPlaybackCallbacks(dependencies),
        preferences = buildRsvpPreferenceCallbacks(dependencies),
        ui = buildRsvpUiCallbacks(dependencies),
        theme = buildRsvpThemeCallbacks(dependencies),
        bionic = buildBionicReadingCallbacks(dependencies),
    )

private fun buildBionicReadingCallbacks(
    dependencies: RsvpRouteCallbackDependencies,
): BionicReadingCallbacks =
    BionicReadingCallbacks(
        onFixationStrengthChange = { strength ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository
                    .updateBionicFixationStrength(strength)
            }
        },
        onHighlightStrengthChange = { strength ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository
                    .updateBionicHighlightStrength(strength)
            }
        },
        onFontSizeChange = { size ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateBionicFontSize(size)
            }
        },
        onTextBrightnessChange = { brightness ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository
                    .updateBionicTextBrightness(brightness)
            }
        },
    )

private fun buildRsvpBookmarkCallbacks(
    dependencies: RsvpRouteCallbackDependencies,
): RsvpBookmarkCallbacks =
    RsvpBookmarkCallbacks(
        onAddBookmark = { tokenIndex, previewText ->
            dependencies.coroutineScope.launch {
                val id = "${dependencies.bookId}:${dependencies.chapterIndex}:$tokenIndex"
                dependencies.container.bookmarkRepository.add(
                    Bookmark(
                        id = id,
                        bookId = dependencies.bookIdValue,
                        chapterIndex = dependencies.chapterIndex,
                        tokenIndex = tokenIndex,
                        previewText = previewText,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                dependencies.onShowUserMessage(
                    dependencies.resources.getString(R.string.toast_bookmark_added)
                )
            }
        },
        onOpenBookmarks = {
            dependencies.container.readingSessionCoordinator.finalizeTimed(
                dependencies.bookIdValue,
                dependencies.sessionMode,
            )
            dependencies.navController.navigate(KairoRoutes.libraryBookmarks()) {
                popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
            }
        },
    )

private fun buildRsvpPlaybackCallbacks(
    dependencies: RsvpRouteCallbackDependencies,
): RsvpPlaybackCallbacks =
    RsvpPlaybackCallbacks(
        onFinished = { resumePoint ->
            dependencies.onSessionFinished(resumePoint.tokenIndex)
            val returnTarget =
                resolveRsvpReturnTarget(
                    resumePoint = resumePoint,
                    currentChapterIndex = dependencies.chapterIndex,
                    chapterCount = dependencies.chapterCount,
                    currentChapterTokens = dependencies.tokens,
                )
            val wordIndex =
                if (returnTarget.chapterIndex == dependencies.chapterIndex) {
                    resolveWordIndex(dependencies.wordCountByToken, returnTarget.tokenIndex)
                } else {
                    0
                }
            dependencies.saveRsvpPosition(
                returnTarget.chapterIndex,
                returnTarget.tokenIndex,
                wordIndex,
                returnTarget.resumeCursor,
            )
            dependencies.persistLiveTempo(resumePoint.tempoMsPerWord)
            dependencies.navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(
                    KairoSavedStateKeys.RSVP_RESULT_CHAPTER_INDEX,
                    returnTarget.chapterIndex,
                )
            dependencies.navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(
                    KairoSavedStateKeys.RSVP_RESULT_TOKEN_INDEX,
                    returnTarget.tokenIndex,
                )
            dependencies.navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(
                    KairoSavedStateKeys.RSVP_RESULT_RESUME_CURSOR,
                    returnTarget.resumeCursor,
                )
            dependencies.navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(
                    KairoSavedStateKeys.RSVP_RESULT_TEMPO_MS,
                    resumePoint.tempoMsPerWord,
                )
            dependencies.navController.popBackStack()
        },
        onPositionChanged = { resumePoint ->
            val safeIndex =
                if (dependencies.tokens.isNotEmpty()) {
                    resumePoint.tokenIndex.coerceIn(0, dependencies.tokens.lastIndex)
                } else {
                    0
                }
            dependencies.backStackEntry.savedStateHandle[
                KairoSavedStateKeys.RSVP_CURRENT_TOKEN_INDEX
            ] =
                safeIndex
            dependencies.backStackEntry.savedStateHandle[
                KairoSavedStateKeys.RSVP_CURRENT_RESUME_CURSOR
            ] =
                resumePoint.resumeCursor
            val wordIndex = resolveWordIndex(dependencies.wordCountByToken, safeIndex)
            dependencies.saveRsvpPosition(
                dependencies.chapterIndex,
                safeIndex,
                wordIndex,
                resumePoint.resumeCursor,
            )
        },
        onTempoChange = { tempoMsPerWord ->
            val baseTempoMs =
                RsvpConfigResolver.toBaseTempoMs(
                    tempoMsPerWord,
                    dependencies.languageTag,
                )
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateRsvpTempoMsPerWord(baseTempoMs)
            }
        },
        onExit = { resumePoint ->
            dependencies.onSessionFinished(resumePoint.tokenIndex)
            val resumeIndex = dependencies.safeResumeIndex(resumePoint.tokenIndex)
            val wordIndex = resolveWordIndex(dependencies.wordCountByToken, resumeIndex)
            dependencies.saveRsvpPosition(
                dependencies.chapterIndex,
                resumeIndex,
                wordIndex,
                resumePoint.resumeCursor,
            )
            dependencies.persistLiveTempo(resumePoint.tempoMsPerWord)
            dependencies.navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(KairoSavedStateKeys.RSVP_RESULT_CHAPTER_INDEX, dependencies.chapterIndex)
            dependencies.navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(KairoSavedStateKeys.RSVP_RESULT_TOKEN_INDEX, resumeIndex)
            dependencies.navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(
                    KairoSavedStateKeys.RSVP_RESULT_RESUME_CURSOR,
                    resumePoint.resumeCursor,
                )
            dependencies.navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(
                    KairoSavedStateKeys.RSVP_RESULT_TEMPO_MS,
                    resumePoint.tempoMsPerWord,
                )
            dependencies.navController.popBackStack()
        },
        onOpenLibrary = { resumePoint ->
            dependencies.onSessionFinished(resumePoint.tokenIndex)
            val resumeIndex = dependencies.safeResumeIndex(resumePoint.tokenIndex)
            val wordIndex = resolveWordIndex(dependencies.wordCountByToken, resumeIndex)
            dependencies.saveRsvpPosition(
                dependencies.chapterIndex,
                resumeIndex,
                wordIndex,
                resumePoint.resumeCursor,
            )
            dependencies.navController.navigate(KairoRoutes.LIBRARY) {
                popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
                launchSingleTop = true
            }
        },
        onPlaybackStateChanged = { isPlaying ->
            dependencies.onSessionActiveChanged(isPlaying)
            dependencies.backStackEntry.savedStateHandle[
                KairoSavedStateKeys.RSVP_PLAYBACK_IS_PLAYING
            ] =
                isPlaying
        },
        onFrameConsumed = frameConsumed@{ frame ->
            val completed = completedFrameProgress(frame, dependencies.tokens)
            val consumedTokenIndex =
                completed.lastFullyConsumedOriginalTokenIndex ?: return@frameConsumed
            val safeTokenIndex =
                if (dependencies.tokens.isEmpty()) {
                    consumedTokenIndex.coerceAtLeast(0)
                } else {
                    consumedTokenIndex.coerceIn(0, dependencies.tokens.lastIndex)
                }
            dependencies.container.readingSessionCoordinator.consumeTimedFrame(
                bookId = dependencies.bookIdValue,
                mode = dependencies.sessionMode,
                location =
                ReadingSessionLocation(
                    chapterIndex = dependencies.chapterIndex,
                    tokenIndex = safeTokenIndex,
                    wordIndex = resolveWordIndex(dependencies.wordCountByToken, safeTokenIndex),
                ),
                words = completed.words,
            )
        },
    )

private fun RsvpRouteCallbackDependencies.persistLiveTempo(tempoMsPerWord: Long) {
    if (tempoMsPerWord <= 0L) return
    val baseTempoMs =
        RsvpConfigResolver.toBaseTempoMs(
            tempoMsPerWord,
            languageTag,
        )
    coroutineScope.launch {
        container.preferencesRepository.updateRsvpTempoMsPerWord(baseTempoMs)
    }
}

private fun buildRsvpPreferenceCallbacks(
    dependencies: RsvpRouteCallbackDependencies,
): RsvpPreferenceCallbacks =
    RsvpPreferenceCallbacks(
        onExtremeSpeedUnlockedChange = { enabled ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateUnlockExtremeSpeed(enabled)
            }
        },
        onSelectProfile = { profileId ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.selectRsvpProfile(profileId)
            }
        },
        onSaveCustomProfile = { name, config ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.saveRsvpCustomProfile(name, config)
            }
        },
        onDeleteCustomProfile = { profileId ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.deleteRsvpCustomProfile(profileId)
            }
        },
        onRsvpConfigChange = { updater ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateRsvpConfig(updater)
            }
        },
    )

private fun buildRsvpUiCallbacks(
    dependencies: RsvpRouteCallbackDependencies,
): RsvpUiCallbacks =
    RsvpUiCallbacks(
        onFocusModeEnabledChange = { enabled ->
            dependencies.coroutineScope.launch {
                if (enabled) {
                    if (!dependencies.prefs.focusModeEnabled) {
                        dependencies.container.preferencesRepository.updateFocusModeEnabled(true)
                    }
                    dependencies.container.preferencesRepository.updateFocusApplyInRsvp(true)
                } else {
                    dependencies.container.preferencesRepository.updateFocusApplyInRsvp(false)
                }
            }
        },
        onRsvpFontSizeChange = { size ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateRsvpFontSize(size)
            }
        },
        onRsvpTextBrightnessChange = { brightness ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateRsvpTextBrightness(brightness)
            }
        },
        onRsvpFontWeightChange = { weight ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateRsvpFontWeight(weight)
            }
        },
        onRsvpFontFamilyChange = { family ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateRsvpFontFamily(family)
            }
        },
        onPositioningGridEnabledChange = { enabled ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository
                    .updateRsvpPositioningGridEnabled(enabled)
            }
        },
        onPositioningGridSnapChange = { snap ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateRsvpPositioningGridSnap(snap)
            }
        },
    )

private fun buildRsvpThemeCallbacks(
    dependencies: RsvpRouteCallbackDependencies,
): RsvpThemeCallbacks =
    RsvpThemeCallbacks(
        onThemeChange = { theme ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateTheme(theme.name)
            }
        },
        onVerticalBiasChange = { bias ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateRsvpVerticalBias(bias)
            }
        },
        onHorizontalBiasChange = { bias ->
            dependencies.coroutineScope.launch {
                dependencies.container.preferencesRepository.updateRsvpHorizontalBias(bias)
            }
        },
    )

private fun RsvpRouteCallbackDependencies.safeResumeIndex(tokenIndex: Int): Int =
    if (tokens.isNotEmpty()) {
        tokenIndex.coerceIn(0, tokens.lastIndex)
    } else {
        tokenIndex.coerceAtLeast(0)
    }
