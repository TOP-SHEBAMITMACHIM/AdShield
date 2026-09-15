package com.adshield.app.core

/**
 * Picks the hosts that the floating panel offers for one tap blocking.
 *
 * The panel exists for the ad that slipped past the lists, so it works from lookups that were
 * allowed, newest first, and leaves out everything that would be a useless button: hosts the
 * user whitelisted on purpose, hosts that are blocked anyway, and names that are not hosts at
 * all (single labels, raw addresses, `.local` noise).
 */
object PanelCandidates {

    const val MAX_OFFERED = 12

    private val NOT_A_HOST_SUFFIXES = listOf(".local", ".arpa", ".invalid", ".test", ".example")

    fun offered(
        recent: List<EngineState.LogEntry>,
        whitelist: Set<String>,
        blacklist: Set<String>,
        max: Int = MAX_OFFERED
    ): List<String> {
        if (max <= 0) return emptyList()
        val out = LinkedHashSet<String>()
        for (entry in recent) {
            if (entry.blocked) continue
            val host = entry.domain.trim().trimEnd('.').lowercase()
            if (!isHost(host)) continue
            if (matches(whitelist, host) || matches(blacklist, host)) continue
            out.add(host)
            if (out.size >= max) break
        }
        return out.toList()
    }

    /** True when the name looks like a hostname a user could reasonably block. */
    fun isHost(host: String): Boolean {
        if (host.length < 4 || host.length > 253) return false
        if (!host.contains('.')) return false
        if (host.startsWith('.') || host.startsWith('-') || host.endsWith('-') || host.endsWith('.')) {
            return false
        }
        if (host.all { it in '0'..'9' || it == '.' }) return false
        if (NOT_A_HOST_SUFFIXES.any { host.endsWith(it) }) return false
        return host.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' || it == '_' }
    }

    /** Suffix match, so a rule for `example.com` also covers `ads.example.com`. */
    private fun matches(rules: Set<String>, host: String): Boolean {
        if (rules.isEmpty()) return false
        var current: String? = host
        while (current != null) {
            if (rules.contains(current)) return true
            val dot = current.indexOf('.')
            current = if (dot >= 0) current.substring(dot + 1) else null
        }
        return false
    }
}
