package com.adshield.app.data

import android.content.Context
import android.net.Uri
import com.adshield.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Owns the blocklists: the bundled starter list, lists added by URL and lists imported from a
 * file. Lists are stored raw on disk and parsed into a single domain set that the filter engine
 * and the browser check against.
 */
class BlocklistRepository(private val context: Context) {

    data class Meta(
        val id: String,
        val name: String,
        val url: String?,
        val enabled: Boolean,
        val builtin: Boolean,
        val count: Int,
        val updatedAt: Long
    )

    private val dir = File(context.filesDir, "lists").apply { mkdirs() }
    private val metaFile = File(dir, "meta.json")

    private val _lists = MutableStateFlow<List<Meta>>(emptyList())
    val lists: StateFlow<List<Meta>> = _lists.asStateFlow()

    @Volatile private var cachedDomains: Set<String> = emptySet()

    fun domains(): Set<String> = cachedDomains

    suspend fun init() = withContext(Dispatchers.IO) {
        dir.mkdirs()
        if (!metaFile.exists()) seedBuiltin()
        reloadFromDisk()
    }

    suspend fun reload() = withContext(Dispatchers.IO) { reloadFromDisk() }

    private fun seedBuiltin() {
        runCatching {
            val text = context.assets.open("default_blocklist.txt")
                .bufferedReader().use { it.readText() }
            File(dir, "$BUILTIN_ID.hosts").writeText(text)
            writeMeta(
                listOf(
                    Meta(
                        id = BUILTIN_ID,
                        name = "builtin",
                        url = BUILTIN_URL,
                        enabled = true,
                        builtin = true,
                        count = 0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            )
        }
    }

    private fun reloadFromDisk() {
        val metas = readMeta()
        val all = HashSet<String>(1 shl 16)
        val updated = ArrayList<Meta>(metas.size)
        for (meta in metas) {
            val file = File(dir, "${meta.id}.hosts")
            val parsed = if (file.exists()) {
                runCatching { parseFile(file) }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
            if (meta.enabled) all += parsed
            updated += meta.copy(count = parsed.size)
        }
        cachedDomains = all
        _lists.value = updated
    }

    suspend fun addFromUrl(name: String, url: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val id = "custom_" + System.currentTimeMillis().toString(36)
            val target = File(dir, "$id.hosts")
            downloadTo(url, target)
            if (parseFile(target).isEmpty()) throw IllegalStateException(context.getString(R.string.list_invalid))
            val metas = readMeta().toMutableList()
            metas += Meta(
                id = id,
                name = name.ifBlank { Uri.parse(url).host ?: url },
                url = url,
                enabled = true,
                builtin = false,
                count = 0,
                updatedAt = System.currentTimeMillis()
            )
            writeMeta(metas)
            reloadFromDisk()
        }
    }

    suspend fun addFromUri(name: String, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw IllegalStateException(context.getString(R.string.list_invalid))
            val id = "import_" + System.currentTimeMillis().toString(36)
            val target = File(dir, "$id.hosts")
            target.writeText(text)
            if (parseFile(target).isEmpty()) throw IllegalStateException(context.getString(R.string.list_invalid))
            val metas = readMeta().toMutableList()
            metas += Meta(
                id = id,
                name = name.ifBlank { context.getString(R.string.list_custom) },
                url = null,
                enabled = true,
                builtin = false,
                count = 0,
                updatedAt = System.currentTimeMillis()
            )
            writeMeta(metas)
            reloadFromDisk()
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val metas = readMeta().map { if (it.id == id) it.copy(enabled = enabled) else it }
        writeMeta(metas)
        reloadFromDisk()
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.hosts").delete()
        writeMeta(readMeta().filterNot { it.id == id })
        reloadFromDisk()
    }

    /** Downloads every list that has a URL. Returns how many succeeded. */
    suspend fun updateAll(): Int = withContext(Dispatchers.IO) {
        var updated = 0
        var metas = readMeta()
        for (meta in metas.toList()) {
            val url = meta.url ?: continue
            val tmp = File(dir, "${meta.id}.tmp")
            val result = runCatching { downloadTo(url, tmp) }
            if (result.isSuccess) {
                val target = File(dir, "${meta.id}.hosts")
                if (target.exists()) target.delete()
                if (tmp.renameTo(target) && parseFile(target).isNotEmpty()) {
                    metas = metas.map {
                        if (it.id == meta.id) it.copy(updatedAt = System.currentTimeMillis()) else it
                    }
                    updated++
                }
            } else {
                tmp.delete()
            }
        }
        writeMeta(metas)
        reloadFromDisk()
        updated
    }

    private fun readMeta(): List<Meta> {
        if (!metaFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(metaFile.readText())
            val out = ArrayList<Meta>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val url = if (obj.isNull("url")) null else obj.optString("url", "").ifBlank { null }
                out += Meta(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    url = url,
                    enabled = obj.optBoolean("enabled", true),
                    builtin = obj.optBoolean("builtin", false),
                    count = obj.optInt("count", 0),
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    private fun writeMeta(metas: List<Meta>) {
        runCatching {
            val array = JSONArray()
            metas.forEach { meta ->
                array.put(
                    JSONObject().apply {
                        put("id", meta.id)
                        put("name", meta.name)
                        put("url", meta.url ?: JSONObject.NULL)
                        put("enabled", meta.enabled)
                        put("builtin", meta.builtin)
                        put("count", meta.count)
                        put("updatedAt", meta.updatedAt)
                    }
                )
            }
            metaFile.writeText(array.toString())
        }
    }

    private fun downloadTo(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Encoding", "gzip")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val gzipped = connection.contentEncoding?.contains("gzip", ignoreCase = true) == true
            connection.inputStream.use { raw ->
                val stream = if (gzipped) GZIPInputStream(raw) else raw
                target.outputStream().use { out -> stream.copyTo(out, BUFFER_SIZE) }
            }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun parseFile(file: File): Set<String> =
        file.bufferedReader().use { parse(it) }

    companion object {
        const val BUILTIN_ID = "builtin"
        const val BUILTIN_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
        private const val USER_AGENT = "AdShield/1.0 (Android)"
        private const val BUFFER_SIZE = 64 * 1024

        private val SKIP_HOSTS = setOf(
            "localhost",
            "localhost.localdomain",
            "local",
            "broadcasthost",
            "ip6-localhost",
            "ip6-loopback",
            "ip6-localnet",
            "ip6-mcastprefix",
            "ip6-allnodes",
            "ip6-allrouters",
            "ip6-allhosts",
            "0.0.0.0"
        )

        /**
         * Reads hosts files (`0.0.0.0 ads.example.com`), plain domain lists and the common
         * `||domain^` Adblock filter syntax.
         */
        fun parse(reader: BufferedReader): Set<String> {
            val out = HashSet<String>(1 shl 16)
            reader.forEachLine { line -> parseLine(line, out) }
            return out
        }

        fun parseText(text: String): Set<String> {
            val out = HashSet<String>(1 shl 14)
            text.lineSequence().forEach { line -> parseLine(line, out) }
            return out
        }

        private fun parseLine(rawLine: String, out: MutableSet<String>) {
            var line = rawLine.trim()
            if (line.isEmpty()) return
            when (line[0]) {
                '#', '!', ';', '[', '@' -> return
            }
            if (line.startsWith("||")) {
                line = line.substring(2)
                val end = line.indexOfFirst { it == '^' || it == '/' || it == '$' || it == '*' || it == '|' }
                addHost(if (end >= 0) line.substring(0, end) else line, out)
                return
            }
            if (line.startsWith("http://") || line.startsWith("https://")) {
                // Parsed by hand so the rule parser stays free of Android dependencies and can be
                // unit tested on the JVM.
                val host = line.substringAfter("://")
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringBefore('#')
                    .substringAfter('@')
                    .substringBefore(':')
                if (host.isNotEmpty()) addHost(host, out)
                return
            }
            if (line.contains('$') || line.contains('*')) return
            val parts = line.split(' ', '\t', ',').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return
            if (isAddress(parts[0])) {
                for (index in 1 until parts.size) addHost(parts[index], out)
            } else {
                addHost(parts[0], out)
            }
        }

        private fun isAddress(token: String): Boolean {
            if (token == "0") return true
            if (token.contains(':')) return true
            if (!token.contains('.')) return false
            return token.all { it.isDigit() || it == '.' }
        }

        private fun addHost(raw: String, out: MutableSet<String>) {
            val host = raw.trim().trimEnd('.').lowercase()
            if (host.length < 4 || host.length > 253) return
            if (!host.contains('.')) return
            // A literal address is not a hostname; some hosts files list one in the target column.
            if (host.all { it.isDigit() || it == '.' }) return
            if (host.startsWith('.') || host.endsWith('.') || host.startsWith('-')) return
            if (SKIP_HOSTS.contains(host)) return
            for (char in host) {
                val ok = char in 'a'..'z' || char in '0'..'9' || char == '.' || char == '-' || char == '_'
                if (!ok) return
            }
            out.add(host)
        }
    }
}
