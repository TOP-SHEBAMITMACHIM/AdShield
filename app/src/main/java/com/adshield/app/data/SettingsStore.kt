package com.adshield.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("adshield_settings", Context.MODE_PRIVATE)

    private val _excluded = MutableStateFlow(prefs.getStringSet(KEY_EXCLUDED, emptySet())?.toSet() ?: emptySet())
    val excludedPackages: StateFlow<Set<String>> = _excluded.asStateFlow()

    private val _theme = MutableStateFlow(prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM)
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _blockedToast = MutableStateFlow(prefs.getBoolean(KEY_BLOCKED_TOAST, true))
    val blockedToast: StateFlow<Boolean> = _blockedToast.asStateFlow()

    var dnsMode: String
        get() = prefs.getString(KEY_DNS_MODE, DNS_SYSTEM) ?: DNS_SYSTEM
        set(value) {
            if (value == DNS_CUSTOM && customUdp.isBlank()) customUdp = "1.1.1.1"
            if (value == DNS_DOH_CUSTOM && customDoh.isBlank()) customDoh = DEFAULT_DOH
            prefs.edit { putString(KEY_DNS_MODE, value) }
        }

    var customUdp: String
        get() = prefs.getString(KEY_CUSTOM_UDP, "") ?: ""
        set(value) = prefs.edit { putString(KEY_CUSTOM_UDP, value.trim()) }

    var customDoh: String
        get() = prefs.getString(KEY_CUSTOM_DOH, "") ?: ""
        set(value) = prefs.edit { putString(KEY_CUSTOM_DOH, value.trim()) }

    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTOSTART, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTOSTART, value) }

    var autoUpdate: Boolean
        get() = prefs.getBoolean(KEY_AUTOUPDATE, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTOUPDATE, value) }

    var hijackResolvers: Boolean
        get() = prefs.getBoolean(KEY_HIJACK, true)
        set(value) = prefs.edit { putBoolean(KEY_HIJACK, value) }

    var blockDohHostnames: Boolean
        get() = prefs.getBoolean(KEY_DOH_HOSTS, true)
        set(value) = prefs.edit { putBoolean(KEY_DOH_HOSTS, value) }

    var pausedUntil: Long
        get() = prefs.getLong(KEY_PAUSED_UNTIL, 0L)
        set(value) = prefs.edit { putLong(KEY_PAUSED_UNTIL, value) }

    var browserStrict: Boolean
        get() = prefs.getBoolean(KEY_BROWSER_STRICT, true)
        set(value) = prefs.edit { putBoolean(KEY_BROWSER_STRICT, value) }

    var browserBlockThirdParty: Boolean
        get() = prefs.getBoolean(KEY_BROWSER_3P, false)
        set(value) = prefs.edit { putBoolean(KEY_BROWSER_3P, value) }

    var browserJavaScript: Boolean
        get() = prefs.getBoolean(KEY_BROWSER_JS, true)
        set(value) = prefs.edit { putBoolean(KEY_BROWSER_JS, value) }

    var browserDesktop: Boolean
        get() = prefs.getBoolean(KEY_BROWSER_DESKTOP, false)
        set(value) = prefs.edit { putBoolean(KEY_BROWSER_DESKTOP, value) }

    var browserUpgradeHttps: Boolean
        get() = prefs.getBoolean(KEY_BROWSER_HTTPS, true)
        set(value) = prefs.edit { putBoolean(KEY_BROWSER_HTTPS, value) }

    var browserLastUrl: String
        get() = prefs.getString(KEY_BROWSER_URL, "") ?: ""
        set(value) = prefs.edit { putString(KEY_BROWSER_URL, value) }

    var rootHostsInstalled: Boolean
        get() = prefs.getBoolean(KEY_ROOT_INSTALLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ROOT_INSTALLED, value) }

    fun setTheme(value: String) {
        _theme.value = value
        prefs.edit { putString(KEY_THEME, value) }
    }

    fun setBlockedToast(value: Boolean) {
        _blockedToast.value = value
        prefs.edit { putBoolean(KEY_BLOCKED_TOAST, value) }
    }

    fun isExcluded(packageName: String): Boolean = _excluded.value.contains(packageName)

    fun setExcluded(packageName: String, excluded: Boolean) {
        val next = _excluded.value.toMutableSet()
        if (excluded) next.add(packageName) else next.remove(packageName)
        _excluded.value = next
        prefs.edit { putStringSet(KEY_EXCLUDED, next) }
    }

    companion object {
        const val DNS_SYSTEM = "system"
        const val DNS_CLOUDFLARE = "cloudflare"
        const val DNS_GOOGLE = "google"
        const val DNS_QUAD9 = "quad9"
        const val DNS_ADGUARD = "adguard"
        const val DNS_CUSTOM = "custom"
        const val DNS_DOH_CLOUDFLARE = "doh_cloudflare"
        const val DNS_DOH_GOOGLE = "doh_google"
        const val DNS_DOH_ADGUARD = "doh_adguard"
        const val DNS_DOH_CUSTOM = "doh_custom"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val DEFAULT_DOH = "https://cloudflare-dns.com/dns-query"

        private const val KEY_DNS_MODE = "dns_mode"
        private const val KEY_CUSTOM_UDP = "dns_custom_udp"
        private const val KEY_CUSTOM_DOH = "dns_custom_doh"
        private const val KEY_AUTOSTART = "auto_start"
        private const val KEY_AUTOUPDATE = "auto_update"
        private const val KEY_HIJACK = "hijack_resolvers"
        private const val KEY_DOH_HOSTS = "block_doh_hosts"
        private const val KEY_PAUSED_UNTIL = "paused_until"
        private const val KEY_THEME = "theme"
        private const val KEY_EXCLUDED = "excluded_packages"
        private const val KEY_BROWSER_STRICT = "browser_strict"
        private const val KEY_BROWSER_3P = "browser_third_party"
        private const val KEY_BROWSER_JS = "browser_js"
        private const val KEY_BROWSER_DESKTOP = "browser_desktop"
        private const val KEY_BROWSER_HTTPS = "browser_https_only"
        private const val KEY_BROWSER_URL = "browser_last_url"
        private const val KEY_ROOT_INSTALLED = "root_hosts_installed"
        private const val KEY_BLOCKED_TOAST = "blocked_toast"
    }
}
