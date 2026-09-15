package com.adshield.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.adshield.app.R
import com.adshield.app.core.AppGraph
import com.adshield.app.data.AppsRepository

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val excluded by AppGraph.settings.excludedPackages.collectAsState()
    var apps by remember { mutableStateOf<List<AppsRepository.AppEntry>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = AppGraph.apps.load(context.packageName)
        loading = false
    }

    val filtered = remember(apps, query, showSystem) {
        val needle = query.trim().lowercase()
        apps.filter { entry ->
            (showSystem || !entry.system) &&
                (needle.isEmpty() ||
                    entry.label.lowercase().contains(needle) ||
                    entry.packageName.lowercase().contains(needle))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringResource(R.string.apps_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.apps_excluded_summary, excluded.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(stringResource(R.string.apps_show_system), style = MaterialTheme.typography.labelMedium)
            Switch(
                checked = showSystem,
                onCheckedChange = { showSystem = it },
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Text(
            stringResource(R.string.apps_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (!loading && filtered.isEmpty()) {
            EmptyHint(stringResource(R.string.apps_empty))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filtered, key = { it.packageName }) { entry ->
                AppRow(
                    entry = entry,
                    excluded = excluded.contains(entry.packageName),
                    onToggle = { checked -> AppGraph.settings.setExcluded(entry.packageName, checked) }
                )
            }
        }
    }
}

@Composable
private fun AppRow(entry: AppsRepository.AppEntry, excluded: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val icon = remember(entry.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(entry.packageName)
                .toBitmap(96, 96)
                .asImageBitmap()
        }.getOrNull()
    }

    ListItem(
        headlineContent = {
            Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    entry.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
            }
        },
        trailingContent = {
            Switch(checked = !excluded, onCheckedChange = { enabled -> onToggle(!enabled) })
        }
    )
}
