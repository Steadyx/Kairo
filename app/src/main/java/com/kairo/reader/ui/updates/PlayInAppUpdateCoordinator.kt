package com.kairo.reader.ui.updates

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

internal enum class InAppUpdateAction {
    START_UPDATE,
    RESTART_TO_INSTALL,
}

internal class PlayInAppUpdateCoordinator(activity: Activity, private val onPromptChanged: (InAppUpdatePrompt?) -> Unit,) {
    private val updateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private val updateOptions = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()

    // Play Core still includes the deprecated REQUIRES_UI_INTENT value in its InstallStatus set.
    @Suppress("DEPRECATION")
    private val installStateListener =
        InstallStateUpdatedListener { state ->
            when (state.installStatus()) {
                InstallStatus.DOWNLOADED -> publishPrompt(InAppUpdatePrompt.READY_TO_RESTART)
                InstallStatus.CANCELED, InstallStatus.FAILED, InstallStatus.INSTALLED -> publishPrompt(null)
                InstallStatus.DOWNLOADING,
                InstallStatus.INSTALLING,
                InstallStatus.PENDING,
                InstallStatus.REQUIRES_UI_INTENT,
                InstallStatus.UNKNOWN,
                -> Unit
            }
        }

    private var updateFlowLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var availablePromptSuppressed = false
    private var currentPrompt: InAppUpdatePrompt? = null

    fun start(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        updateFlowLauncher = launcher
        updateManager.registerListener(installStateListener)
        refreshUpdateState()
    }

    fun stop() {
        updateManager.unregisterListener(installStateListener)
        updateFlowLauncher = null
    }

    fun refreshUpdateState() {
        requestUpdateState()
    }

    fun checkForUpdates(onResult: (InAppUpdateCheckResult) -> Unit) {
        availablePromptSuppressed = false
        requestUpdateState(onResult)
    }

    private fun requestUpdateState(onResult: ((InAppUpdateCheckResult) -> Unit)? = null) {
        updateManager.appUpdateInfo
            .addOnSuccessListener { updateInfo ->
                val prompt =
                    selectInAppUpdatePrompt(
                        snapshot =
                        InAppUpdateSnapshot(
                            updateAvailable =
                            updateInfo.updateAvailability() ==
                                UpdateAvailability.UPDATE_AVAILABLE,
                            flexibleUpdateAllowed = updateInfo.isUpdateTypeAllowed(updateOptions),
                            updateDownloaded = updateInfo.installStatus() == InstallStatus.DOWNLOADED,
                        ),
                        availablePromptSuppressed = availablePromptSuppressed,
                    )
                publishPrompt(prompt)
                onResult?.invoke(
                    if (prompt == null) {
                        InAppUpdateCheckResult.UP_TO_DATE
                    } else {
                        InAppUpdateCheckResult.AVAILABLE
                    }
                )
            }.addOnFailureListener {
                onResult?.invoke(InAppUpdateCheckResult.FAILED)
            }
    }

    fun perform(action: InAppUpdateAction) {
        when (action) {
            InAppUpdateAction.START_UPDATE -> startFlexibleUpdate()
            InAppUpdateAction.RESTART_TO_INSTALL -> completeUpdate()
        }
    }

    fun dismiss(prompt: InAppUpdatePrompt) {
        if (prompt == InAppUpdatePrompt.UPDATE_AVAILABLE) {
            availablePromptSuppressed = true
        }
        publishPrompt(null)
    }

    fun onUpdateFlowResult(resultCode: Int) {
        if (resultCode != Activity.RESULT_OK) {
            availablePromptSuppressed = true
        }
        publishPrompt(null)
    }

    private fun startFlexibleUpdate() {
        val launcher = updateFlowLauncher ?: return
        publishPrompt(null)
        updateManager.appUpdateInfo
            .addOnSuccessListener { updateInfo ->
                val updateCanStart =
                    updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        updateInfo.isUpdateTypeAllowed(updateOptions)
                if (!updateCanStart ||
                    !updateManager.startUpdateFlowForResult(updateInfo, launcher, updateOptions)
                ) {
                    availablePromptSuppressed = true
                }
            }.addOnFailureListener {
                availablePromptSuppressed = true
            }
    }

    private fun completeUpdate() {
        publishPrompt(null)
        updateManager.completeUpdate()
    }

    private fun publishPrompt(prompt: InAppUpdatePrompt?) {
        if (currentPrompt == prompt) {
            return
        }
        currentPrompt = prompt
        onPromptChanged(prompt)
    }
}
