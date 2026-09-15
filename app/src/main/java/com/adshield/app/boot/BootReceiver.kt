package com.adshield.app.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.adshield.app.core.AppGraph
import com.adshield.app.vpn.AdVpnService

/** Re-enables protection after a reboot or an app update when the user asked for it. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        AppGraph.init(context)
        if (!AppGraph.settings.autoStart) return

        // Only start when the VPN consent is still granted; otherwise the app asks on open.
        val consent = runCatching { VpnService.prepare(context) }.getOrNull()
        if (consent == null) {
            AdVpnService.start(context)
        }
    }
}
