package com.adshield.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.adshield.app.core.AppGraph
import com.adshield.app.ui.theme.AdShieldTheme

/**
 * The floating bubble and the panel it opens.
 *
 * Drawing over other apps is a permission only the user can grant, so the whole thing is
 * optional and stays away until the Settings screen asks for it. The panel runs in the same
 * process as the tunnel and reads the rule stores directly, which is why a tap on "block" takes
 * effect at once: the tunnel only has to be told to rebuild its rule sets.
 *
 * Every method must be called on the main thread, because the windows and the Compose owners
 * behind them are main thread objects.
 */
class OverlayPanel(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val owner = OverlayLifecycleOwner()

    private var bubble: ComposeView? = null
    private var panel: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    fun canDraw(): Boolean = canDrawOverlays(appContext)

    fun show() {
        if (bubble != null || !canDraw()) return
        owner.attach()
        val view = composeView {
            OverlayBubble(onOpen = { togglePanel() }, onMove = ::move)
        }
        val params = windowParams(focusable = false).apply {
            gravity = Gravity.TOP or Gravity.START
            x = INITIAL_MARGIN
            y = INITIAL_MARGIN
        }
        if (!runCatching { windowManager.addView(view, params) }.isSuccess) {
            owner.detach()
            return
        }
        bubble = view
        bubbleParams = params
    }

    fun hide() {
        closePanel()
        bubble?.let { view -> runCatching { windowManager.removeView(view) } }
        bubble = null
        bubbleParams = null
        owner.detach()
    }

    fun togglePanel() {
        if (panel != null) closePanel() else openPanel()
    }

    private fun openPanel() {
        if (panel != null || bubble == null || !canDraw()) return
        val view = composeView { OverlayPanelBody(onClose = { closePanel() }) }
        val params = windowParams(focusable = true).apply {
            gravity = Gravity.CENTER
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        keepAboveKeyboard(params)
        if (runCatching { windowManager.addView(view, params) }.isSuccess) {
            panel = view
        }
    }

    /** The panel has a text field, so it must move up when the keyboard opens over it. */
    @Suppress("DEPRECATION")
    private fun keepAboveKeyboard(params: WindowManager.LayoutParams) {
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    private fun closePanel() {
        panel?.let { view -> runCatching { windowManager.removeView(view) } }
        panel = null
    }

    private fun move(dx: Float, dy: Float) {
        val view = bubble ?: return
        val params = bubbleParams ?: return
        params.x = (params.x + dx.toInt()).coerceAtLeast(0)
        params.y = (params.y + dy.toInt()).coerceAtLeast(0)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun composeView(content: @Composable () -> Unit): ComposeView =
        ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val theme by AppGraph.settings.theme.collectAsState()
                AdShieldTheme(theme = theme) { content() }
            }
        }

    private fun windowParams(focusable: Boolean): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        // The bubble must never steal input from the app underneath, so it is not focusable.
        // The panel is, otherwise its text field could not open the keyboard.
        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        )
    }

    companion object {
        private const val INITIAL_MARGIN = 24

        fun canDrawOverlays(context: Context): Boolean =
            runCatching { Settings.canDrawOverlays(context.applicationContext) }.getOrDefault(false)
    }
}

/**
 * Lifecycle and saved state owner for views that live in a window of their own. Compose refuses
 * to compose without one, and a service is not an owner by itself.
 */
private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun attach() {
        runCatching { controller.performRestore(null) }
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun detach() {
        runCatching { registry.currentState = Lifecycle.State.DESTROYED }
    }
}
