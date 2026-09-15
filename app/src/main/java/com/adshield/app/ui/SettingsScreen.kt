package com.adshield.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adshield.app.R
import com.adshield.app.core.AppGraph
import com.adshield.app.core.BlockedToastNotifier
import com.adshield.app.data.BackupManager
import com.adshield.app.data.SettingsStore
import com.adshield.app.root.RootHostsManager
import com.adshield.app.vpn.AdVpnService
import com.adshield.app.work.DailyUpdateWorker
import kotlinx.coroutines.launch

private data class DnsOption(val mode: String, val labelRes: Int)

private val DNS_OPTIONS = listOf(
    DnsOption(SettingsStore.DNS_SYSTEM, R.string.dns_system),
    DnsOption(SettingsStore.DNS_CLOUDFLARE, R.string.dns_cloudflare),
    DnsOption(SettingsStore.DNS_GOOGLE, R.string.dns_google),
    DnsOption(SettingsStore.DNS_QUAD9, R.string.dns_quad9),
    DnsOption(SettingsStore.DNS_ADGUARD, R.string.dns_adguard),
    DnsOption(SettingsStore.DNS_CUSTOM, R.string.dns_custom),
    DnsOption(SettingsStore.DNS_DOH_CLOUDFLARE, R.string.dns_doh_cloudflare),
    DnsOption(SettingsStore.DNS_DOH_GOOGLE, R.string.dns_doh_google),
    DnsOption(SettingsStore.DNS_DOH_ADGUARD, R.string.dns_doh_adguard),
    DnsOption(SettingsStore.DNS_DOH_CUSTOM, R.string.dns_doh_custom)
)

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings = AppGraph.settings
    val scope = rememberCoroutineScope()

    var dnsMode by remember { mutableStateOf(settings.dnsMode) }
    var customUdp by remember { mutableStateOf(settings.customUdp) }
    var customDoh by remember { mutableStateOf(settings.customDoh) }
    var autoStart by remember { mutableStateOf(settings.autoStart) }
    var autoUpdate by remember { mutableStateOf(settings.autoUpdate) }
    var hijack by remember { mutableStateOf(settings.hijackResolvers) }
    var blockDoh by remember { mutableStateOf(settings.blockDohHostnames) }
    var blockedMessage by remember { mutableStateOf(settings.blockedToast.value) }
    var theme by remember { mutableStateOf(settings.theme.value) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.1.0"
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val ok = BackupManager(context).export(uri)
                busy = false
                status = context.getString(if (ok) R.string.backup_exported else R.string.backup_failed)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val ok = BackupManager(context).import(uri)
                AppGraph.refreshFilters()
                busy = false
                status = context.getString(if (ok) R.string.backup_imported else R.string.backup_failed)
                dnsMode = settings.dnsMode
                autoStart = settings.autoStart
                autoUpdate = settings.autoUpdate
                hijack = settings.hijackResolvers
                blockDoh = settings.blockDohHostnames
                blockedMessage = settings.blockedToast.value
                theme = settings.theme.value
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(
                title = stringResource(R.string.settings_dns_title),
                subtitle = stringResource(R.string.settings_dns_desc)
            ) {
                DNS_OPTIONS.forEach { option ->
                    RadioRow(
                        title = stringResource(option.labelRes),
                        selected = dnsMode == option.mode,
                        onClick = {
                            dnsMode = option.mode
                            settings.dnsMode = option.mode
                        }
                    )
                }

                if (dnsMode == SettingsStore.DNS_CUSTOM) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customUdp,
                            onValueChange = { customUdp = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.dns_custom_hint)) },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { settings.customUdp = customUdp }) {
                            Text(stringResource(R.string.common_save))
                        }
                    }
                }

                if (dnsMode == SettingsStore.DNS_DOH_CUSTOM) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customDoh,
                            onValueChange = { customDoh = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.dns_doh_url_hint)) },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { settings.customDoh = customDoh }) {
                            Text(stringResource(R.string.common_save))
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_advanced_title)) {
                ToggleRow(
                    title = stringResource(R.string.settings_hijack_title),
                    description = stringResource(R.string.settings_hijack_desc),
                    checked = hijack,
                    onCheckedChange = {
                        hijack = it
                        settings.hijackResolvers = it
                        AdVpnService.rebuild(context)
                    }
                )
                ToggleRow(
                    title = stringResource(R.string.settings_doh_hosts_title),
                    description = stringResource(R.string.settings_doh_hosts_desc),
                    checked = blockDoh,
                    onCheckedChange = {
                        blockDoh = it
                        settings.blockDohHostnames = it
                        AppGraph.refreshFiltersAsync()
                    }
                )
                ToggleRow(
                    title = stringResource(R.string.settings_autostart_title),
                    description = stringResource(R.string.settings_autostart_desc),
                    checked = autoStart,
                    onCheckedChange = {
                        autoStart = it
                        settings.autoStart = it
                    }
                )
                ToggleRow(
                    title = stringResource(R.string.settings_auto_update_title),
                    description = stringResource(R.string.settings_auto_update_desc),
                    checked = autoUpdate,
                    onCheckedChange = {
                        autoUpdate = it
                        settings.autoUpdate = it
                        DailyUpdateWorker.schedule(context, it)
                    }
                )
                ToggleRow(
                    title = stringResource(R.string.settings_toast_title),
                    description = stringResource(R.string.settings_toast_desc),
                    checked = blockedMessage,
                    onCheckedChange = {
                        blockedMessage = it
                        settings.setBlockedToast(it)
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            BlockedToastNotifier(context)
                                .showSample(context.getString(R.string.settings_toast_sample_domain))
                        }
                    ) { Text(stringResource(R.string.settings_toast_test)) }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_theme_title)) {
                listOf(
                    SettingsStore.THEME_SYSTEM to R.string.theme_system,
                    SettingsStore.THEME_LIGHT to R.string.theme_light,
                    SettingsStore.THEME_DARK to R.string.theme_dark
                ).forEach { (mode, label) ->
                    RadioRow(
                        title = stringResource(label),
                        selected = theme == mode,
                        onClick = {
                            theme = mode
                            settings.setTheme(mode)
                        }
                    )
                }
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.settings_root_title),
                subtitle = stringResource(R.string.settings_root_desc)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val available = RootHostsManager.isRootAvailable()
                                status = context.getString(
                                    if (available) R.string.root_available else R.string.root_unavailable
                                )
                                busy = false
                            }
                        }
                    ) { Text(stringResource(R.string.root_check)) }

                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val domains = (AppGraph.lists.domains() + AppGraph.rules.blacklist())
                                    .take(ROOT_HOSTS_LIMIT)
                                    .toSet()
                                val result = RootHostsManager.install(context, domains)
                                if (result.isSuccess) settings.rootHostsInstalled = true
                                status = result.fold(
                                    { count -> context.getString(R.string.root_installed, count) },
                                    { error -> context.getString(R.string.root_failed, error.message ?: "") }
                                )
                                busy = false
                            }
                        }
                    ) { Text(stringResource(R.string.root_install)) }

                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val result = RootHostsManager.restore(context)
                                if (result.isSuccess) settings.rootHostsInstalled = false
                                status = result.fold(
                                    { context.getString(R.string.root_restored) },
                                    { error -> context.getString(R.string.root_failed, error.message ?: "") }
                                )
                                busy = false
                            }
                        }
                    ) { Text(stringResource(R.string.root_restore)) }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_data_title)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { exportLauncher.launch("adshield-backup.json") }
                    ) { Text(stringResource(R.string.backup_export)) }

                    OutlinedButton(
                        enabled = !busy,
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
                    ) { Text(stringResource(R.string.backup_import)) }
                }
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.settings_about_title),
                subtitle = stringResource(R.string.about_version, versionName)
            ) {
                Text(
                    stringResource(R.string.about_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                status?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private const val ROOT_HOSTS_LIMIT = 200_000

@Composable
private fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
    }
}
