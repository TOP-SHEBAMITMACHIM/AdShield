package com.adshield.app.data

import android.content.Context
import com.adshield.app.core.EngineState
import com.adshield.app.core.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Counters for blocked requests. Everything is kept in memory for speed and flushed to disk
 * in the background, so the packet loop never touches the file system.
 */
class StatsStore(context: Context) {

    private val file = File(context.filesDir, "stats.json")

    private var total = 0L
    private var queries = 0L
    private var today = 0L
    private var dayKey: String = Format.dayKey()
    private val days = HashMap<String, Long>()
    private val domains = HashMap<String, Long>()
    private val recent = ArrayDeque<EngineState.LogEntry>()

    private val dirty = AtomicBoolean(false)

    fun load() {
        runCatching {
            if (!file.exists()) return@runCatching
            val root = JSONObject(file.readText())
            total = root.optLong("total", 0L)
            queries = root.optLong("queries", 0L)
            dayKey = root.optString("dayKey", Format.dayKey())
            val storedDays = root.optJSONObject("days")
            if (storedDays != null) {
                storedDays.keys().forEach { key -> days[key] = storedDays.optLong(key, 0L) }
            }
            val storedDomains = root.optJSONObject("domains")
            if (storedDomains != null) {
                storedDomains.keys().forEach { key -> domains[key] = storedDomains.optLong(key, 0L) }
            }
            val storedRecent = root.optJSONArray("recent")
            if (storedRecent != null) {
                for (i in 0 until storedRecent.length()) {
                    val entry = storedRecent.optJSONArray(i) ?: continue
                    val time = entry.optLong(0, 0L)
                    val domain = entry.optString(1, "")
                    if (domain.isNotEmpty()) {
                        recent.addLast(EngineState.LogEntry(time, domain, true))
                    }
                }
            }
        }
        val todayKey = Format.dayKey()
        today = if (dayKey == todayKey) days[todayKey] ?: 0L else 0L
        dayKey = todayKey
        publish()
    }

    fun recordQuery(domain: String) {
        rotateDay()
        queries++
        dirty.set(true)
    }

    fun recordBlocked(domain: String) {
        rotateDay()
        today++
        total++
        days[dayKey] = today
        domains[domain] = (domains[domain] ?: 0L) + 1L
        recent.addFirst(EngineState.LogEntry(System.currentTimeMillis(), domain, true))
        while (recent.size > MAX_RECENT) recent.removeLast()
        if (domains.size > MAX_DOMAIN_ENTRIES) trimDomains()
        dirty.set(true)
    }

    private fun rotateDay() {
        val current = Format.dayKey()
        if (current != dayKey) {
            dayKey = current
            today = days[current] ?: 0L
        }
    }

    private fun trimDomains() {
        val keep = domains.entries.sortedByDescending { it.value }.take(MAX_DOMAIN_ENTRIES / 2)
        domains.clear()
        keep.forEach { (key, value) -> domains[key] = value }
    }

    fun publish() {
        EngineState.todayBlocked.value = today
        EngineState.totalBlocked.value = total
        EngineState.queriesToday.value = queries
        EngineState.recent.value = recent.toList()
        EngineState.topDomains.value = domains.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key to it.value }
        val keys = Format.lastSevenDayKeys()
        EngineState.week.value = keys.map { days[it] ?: 0L }
    }

    /** Publishes at most twice a second and flushes to disk periodically. */
    fun startPublisher(scope: CoroutineScope) {
        scope.launch {
            var ticks = 0
            while (isActive) {
                delay(PUBLISH_INTERVAL_MS)
                ticks++
                if (dirty.getAndSet(false)) publish()
                if (ticks % FLUSH_TICKS == 0) flush()
            }
        }
    }

    suspend fun flush() = withContext(Dispatchers.IO) {
        writeSync()
    }

    fun flushBlocking() {
        writeSync()
    }

    private fun writeSync() {
        runCatching {
            val root = JSONObject()
            root.put("total", total)
            root.put("queries", queries)
            root.put("dayKey", dayKey)
            val outDays = JSONObject()
            days.forEach { (key, value) -> outDays.put(key, value) }
            root.put("days", outDays)
            val outDomains = JSONObject()
            domains.entries.sortedByDescending { it.value }.take(300)
                .forEach { (key, value) -> outDomains.put(key, value) }
            root.put("domains", outDomains)
            val outRecent = JSONArray()
            recent.take(120).forEach { entry ->
                outRecent.put(JSONArray().put(entry.timeMs).put(entry.domain))
            }
            root.put("recent", outRecent)
            val tmp = File(file.parentFile, "stats.json.tmp")
            tmp.writeText(root.toString())
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    private companion object {
        const val MAX_RECENT = 200
        const val MAX_DOMAIN_ENTRIES = 4_000
        const val PUBLISH_INTERVAL_MS = 500L
        const val FLUSH_TICKS = 40
    }
}
