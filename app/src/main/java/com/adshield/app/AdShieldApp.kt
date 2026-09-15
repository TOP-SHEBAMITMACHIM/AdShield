package com.adshield.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.adshield.app.core.AppGraph
import com.adshield.app.work.DailyUpdateWorker

class AdShieldApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        createChannels(this)
        runCatching { DailyUpdateWorker.schedule(this, AppGraph.settings.autoUpdate) }
    }

    companion object {
        const val CHANNEL_STATUS = "adshield_status"

        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_STATUS) != null) return
            val channel = NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
