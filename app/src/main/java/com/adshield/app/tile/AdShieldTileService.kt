package com.adshield.app.tile

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.app.PendingIntent
import com.adshield.app.MainActivity
import com.adshield.app.R
import com.adshield.app.core.EngineState
import com.adshield.app.vpn.AdVpnService

/** Quick settings tile that toggles filtering without opening the app. */
class AdShieldTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val running = EngineState.isRunning.value
        if (running) {
            AdVpnService.stop(this)
        } else {
            val consent = runCatching { VpnService.prepare(this) }.getOrNull()
            if (consent == null) {
                AdVpnService.start(this)
            } else {
                openApp()
            }
        }
        updateTile()
    }

    // The PendingIntent overload only exists from API 34, so the Intent overload stays for older
    // devices this app still supports.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val running = EngineState.isRunning.value && EngineState.pausedUntil.value <= System.currentTimeMillis()
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_shield)
        tile.updateTile()
    }
}
