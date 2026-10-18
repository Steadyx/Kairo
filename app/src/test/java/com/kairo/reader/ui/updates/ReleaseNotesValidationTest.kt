package com.kairo.reader.ui.updates

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesValidationTest {
    private val published = ReleaseNotes("1.3.0", 15, listOf(1))
    private val upcoming = ReleaseNotes(null, null, listOf(2))

    @Test
    fun acceptsPublishedVersionAlongsideUpcomingNotes() {
        validatePublishedReleaseNotes("1.3.0", 15, listOf(upcoming, published))
    }

    @Test
    fun rejectsVersionBumpWithOnlyOldAndUpcomingNotes() {
        val error = assertThrows(IllegalStateException::class.java) {
            validatePublishedReleaseNotes("1.4.0", 16, listOf(upcoming, published))
        }
        assertTrue(error.message.orEmpty().contains("versionName=1.4.0, versionCode=16"))
        assertTrue(error.message.orEmpty().contains("Finalise the Upcoming entry"))
    }

    @Test
    fun acceptsFinalisedReleaseWithoutATour() {
        val finalised = upcoming.copy(version = "1.4.0", versionCode = 16)
        validatePublishedReleaseNotes("1.4.0", 16, listOf(finalised, published))
    }

    @Test
    fun rejectsNameOnlyAndCodeOnlyVersionChanges() {
        assertThrows(IllegalStateException::class.java) {
            validatePublishedReleaseNotes("1.4.0", 15, listOf(published))
        }
        assertThrows(IllegalStateException::class.java) {
            validatePublishedReleaseNotes("1.3.0", 16, listOf(published))
        }
    }

    @Test
    fun requiresBothFieldsOnTheSamePublishedEntry() {
        val partialNotes = listOf(
            upcoming.copy(version = "1.4.0"),
            upcoming.copy(versionCode = 16),
            published,
        )
        assertThrows(IllegalStateException::class.java) {
            validatePublishedReleaseNotes("1.4.0", 16, partialNotes)
        }
    }

    @Test
    fun rejectsEmptyUnpublishedOnlyAndDuplicateCatalogs() {
        listOf(emptyList(), listOf(upcoming), listOf(published, published)).forEach { notes ->
            assertThrows(IllegalStateException::class.java) {
                validatePublishedReleaseNotes("1.3.0", 15, notes)
            }
        }
    }
}
