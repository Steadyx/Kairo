package com.kairo.reader.ui.updates

internal enum class InAppUpdatePrompt {
    UPDATE_AVAILABLE,
    READY_TO_RESTART,
}

internal enum class InAppUpdateCheckResult {
    AVAILABLE,
    UP_TO_DATE,
    UNAVAILABLE,
    FAILED,
}

internal fun selectInAppUpdateCheckResult(
    prompt: InAppUpdatePrompt?,
    updateNotAvailable: Boolean,
): InAppUpdateCheckResult =
    when {
        prompt != null -> InAppUpdateCheckResult.AVAILABLE
        updateNotAvailable -> InAppUpdateCheckResult.UP_TO_DATE
        else -> InAppUpdateCheckResult.UNAVAILABLE
    }

internal data class InAppUpdateSnapshot(val updateAvailable: Boolean, val flexibleUpdateAllowed: Boolean, val updateDownloaded: Boolean,)

internal fun selectInAppUpdatePrompt(
    snapshot: InAppUpdateSnapshot,
    availablePromptSuppressed: Boolean,
): InAppUpdatePrompt? =
    when {
        snapshot.updateDownloaded -> InAppUpdatePrompt.READY_TO_RESTART
        snapshot.updateAvailable &&
            snapshot.flexibleUpdateAllowed &&
            !availablePromptSuppressed -> InAppUpdatePrompt.UPDATE_AVAILABLE
        else -> null
    }
