package com.kairo.reader.ui.focus

import android.app.NotificationManager
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusDndDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun foregroundFocusAllowsMediaAndReleasesDndOnBackgroundAndDisable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        assumeTrue(manager.isNotificationPolicyAccessGranted)
        assumeTrue(manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL)
        val wasMediaMuted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val enabled = mutableStateOf(true)
        try {
            compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
            compose.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    FocusDndSideEffect(enabled.value)
                }
            }
            awaitFilter(manager, NotificationManager.INTERRUPTION_FILTER_ALARMS)
            assertEquals(wasMediaMuted, audio.isStreamMute(AudioManager.STREAM_MUSIC))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                assertFalse(
                    manager.consolidatedNotificationPolicy.priorityCategories and
                        NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA == 0
                )
            }
            compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
            awaitFilter(manager, NotificationManager.INTERRUPTION_FILTER_ALL)
            compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
            awaitFilter(manager, NotificationManager.INTERRUPTION_FILTER_ALARMS)
            compose.runOnIdle { enabled.value = false }
            awaitFilter(manager, NotificationManager.INTERRUPTION_FILTER_ALL)
        } finally {
            compose.runOnIdle { owner.registry.currentState = Lifecycle.State.DESTROYED }
            FocusDndController(AndroidFocusDndBackend(context)).recover()
        }
    }

    private fun awaitFilter(manager: NotificationManager, expected: Int) {
        compose.waitUntil(5_000) { manager.currentInterruptionFilter == expected }
    }
}
