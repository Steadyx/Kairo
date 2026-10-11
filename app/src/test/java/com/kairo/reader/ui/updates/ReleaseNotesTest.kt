package com.kairo.reader.ui.updates

import com.kairo.reader.ui.tutorial.StartingTutorialRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun offersNewPublishedReleaseToExistingUsersIncludingMigrationAndSkippedVersions() {
        listOf(0, 10, 14).forEach { seen ->
            assertTrue(shouldOfferReleaseTour(15, 15, seen, hasSeenStartingTutorial = true))
        }
    }

    @Test
    fun doesNotOfferFreshInstallsAcknowledgedVersionsOrDowngrades() {
        assertFalse(shouldOfferReleaseTour(15, 15, 0, hasSeenStartingTutorial = false))
        assertFalse(shouldOfferReleaseTour(15, 15, 15, hasSeenStartingTutorial = true))
        assertFalse(shouldOfferReleaseTour(15, 15, 16, hasSeenStartingTutorial = true))
    }

    @Test
    fun unpublishedAndMismatchedNotesNeverAutoLaunch() {
        assertFalse(shouldOfferReleaseTour(null, 15, 0, hasSeenStartingTutorial = true))
        assertFalse(shouldOfferReleaseTour(16, 15, 0, hasSeenStartingTutorial = true))
        assertFalse(shouldOfferReleaseTour(14, 15, 0, hasSeenStartingTutorial = true))
    }

    @Test
    fun catalogHasUniqueDescendingVersionsAndCompleteNotes() {
        val published = releaseNotes.mapNotNull { it.versionCode }
        assertTrue(published == published.distinct().sortedDescending())
        assertTrue(releaseNotes.all { (it.version == null) == (it.versionCode == null) })
        assertTrue(releaseNotes.all { it.changes.isNotEmpty() })
        assertTrue(releaseNotes.count { it.versionCode == null } <= 1)
        assertTrue(releaseNotes.drop(1).all { it.versionCode != null })
    }

    @Test
    fun currentTourCanRunWithoutAnImportedBook() {
        assertTrue(
            releaseNotes.first().tour.all {
                it.route == StartingTutorialRoute.SETTINGS || it.route == StartingTutorialRoute.LIBRARY
            }
        )
    }
}
