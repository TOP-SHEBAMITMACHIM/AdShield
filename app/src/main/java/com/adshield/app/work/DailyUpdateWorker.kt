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
import java.util.concurrent.TimeUnit

/** Keeps the blocklists fresh once a day while the device is on Wi-Fi. */
class DailyUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        AppGraph.init(applicationContext)
        AppGraph.lists.init()
        AppGraph.lists.updateAll()
        AppGraph.refreshFilters()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() }
    )

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
