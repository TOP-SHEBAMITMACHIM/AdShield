package com.adshield.app.data

import android.content.Context
import android.net.Uri
import com.adshield.app.core.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Exports and restores user configuration as a small JSON document. */
class BackupManager(private val context: Context) {

    suspend fun export(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val settings = AppGraph.settings
            val rules = AppGraph.rules

            val root = JSONObject()
            root.put("version", 1)
            root.put("dnsMode", settings.dnsMode)
            root.put("customUdp", settings.customUdp)
            root.put("customDoh", settings.customDoh)
            root.put("autoStart", settings.autoStart)
            root.put("autoUpdate", settings.autoUpdate)
            root.put("hijackResolvers", settings.hijackResolvers)
            root.put("blockDohHostnames", settings.blockDohHostnames)
            root.put("theme", settings.theme.value)
            root.put("whitelist", JSONArray(rules.whitelist().sorted()))
            root.put("blacklist", JSONArray(rules.blacklist().sorted()))
            root.put("excludedApps", JSONArray(settings.excludedPackages.value.sorted()))

            val listArray = JSONArray()
            AppGraph.lists.lists.value
                .filter { !it.builtin && it.url != null }
                .forEach { meta ->
                    listArray.put(
                        JSONObject().apply {
                            put("name", meta.name)
                            put("url", meta.url)
                            put("enabled", meta.enabled)
                        }
                    )
                }
            root.put("customLists", listArray)

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(root.toString(2).toByteArray())
                stream.flush()
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@runCatching false
            val root = JSONObject(text)
            val settings = AppGraph.settings
            val rules = AppGraph.rules

            val dnsMode = root.optString("dnsMode", "")
            if (dnsMode.isNotBlank()) settings.dnsMode = dnsMode
            settings.customUdp = root.optString("customUdp", settings.customUdp)
            settings.customDoh = root.optString("customDoh", settings.customDoh)
            settings.autoStart = root.optBoolean("autoStart", settings.autoStart)
            settings.autoUpdate = root.optBoolean("autoUpdate", settings.autoUpdate)
            settings.hijackResolvers = root.optBoolean("hijackResolvers", settings.hijackResolvers)
            settings.blockDohHostnames = root.optBoolean("blockDohHostnames", settings.blockDohHostnames)
            val theme = root.optString("theme", "")
            if (theme.isNotBlank()) settings.setTheme(theme)

            rules.whitelist().toList().forEach { rules.removeWhitelist(it) }
            root.optJSONArray("whitelist")?.let { array ->
                for (i in 0 until array.length()) rules.addWhitelist(array.optString(i))
            }
            rules.blacklist().toList().forEach { rules.removeBlacklist(it) }
            root.optJSONArray("blacklist")?.let { array ->
                for (i in 0 until array.length()) rules.addBlacklist(array.optString(i))
            }

            settings.excludedPackages.value.toList().forEach { settings.setExcluded(it, false) }
            root.optJSONArray("excludedApps")?.let { array ->
                for (i in 0 until array.length()) {
                    val pkg = array.optString(i)
                    if (pkg.isNotBlank()) settings.setExcluded(pkg, true)
                }
            }

            root.optJSONArray("customLists")?.let { array ->
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val url = obj.optString("url", "")
                    if (url.isBlank()) continue
                    runCatching { AppGraph.lists.addFromUrl(obj.optString("name", ""), url) }
                        .onSuccess { result -> result.getOrNull() }
                }
            }

            AppGraph.refreshFiltersAsync()
            true
        }.getOrDefault(false)
    }
}
