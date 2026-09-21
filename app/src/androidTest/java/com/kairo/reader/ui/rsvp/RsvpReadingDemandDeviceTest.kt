package com.kairo.reader.ui.rsvp

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.ComprehensionRsvpEngine
import com.kairo.reader.core.rsvp.RsvpGenerationOptions
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RsvpReadingDemandDeviceTest {
    @Test
    fun packagedLookupSupportsEnglishPlaybackAndRepeatedPreparationIsDeterministic() {
        val words = listOf("the", "people", "considered", "quizzacious", "12345", "neuroplasticity", "again")
        val tokens = List(5000) { Token(words[it % words.size], TokenType.WORD) }
        val config = RsvpConfig(
            enablePhraseChunking = false,
            maxChunkLength = 32,
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
        )
        val engine = ComprehensionRsvpEngine()
        val options = RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH)
        val times = mutableListOf<Long>()
        var expectedDurations: List<Long>? = null
        repeat(3) {
            val before = SystemClock.elapsedRealtimeNanos()
            val frames = engine.generateFrames(tokens, 0, config, options)
            times += (SystemClock.elapsedRealtimeNanos() - before) / 1_000_000L
            assertEquals(tokens.indices.toList(), frames.map { it.originalTokenIndex }.distinct())
            assertTrue(frames[3].protectedWordMs > frames[0].protectedWordMs)
            val durations = frames.map { frame -> frame.durationMs }
            expectedDurations?.let { expected -> assertEquals(expected, durations) }
            expectedDurations = durations
        }
        Log.i("RsvpDemandBenchmark", "5000 English words, full generation: first=${times.first()} ms, warm=${times.drop(1)} ms")
    }
}
