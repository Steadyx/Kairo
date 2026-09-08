package com.kairo.reader.ui.focus

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** Owns only Kairo's foreground restriction, including recovery after an interrupted session. */
internal class FocusDndController(private val backend: FocusDndBackend) {
    private val owners = mutableSetOf<Any>()

    fun recover() {
        if (!backend.hasAccess) return
        try {
            if (backend.appScoped) {
                // Android 15+: ALL disables our implicit rule, without changing other DND rules.
                backend.setFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            } else if (backend.pendingRestore != null &&
                backend.currentFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS
            ) {
                backend.setFilter(backend.pendingRestore!!)
            }
            backend.pendingRestore = null
        } catch (_: SecurityException) {
            // Access can be revoked between checking it and calling the platform API.
        }
    }

    fun acquire(owner: Any) {
        if (!owners.add(owner) || owners.size > 1 || !backend.hasAccess) return
        try {
            if (backend.pendingRestore != null) recover()
            val current = backend.currentFilter
            // Legacy Android exposes only a global filter. Preserve an existing user DND mode.
            if (!backend.appScoped && current != NotificationManager.INTERRUPTION_FILTER_ALL) return
            backend.pendingRestore = if (backend.appScoped) NotificationManager.INTERRUPTION_FILTER_ALL else current
            // Alarms-only suppresses notification interruptions while leaving media audio available.
            backend.setFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        } catch (_: SecurityException) {
            // Keep the recovery marker if permission disappeared during activation.
        }
    }

    fun release(owner: Any) {
        if (!owners.remove(owner) || owners.isNotEmpty()) return
        if (backend.pendingRestore != null) recover()
    }
}

internal interface FocusDndBackend {
    val hasAccess: Boolean
    val appScoped: Boolean
    val currentFilter: Int
    var pendingRestore: Int?
    fun setFilter(filter: Int)
}

internal class AndroidFocusDndBackend(context: Context) : FocusDndBackend {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val preferences = context.getSharedPreferences("focus_dnd_session", Context.MODE_PRIVATE)
    override val hasAccess: Boolean get() = manager.isNotificationPolicyAccessGranted
    override val appScoped: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    override val currentFilter: Int get() = manager.currentInterruptionFilter
    override var pendingRestore: Int?
        get() = if (preferences.contains(RESTORE_FILTER)) preferences.getInt(RESTORE_FILTER, 1) else null

        @SuppressLint("ApplySharedPref", "UseKtx")
        set(value) {
            // Check commit success before changing system state; KTX edit does not expose that result.
            // A process restart can then repair an abandoned session.
            val edit = preferences.edit()
            if (value == null) edit.remove(RESTORE_FILTER) else edit.putInt(RESTORE_FILTER, value)
            check(edit.commit()) { "Unable to persist Focus mode recovery state" }
        }

    override fun setFilter(filter: Int) = manager.setInterruptionFilter(filter)
}

private const val RESTORE_FILTER = "restore_filter"
