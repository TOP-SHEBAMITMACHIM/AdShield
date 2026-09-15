package com.adshield.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User managed whitelist and blacklist rules. */
class RulesStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("adshield_rules", Context.MODE_PRIVATE)

    private val _whitelist = MutableStateFlow(load(KEY_WHITELIST))
    private val _blacklist = MutableStateFlow(load(KEY_BLACKLIST))

    val whitelistFlow: StateFlow<Set<String>> = _whitelist.asStateFlow()
    val blacklistFlow: StateFlow<Set<String>> = _blacklist.asStateFlow()

    fun whitelist(): Set<String> = _whitelist.value
    fun blacklist(): Set<String> = _blacklist.value

    /** Returns false when the input is not a usable domain. */
    fun addWhitelist(input: String): Boolean {
        val domain = normalizeDomain(input) ?: return false
        val next = _whitelist.value.toMutableSet().apply { add(domain) }
        _whitelist.value = next
        prefs.edit { putStringSet(KEY_WHITELIST, next) }
        return true
    }

    fun addBlacklist(input: String): Boolean {
        val domain = normalizeDomain(input) ?: return false
        val next = _blacklist.value.toMutableSet().apply { add(domain) }
        _blacklist.value = next
        prefs.edit { putStringSet(KEY_BLACKLIST, next) }
        return true
    }

    fun removeWhitelist(domain: String) {
        val next = _whitelist.value.toMutableSet().apply { remove(domain) }
        _whitelist.value = next
        prefs.edit { putStringSet(KEY_WHITELIST, next) }
    }

    fun removeBlacklist(domain: String) {
        val next = _blacklist.value.toMutableSet().apply { remove(domain) }
        _blacklist.value = next
        prefs.edit { putStringSet(KEY_BLACKLIST, next) }
    }

    private fun load(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.mapNotNull { normalizeDomain(it) }?.toSet() ?: emptySet()

    companion object {
        private const val KEY_WHITELIST = "whitelist"
        private const val KEY_BLACKLIST = "blacklist"

        /** Accepts bare domains, URLs and wildcard prefixes and returns a clean hostname. */
        fun normalizeDomain(input: String): String? {
            var value = input.trim().lowercase()
            if (value.isEmpty()) return null
            val scheme = value.indexOf("://")
            if (scheme >= 0) value = value.substring(scheme + 3)
            val slash = value.indexOf('/')
            if (slash >= 0) value = value.substring(0, slash)
            val colon = value.indexOf(':')
            if (colon >= 0) value = value.substring(0, colon)
            value = value.removePrefix("*.").removePrefix("www.").trimEnd('.')
            if (value.isEmpty() || value.length < 4 || value.length > 253) return null
            if (!value.contains('.')) return null
            if (value.startsWith('.') || value.endsWith('-')) return null
            val allowed = value.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' || it == '_' }
            if (!allowed) return null
            return value
        }
    }
}
