package com.adshield.app.filter

/**
 * Decides whether a hostname should be blocked.
 *
 * Matching is suffix based: a rule for `example.com` also covers `ads.example.com`,
 * because hosts-style lists usually only name the network domain.
 */
object FilterEngine {

    enum class Action { ALLOW, BLOCK }

    @Volatile private var blocks: Set<String> = emptySet()
    @Volatile private var allow: Set<String> = emptySet()
    @Volatile private var deny: Set<String> = emptySet()

    fun update(blockList: Set<String>, whitelist: Set<String>, blacklist: Set<String>) {
        blocks = blockList
        allow = whitelist
        deny = blacklist
    }

    fun decide(domain: String): Action {
        val d = domain.trim().trimEnd('.').lowercase()
        if (d.isEmpty()) return Action.ALLOW
        val localAllow = allow
        if (localAllow.isNotEmpty() && suffixMatch(localAllow, d)) return Action.ALLOW
        val localDeny = deny
        if (localDeny.isNotEmpty() && suffixMatch(localDeny, d)) return Action.BLOCK
        val localBlocks = blocks
        if (localBlocks.isNotEmpty() && suffixMatch(localBlocks, d)) return Action.BLOCK
        return Action.ALLOW
    }

    fun isBlocked(domain: String): Boolean = decide(domain) == Action.BLOCK

    fun isWhitelisted(domain: String): Boolean = suffixMatch(allow, domain.trim().trimEnd('.').lowercase())

    fun ruleCount(): Int = blocks.size + deny.size

    private fun suffixMatch(set: Set<String>, domain: String): Boolean {
        if (set.isEmpty()) return false
        var current: String? = domain
        while (current != null) {
            if (set.contains(current)) return true
            val dot = current.indexOf('.')
            current = if (dot >= 0) current.substring(dot + 1) else null
        }
        return false
    }
}
