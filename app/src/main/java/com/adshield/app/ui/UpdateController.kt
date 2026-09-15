package com.adshield.app.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.adshield.app.R
import com.adshield.app.core.AppGraph
import com.adshield.app.core.EngineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The three things the update UI can do. The dashboard banner and the Settings section share it,
 * so the two places cannot drift apart.
 */
class UpdateController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val canInstall: () -> Boolean,
    private val askInstallPermission: () -> Unit
) {

    /** Reads the install permission, which the user can grant outside the app. */
    fun installAllowed(): Boolean = canInstall()

    fun check() {
        if (EngineState.updateProgress.value >= 0f) return
        scope.launch {
            EngineState.updateError.value = null
            val result = AppGraph.updates.check()
            EngineState.updateChecked.value = true
            result.fold(
                onSuccess = { release -> EngineState.availableUpdate.value = release },
                onFailure = {
                    EngineState.updateError.value = context.getString(R.string.update_check_failed)
                }
            )
        }
    }

    fun downloadAndInstall() {
        val release = EngineState.availableUpdate.value ?: return
        if (EngineState.updateProgress.value >= 0f) return
        if (release.apkUrl == null) {
            EngineState.updateError.value = context.getString(R.string.update_apk_missing)
            return
        }
        // Android 8 and later need one explicit permission per app. Ask for it, explain, and let
        // the user tap again: silently failing here would look like a broken button.
        if (!canInstall()) {
            EngineState.updateError.value = context.getString(R.string.update_install_permission)
            askInstallPermission()
            return
        }
        scope.launch {
            EngineState.updateError.value = null
            EngineState.updateProgress.value = 0f
            val result = AppGraph.updates.download(release) { progress ->
                EngineState.updateProgress.value = progress
            }
            EngineState.updateProgress.value = -1f
            result.fold(
                onSuccess = { file ->
                    if (!AppGraph.updates.install(file)) {
                        EngineState.updateError.value =
                            context.getString(R.string.update_install_failed)
                    }
                },
                onFailure = {
                    EngineState.updateError.value =
                        context.getString(R.string.update_download_failed)
                }
            )
        }
    }

    fun askForInstallPermission() = askInstallPermission()

    /** Hides the banner until the next check finds the release again. */
    fun dismiss() {
        EngineState.availableUpdate.value = null
    }
}

@Composable
fun rememberUpdateController(): UpdateController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installAllowed = remember { mutableStateOf(AppGraph.updates.canInstall()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // The consent screen belongs to Android, so the answer is re-read once it closes.
        installAllowed.value = AppGraph.updates.canInstall()
    }
    return remember(context) {
        UpdateController(
            context = context,
            scope = scope,
            canInstall = { installAllowed.value },
            askInstallPermission = {
                runCatching {
                    permissionLauncher.launch(AppGraph.updates.installPermissionIntent())
                }
            }
        )
    }
}
