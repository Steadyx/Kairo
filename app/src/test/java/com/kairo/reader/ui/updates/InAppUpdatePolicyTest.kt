package com.kairo.reader.ui.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InAppUpdatePolicyTest {
    @Test
    fun updateCheckDoesNotSayUpToDateWhenPlayCannotOfferTheUpdate() {
        assertEquals(
            InAppUpdateCheckResult.UNAVAILABLE,
            selectInAppUpdateCheckResult(prompt = null, updateNotAvailable = false),
        )
        assertEquals(
            InAppUpdateCheckResult.UP_TO_DATE,
            selectInAppUpdateCheckResult(prompt = null, updateNotAvailable = true),
        )
        assertEquals(
            InAppUpdateCheckResult.AVAILABLE,
            selectInAppUpdateCheckResult(InAppUpdatePrompt.READY_TO_RESTART, updateNotAvailable = false),
        )
    }

    @Test
    fun selectInAppUpdatePrompt_offersFlexibleAvailableUpdate() {
        val prompt =
            selectInAppUpdatePrompt(
                snapshot =
                InAppUpdateSnapshot(
                    updateAvailable = true,
                    flexibleUpdateAllowed = true,
                    updateDownloaded = false,
                ),
                availablePromptSuppressed = false,
            )

        assertEquals(InAppUpdatePrompt.UPDATE_AVAILABLE, prompt)
    }

    @Test
    fun selectInAppUpdatePrompt_doesNotOfferUnavailableOrDisallowedUpdate() {
        assertNull(
            selectInAppUpdatePrompt(
                snapshot =
                InAppUpdateSnapshot(
                    updateAvailable = false,
                    flexibleUpdateAllowed = true,
                    updateDownloaded = false,
                ),
                availablePromptSuppressed = false,
            )
        )
        assertNull(
            selectInAppUpdatePrompt(
                snapshot =
                InAppUpdateSnapshot(
                    updateAvailable = true,
                    flexibleUpdateAllowed = false,
                    updateDownloaded = false,
                ),
                availablePromptSuppressed = false,
            )
        )
    }

    @Test
    fun selectInAppUpdatePrompt_respectsSessionDismissal() {
        val prompt =
            selectInAppUpdatePrompt(
                snapshot =
                InAppUpdateSnapshot(
                    updateAvailable = true,
                    flexibleUpdateAllowed = true,
                    updateDownloaded = false,
                ),
                availablePromptSuppressed = true,
            )

        assertNull(prompt)
    }

    @Test
    fun selectInAppUpdatePrompt_alwaysOffersDownloadedUpdateRestart() {
        val prompt =
            selectInAppUpdatePrompt(
                snapshot =
                InAppUpdateSnapshot(
                    updateAvailable = false,
                    flexibleUpdateAllowed = false,
                    updateDownloaded = true,
                ),
                availablePromptSuppressed = true,
            )

        assertEquals(InAppUpdatePrompt.READY_TO_RESTART, prompt)
    }
}
