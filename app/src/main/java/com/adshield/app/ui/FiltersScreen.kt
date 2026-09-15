package com.adshield.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.adshield.app.R
import com.adshield.app.core.AppGraph
import com.adshield.app.core.EngineState
import com.adshield.app.core.Format
import com.adshield.app.data.BlocklistRepository
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun FiltersScreen() {
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_lists)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.tab_whitelist)) })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.tab_blacklist)) })
        }
        when (tab) {
            0 -> ListsTab()
            1 -> RulesTab(whitelist = true)
            else -> RulesTab(whitelist = false)
        }
    }
}

@Composable
private fun ListsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lists by AppGraph.lists.lists.collectAsState()
    val rules by EngineState.rulesCount.collectAsState()

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val result = AppGraph.lists.addFromUri("", uri)
                AppGraph.refreshFilters()
                busy = false
                status = result.fold(
                    { context.getString(R.string.list_imported) },
                    { context.getString(R.string.list_add_failed) }
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SectionCard(
            title = pluralStringResource(R.plurals.lists_summary, lists.size, rules, lists.size),
            modifier = Modifier.padding(16.dp)
        ) {
            // Stacked instead of side by side: on a narrow screen a Row squeezed the second
            // button until its Hebrew label wrapped letter by letter.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.lists_add),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("text/plain", "text/*", "*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.lists_import),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            val updated = AppGraph.lists.updateAll()
                            AppGraph.refreshFilters()
                            busy = false
                            status = context.getString(R.string.lists_updated_count, updated)
                        }
                    },
                    enabled = !busy
                ) {
                    Text(stringResource(R.string.lists_update_all))
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.lists_updating), style = MaterialTheme.typography.bodySmall)
                }
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        if (lists.isEmpty() && !busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(lists, key = { it.id }) { meta ->
                ListCard(
                    meta = meta,
                    onToggle = { enabled ->
                        scope.launch {
                            AppGraph.lists.setEnabled(meta.id, enabled)
                            AppGraph.refreshFilters()
                        }
                    },
                    onDelete = {
                        scope.launch {
                            AppGraph.lists.remove(meta.id)
                            AppGraph.refreshFilters()
                        }
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddListDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url ->
                showAddDialog = false
                scope.launch {
                    busy = true
                    val result = AppGraph.lists.addFromUrl(name, url)
                    AppGraph.refreshFilters()
                    busy = false
                    status = result.fold(
                        { context.getString(R.string.list_added) },
                        { context.getString(R.string.list_add_failed) }
                    )
                }
            }
        )
    }
}

@Composable
private fun ListCard(
    meta: BlocklistRepository.Meta,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (meta.builtin) stringResource(R.string.list_builtin) else meta.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        pluralStringResource(R.plurals.list_domains, meta.count, meta.count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = meta.enabled, onCheckedChange = onToggle)
                if (!meta.builtin) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete))
                    }
                }
            }
            Text(
                if (meta.updatedAt > 0) {
                    stringResource(R.string.list_last_update, Format.relative(meta.updatedAt))
                } else {
                    stringResource(R.string.list_never)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            meta.url?.let { url ->
                // A hosts URL is long and Latin. Laid out inside the RTL screen its line was
                // shifted out of the card and clipped from the left, so it gets an LTR paragraph
                // of its own and an ellipsis at the end.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.common_delete),
            text = stringResource(R.string.common_delete_confirm),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun AddListDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.list_name_label)) }
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.list_url_label)) },
                    placeholder = { Text(stringResource(R.string.list_url_hint)) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, url) },
                enabled = url.startsWith("http")
            ) { Text(stringResource(R.string.common_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun RulesTab(whitelist: Boolean) {
    val store = AppGraph.rules
    val rules by (if (whitelist) store.whitelistFlow else store.blacklistFlow).collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            stringResource(if (whitelist) R.string.whitelist_desc else R.string.blacklist_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    error = false
                },
                singleLine = true,
                isError = error,
                label = {
                    Text(stringResource(if (whitelist) R.string.whitelist_hint else R.string.blacklist_hint))
                },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                val added = if (whitelist) store.addWhitelist(input) else store.addBlacklist(input)
                if (added) {
                    input = ""
                    AppGraph.refreshFiltersAsync()
                } else {
                    error = true
                }
            }) {
                Text(stringResource(R.string.rules_add))
            }
        }

        if (error) {
            Text(
                stringResource(R.string.rules_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (rules.isEmpty()) {
            EmptyHint(stringResource(R.string.rules_empty))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(rules.sorted(), key = { it }) { domain ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(domain, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        if (whitelist) store.removeWhitelist(domain) else store.removeBlacklist(domain)
                        AppGraph.refreshFiltersAsync()
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete))
                    }
                }
            }
        }
    }
}
