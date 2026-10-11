package com.kairo.reader.ui.updates

import androidx.annotation.StringRes
import com.kairo.reader.R
import com.kairo.reader.ui.tutorial.StartingTutorialRoute
import com.kairo.reader.ui.tutorial.StartingTutorialStep
import com.kairo.reader.ui.tutorial.StartingTutorialTargetIds

internal data class ReleaseNotes(
    val version: String?,
    val versionCode: Int?,
    @param:StringRes val changes: List<Int>,
    val tour: List<StartingTutorialStep> = emptyList(),
)

// Set both version fields during release preparation; unpublished notes never trigger an update prompt.
@Suppress("MagicNumber") // Android version codes from the corresponding release tags.
internal val releaseNotes = listOf(
    ReleaseNotes(
        version = null,
        versionCode = null,
        changes = listOf(
            R.string.release_upcoming_themes,
            R.string.release_upcoming_reading,
            R.string.release_upcoming_guidance,
            R.string.release_upcoming_progress,
            R.string.release_upcoming_imports,
            R.string.release_upcoming_search_focus,
            R.string.release_upcoming_helper,
        ),
        tour = listOf(
            StartingTutorialStep(
                StartingTutorialRoute.SETTINGS,
                StartingTutorialTargetIds.SETTINGS_THEME,
                R.string.theme_settings_title,
                R.string.release_upcoming_themes,
            ),
            StartingTutorialStep(
                StartingTutorialRoute.SETTINGS,
                StartingTutorialTargetIds.SETTINGS_RSVP,
                R.string.rsvp_settings_title,
                R.string.release_upcoming_reading,
            ),
            StartingTutorialStep(
                StartingTutorialRoute.SETTINGS,
                StartingTutorialTargetIds.SETTINGS_RSVP,
                R.string.whats_new_word_guidance,
                R.string.release_upcoming_guidance,
            ),
            StartingTutorialStep(
                StartingTutorialRoute.SETTINGS,
                StartingTutorialTargetIds.SETTINGS_CHANGELOG,
                R.string.whats_new_title,
                R.string.release_upcoming_helper,
            ),
        ),
    ),
    ReleaseNotes("1.3.0", 15, listOf(R.string.release_130)),
    ReleaseNotes("1.2.0", 14, listOf(R.string.release_120)),
    ReleaseNotes("1.1.0", 12, listOf(R.string.release_110)),
    ReleaseNotes("1.0.8", 10, listOf(R.string.release_108)),
)

internal fun shouldOfferReleaseTour(
    releaseVersionCode: Int?,
    installedVersionCode: Int,
    lastSeenVersionCode: Int,
    hasSeenStartingTutorial: Boolean,
): Boolean =
    releaseVersionCode == installedVersionCode &&
        lastSeenVersionCode < installedVersionCode &&
        hasSeenStartingTutorial
