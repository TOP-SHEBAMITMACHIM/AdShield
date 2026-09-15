package com.adshield.app.core

/**
 * Rules for the short "an ad was blocked" message.
 *
 * The tunnel can decide dozens of lookups within a second while an app loads, so a plain
 * one-message-per-block policy would bury whatever is on screen. Every decision goes through
 * [shouldShow], which keeps this object free of Android types so the rules stay unit testable.
 */
object BlockedToastPolicy {

    /** Shortest gap between two messages. */
    const val MIN_GAP_MS = 4_000L

    /** Longest hostname the message shows before it is shortened. */
    const val MAX_DOMAIN_LENGTH = 36

    fun shouldShow(
        enabled: Boolean,
        appVisible: Boolean,
        screenOn: Boolean,
        nowMs: Long,
        lastShownMs: Long,
        gapMs: Long = MIN_GAP_MS
    ): Boolean {
        if (!enabled) return false
        // The dashboard already scrolls a live feed of blocked hosts, so a message would only
        // cover up the screen the user is looking at.
        if (appVisible) return false
        // Nobody is watching, and a queued message would only pop up late and out of context.
        if (!screenOn) return false
        return nowMs - lastShownMs >= gapMs.coerceAtLeast(0L)
    }

    /**
     * Shortens a hostname to one line. Subdomain labels are dropped first, because the useful
     * part of `ads.tracker.example.com` is the registrable domain at the end.
     */
    fun shorten(domain: String, maxLength: Int = MAX_DOMAIN_LENGTH): String {
        if (maxLength < 2) return ""
        var value = domain.trim().trimEnd('.').lowercase()
        if (value.length <= maxLength) return value
        while (value.length > maxLength) {
            val dot = value.indexOf('.')
            if (dot <= 0) break
            val rest = value.substring(dot + 1)
            // Stop before the label that is left is only a public suffix: `com` on its own
            // tells the user nothing about what was blocked.
            if (!rest.contains('.')) break
            value = rest
        }
        if (value.length <= maxLength) return value
        return "…" + value.takeLast(maxLength - 1)
    }
}
