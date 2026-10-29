package com.kairo.reader.ui.rsvp

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.ui.theme.KairoTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RsvpCompletionDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<TestActivity>()

    @Test
    fun lifecycleStopDoesNotSaveOldPositionAfterCompletion() {
        val fixture = RsvpDeviceFixture(
            listOf(Token("First", TokenType.WORD), Token("Last", TokenType.WORD)),
            RsvpConfig(tempoMsPerWord = 80L, blinkMode = BlinkMode.OFF, startDelayMs = 0L),
        )
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val saveCount = AtomicInteger()
        val callbacks = fixture.callbacks.copy(
            playback = fixture.callbacks.playback.copy(onPositionChanged = { saveCount.incrementAndGet() }),
        )
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                KairoTheme { RsvpScreen(fixture.state, callbacks, fixture.dependencies) }
            }
        }
        rule.waitUntil(20_000) { fixture.finished }
        rule.runOnIdle {
            val beforeStop = saveCount.get()
            owner.registry.currentState = Lifecycle.State.CREATED
            assertEquals(beforeStop, saveCount.get())
        }
    }
}
