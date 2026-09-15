package com.adshield.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.adshield.app.R
import com.adshield.app.core.AppGraph
import com.adshield.app.core.EngineState

/**
 * The Settings section for app updates: version in use, the automatic check, the release notes
 * of a newer build and the button that downloads and hands it to the package installer.
 */
@Composable
fun UpdateSettingsCard() {
    val updates = rememberUpdateController()
    val installed = remember { AppGraph.updates.installedVersion() }
    val available by EngineState.availableUpdate.collectAsState()
    val checked by EngineState.updateChecked.collectAsState()
    val progress by EngineState.updateProgress.collectAsState()
    val error by EngineState.updateError.collectAsState()
    var autoCheck by remember { mutableStateOf(AppGraph.settings.autoCheckUpdates) }
    val release = available

    SectionCard(
        title = stringResource(R.string.settings_update_title),
        subtitle = stringResource(R.string.update_installed, installed)
    ) {
        ToggleRow(
            title = stringResource(R.string.update_auto_title),
            description = stringResource(R.string.update_auto_desc),
            checked = autoCheck,
            onCheckedChange = {
                autoCheck = it
                AppGraph.settings.autoCheckUpdates = it
            }
        )

        Text(
            when {
                progress >= 0f -> stringResource(R.string.update_downloading)
                release != null -> stringResource(R.string.update_available, release.version)
                checked -> stringResource(R.string.update_up_to_date)
                else -> stringResource(R.string.update_not_checked)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (progress >= 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (release != null) {
            if (release.notes.isNotBlank()) {
                Text(
                    stringResource(R.string.update_notes_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    release.notes.take(NOTES_PREVIEW_LENGTH),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = progress < 0f,
                onClick = { updates.downloadAndInstall() }
            ) {
                Text(
                    stringResource(R.string.update_download_install, release.version),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!updates.installAllowed()) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { updates.askForInstallPermission() }
                ) { Text(stringResource(R.string.update_install_permission_button)) }
            }
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = progress < 0f,
            onClick = { updates.check() }
        ) { Text(stringResource(R.string.update_check_now)) }

        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Text(
            stringResource(R.string.update_digest_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The dashboard hint that keeps a published update in sight. */
@Composable
fun UpdateBannerCard() {
    val updates = rememberUpdateController()
    val available by EngineState.availableUpdate.collectAsState()
    val progress by EngineState.updateProgress.collectAsState()
    val error by EngineState.updateError.collectAsState()
    val release = available ?: return

    SectionCard(
        title = stringResource(R.string.update_available, release.version),
        subtitle = stringResource(R.string.update_banner_subtitle)
    ) {
        if (progress >= 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = progress < 0f,
            onClick = { updates.downloadAndInstall() }
        ) {
            Text(
                stringResource(R.string.update_download_install, release.version),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        TextButton(onClick = { updates.dismiss() }) {
            Text(stringResource(R.string.dismiss))
        }
    }
}

private const val NOTES_PREVIEW_LENGTH = 700
