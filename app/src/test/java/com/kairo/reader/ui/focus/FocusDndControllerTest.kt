package com.kairo.reader.ui.focus

import android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.app.NotificationManager.INTERRUPTION_FILTER_NONE
import android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusDndControllerTest {
    @Test
    fun modernSessionAllowsMediaAndDeactivatesOnlyItsRuleAfterLastOwnerLeaves() {
        val backend = FakeBackend(appScoped = true, currentFilter = INTERRUPTION_FILTER_PRIORITY)
        val controller = FocusDndController(backend)
        val reader = Any()
        val rsvp = Any()
        controller.acquire(reader)
        controller.acquire(rsvp)
        controller.release(reader)
        assertEquals(listOf(INTERRUPTION_FILTER_ALARMS), backend.calls)
        controller.release(rsvp)
        assertEquals(listOf(INTERRUPTION_FILTER_ALARMS, INTERRUPTION_FILTER_ALL), backend.calls)
        assertNull(backend.pendingRestore)
    }

    @Test
    fun legacySessionRestoresItsPreviousFilter() {
        val backend = FakeBackend(appScoped = false)
        val controller = FocusDndController(backend)
        val owner = Any()
        controller.acquire(owner)
        assertEquals(INTERRUPTION_FILTER_ALL, backend.pendingRestore)
        controller.release(owner)
        assertEquals(INTERRUPTION_FILTER_ALL, backend.currentFilter)
        assertNull(backend.pendingRestore)
    }

    @Test
    fun legacySessionPreservesExistingDndAndSubsequentUserChanges() {
        listOf(INTERRUPTION_FILTER_PRIORITY, INTERRUPTION_FILTER_NONE, INTERRUPTION_FILTER_ALARMS).forEach { filter ->
            val backend = FakeBackend(appScoped = false, currentFilter = filter)
            val controller = FocusDndController(backend)
            controller.acquire(this)
            controller.release(this)
            assertTrue(backend.calls.isEmpty())
        }
        val backend = FakeBackend(appScoped = false)
        val controller = FocusDndController(backend)
        controller.acquire(this)
        backend.currentFilter = INTERRUPTION_FILTER_NONE
        controller.release(this)
        assertEquals(INTERRUPTION_FILTER_NONE, backend.currentFilter)
    }

    @Test
    fun restartRecoversAbandonedModernAndLegacySessions() {
        listOf(false, true).forEach { modern ->
            val backend = FakeBackend(appScoped = modern)
            FocusDndController(backend).acquire(this)
            FocusDndController(backend).recover()
            assertEquals(INTERRUPTION_FILTER_ALL, backend.currentFilter)
            assertNull(backend.pendingRestore)
        }
    }

    @Test
    fun modernStartupAlsoClearsRulesLeftByOlderAppVersionsWithoutAMarker() {
        val backend = FakeBackend(appScoped = true, currentFilter = INTERRUPTION_FILTER_NONE)
        FocusDndController(backend).recover()
        assertEquals(listOf(INTERRUPTION_FILTER_ALL), backend.calls)
    }

    @Test
    fun permissionRevocationDoesNotCrashAndCleanupRetriesAfterAccessReturns() {
        val backend = FakeBackend(appScoped = true)
        val controller = FocusDndController(backend)
        controller.acquire(this)
        backend.throwSecurityException = true
        controller.release(this)
        assertEquals(INTERRUPTION_FILTER_ALL, backend.pendingRestore)
        backend.throwSecurityException = false
        controller.recover()
        assertNull(backend.pendingRestore)
        backend.hasAccess = false
        backend.calls.clear()
        controller.acquire(this)
        controller.release(this)
        assertTrue(backend.calls.isEmpty())
    }

    private class FakeBackend(override val appScoped: Boolean, override var currentFilter: Int = INTERRUPTION_FILTER_ALL,) :
        FocusDndBackend {
        override var hasAccess = true
        override var pendingRestore: Int? = null
        var throwSecurityException = false
        val calls = mutableListOf<Int>()
        override fun setFilter(filter: Int) {
            if (throwSecurityException) throw SecurityException("Access revoked")
            calls += filter
            currentFilter = filter
        }
    }
}
