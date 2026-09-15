package com.adshield.app.core

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single source of truth for everything the UI observes. The VPN service runs in the same
 * process, so a plain object of StateFlows is enough to make the UI live without IPC.
 */
object EngineState {
    data class LogEntry(val timeMs: Long, val domain: String, val blocked: Boolean)

    val isRunning = MutableStateFlow(false)
    val pausedUntil = MutableStateFlow(0L)
    val todayBlocked = MutableStateFlow(0L)
    val totalBlocked = MutableStateFlow(0L)
    val queriesToday = MutableStateFlow(0L)
    val rulesCount = MutableStateFlow(0)
    val recent = MutableStateFlow<List<LogEntry>>(emptyList())

    /**
     * Lookups that were allowed, newest first. Only the floating panel reads this, and it is
     * never written to disk: it exists so a host that slipped past the lists can be blocked in
     * one tap while the user is still looking at the page that showed the ad.
     */
    val allowedRecent = MutableStateFlow<List<LogEntry>>(emptyList())
    val topDomains = MutableStateFlow<List<Pair<String, Long>>>(emptyList())
    val week = MutableStateFlow(List(7) { 0L })
    val vpnError = MutableStateFlow<String?>(null)
    val listsLoaded = MutableStateFlow(false)

    /** True while MainActivity is on screen; the blocked-ad message stays quiet then. */
    val appVisible = MutableStateFlow(false)
}
