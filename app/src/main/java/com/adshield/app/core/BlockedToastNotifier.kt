package com.adshield.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import com.adshield.app.R

/**
 * Shows the short "ad blocked" message while another app is in the foreground.
 *
 * A toast is used on purpose: it needs no permission and no floating overlay. While filtering,
 * the app always runs a foreground service with a visible notification, so the message is not
 * coming from a background-only process. OEM builds treat these messages differently, which is
 * why Settings offers a test button and the whole feature can be switched off.
 */
class BlockedToastNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var lastShownMs = 0L

    /** Called from the tunnel thread for every blocked lookup. */
    fun onBlocked(domain: String, enabled: Boolean) {
        val label = BlockedToastPolicy.shorten(domain)
        if (label.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!BlockedToastPolicy.shouldShow(
                enabled = enabled,
                appVisible = EngineState.appVisible.value,
                screenOn = isScreenOn(),
                nowMs = now,
                lastShownMs = lastShownMs
            )
        ) {
            return
        }
        show(label, now)
    }

    /** Ignores the throttle so the user can check that messages work on this device. */
    fun showSample(domain: String) {
        val label = BlockedToastPolicy.shorten(domain)
        if (label.isEmpty()) return
        show(label, System.currentTimeMillis())
    }

    private fun show(label: String, nowMs: Long) {
        lastShownMs = nowMs
        val text = runCatching { appContext.getString(R.string.toast_ad_blocked, label) }
            .getOrNull() ?: return
        main.post {
            runCatching { Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun isScreenOn(): Boolean = runCatching {
        (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
    }.getOrDefault(true)
}
