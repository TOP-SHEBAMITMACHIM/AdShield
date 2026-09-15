package com.adshield.app.core

import android.content.Context
import com.adshield.app.data.AppsRepository
import com.adshield.app.data.BlocklistRepository
import com.adshield.app.data.RulesStore
import com.adshield.app.data.SettingsStore
import com.adshield.app.data.StatsStore
import com.adshield.app.data.UpdateManager
import com.adshield.app.filter.DohHosts
import com.adshield.app.filter.FilterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Tiny service locator; the app is a single process so this is enough. */
object AppGraph {

    lateinit var app: Context
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initialized = false

    val settings: SettingsStore by lazy { SettingsStore(app) }
    val stats: StatsStore by lazy { StatsStore(app) }
    val lists: BlocklistRepository by lazy { BlocklistRepository(app) }
    val rules: RulesStore by lazy { RulesStore(app) }
    val apps: AppsRepository by lazy { AppsRepository(app) }
    val updates: UpdateManager by lazy { UpdateManager(app) }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        app = context.applicationContext
        stats.load()
        stats.startPublisher(scope)
        scope.launch {
            runCatching { lists.init() }
            refreshFilters()
        }
        scope.launch { runCatching { checkForUpdateIfDue() } }
    }

    /**
     * Asks GitHub for a newer release, at most a few times a day. The manual button in Settings
     * skips the interval, this is only the background courtesy check.
     */
    private suspend fun checkForUpdateIfDue() {
        if (!settings.autoCheckUpdates) return
        val now = System.currentTimeMillis()
        if (now - settings.lastUpdateCheck < UPDATE_CHECK_INTERVAL_MS) return
        settings.lastUpdateCheck = now
        updates.check().getOrNull()?.let { release ->
            EngineState.availableUpdate.value = release
        }
        EngineState.updateChecked.value = true
    }

    /** Rebuilds the in-memory rule sets from lists and user rules. */
    suspend fun refreshFilters() {
        val blocks = lists.domains()
        val whitelist = rules.whitelist()
        val blacklist = if (settings.blockDohHostnames) rules.blacklist() + DohHosts.DOMAINS else rules.blacklist()
        FilterEngine.update(blocks, whitelist, blacklist)
        EngineState.rulesCount.value = FilterEngine.ruleCount()
        EngineState.listsLoaded.value = true
    }

    fun refreshFiltersAsync() {
        scope.launch { runCatching { refreshFilters() } }
    }

    private const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
}
