package com.adshield.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.adshield.app.core.AppGraph
import com.adshield.app.core.EngineState
import com.adshield.app.ui.AppRoot
import com.adshield.app.ui.theme.AdShieldTheme
import com.adshield.app.vpn.AdVpnService

class MainActivity : ComponentActivity() {

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            AdVpnService.start(this)
        } else {
            EngineState.vpnError.value = getString(R.string.vpn_permission_denied)
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(applicationContext)
        setContent {
            val theme by AppGraph.settings.theme.collectAsState()
            AdShieldTheme(theme = theme) {
                AppRoot(onRequestVpn = { requestVpn() })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        EngineState.appVisible.value = true
    }

    override fun onStop() {
        super.onStop()
        EngineState.appVisible.value = false
    }

    private fun requestVpn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val consent = runCatching { VpnService.prepare(this) }.getOrNull()
        if (consent == null) {
            AdVpnService.start(this)
        } else {
            vpnPermission.launch(consent)
        }
    }
}
