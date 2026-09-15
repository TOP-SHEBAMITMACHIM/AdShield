package com.adshield.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.adshield.app.core.AppGraph
import com.adshield.app.core.EngineState
import java.util.concurrent.TimeUnit

/**
 * Keeps the blocklists fresh once a day while the device is on Wi-Fi, and uses the same run to
 * ask whether a newer build of the app itself has been published.
 */
class DailyUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        AppGraph.init(applicationContext)
        AppGraph.lists.init()
        AppGraph.lists.updateAll()
        AppGraph.refreshFilters()
        checkForAppUpdate()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() }
    )

    /** A failed update check must not fail the list refresh that already succeeded. */
    private suspend fun checkForAppUpdate() {
        if (!AppGraph.settings.autoCheckUpdates) return
        val checked = AppGraph.updates.check()
        // A failed check must not clear a release that an earlier check already found.
        checked.getOrNull()?.let { release -> EngineState.availableUpdate.value = release }
        if (checked.isSuccess) EngineState.updateChecked.value = true
    }

    companion object {
        private const val UNIQUE_NAME = "adshield_daily_list_update"

        fun schedule(context: Context, enabled: Boolean) {
            val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            if (!enabled) {
                manager.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
            val request = PeriodicWorkRequestBuilder<DailyUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            manager.enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
