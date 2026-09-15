package com.adshield.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adshield.app.R
import com.adshield.app.core.EngineState
import com.adshield.app.core.Format
import com.adshield.app.vpn.AdVpnService
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(onRequestVpn: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val running by EngineState.isRunning.collectAsState()
    val pausedUntil by EngineState.pausedUntil.collectAsState()
    val today by EngineState.todayBlocked.collectAsState()
    val total by EngineState.totalBlocked.collectAsState()
    val queries by EngineState.queriesToday.collectAsState()
    val rules by EngineState.rulesCount.collectAsState()
    val recent by EngineState.recent.collectAsState()
    val topDomains by EngineState.topDomains.collectAsState()
    val week by EngineState.week.collectAsState()
    val error by EngineState.vpnError.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val paused = pausedUntil > now
    val weekLabels = remember { Format.weekdayLabels() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ProtectionCard(
                running = running,
                paused = paused,
                onToggle = { enabled ->
                    if (enabled) onRequestVpn() else AdVpnService.stop(context)
                }
            )
        }

        val currentError = error
        if (currentError != null) {
            item {
                SectionCard(title = stringResource(R.string.protection_title)) {
                    Text(currentError, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { EngineState.vpnError.value = null }) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            }
        }

        if (running) {
            item {
                PauseCard(
                    paused = paused,
                    onPause = { minutes -> AdVpnService.pause(context, minutes) },
                    onResume = { AdVpnService.resume(context) }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatTile(
                    label = stringResource(R.string.stat_blocked_today),
                    value = Format.count(today),
                    icon = Icons.Filled.Block,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = stringResource(R.string.stat_blocked_total),
                    value = Format.count(total),
                    icon = Icons.Filled.Shield,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatTile(
                    label = stringResource(R.string.stat_queries_today),
                    value = Format.count(queries),
                    icon = Icons.Filled.Dns,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = stringResource(R.string.stat_rules),
                    value = Format.count(rules.toLong()),
                    icon = Icons.AutoMirrored.Filled.Rule,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.week_title),
                subtitle = stringResource(R.string.week_subtitle)
            ) {
                BarChart(
                    values = week,
                    labels = weekLabels,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
            }
        }

        item { TopDomainsCard(topDomains) }
        item { RecentCard(recent) }
    }
}

@Composable
private fun ProtectionCard(running: Boolean, paused: Boolean, onToggle: (Boolean) -> Unit) {
    val active = running && !paused
    ElevatedCard {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = if (active) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(
                        stringResource(
                            when {
                                !running -> R.string.protection_off
                                paused -> R.string.protection_paused
                                else -> R.string.protection_active
                            }
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.protection_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = running, onCheckedChange = onToggle)
            }
            Text(
                stringResource(
                    when {
                        !running -> R.string.protection_desc_off
                        paused -> R.string.protection_desc_paused
                        else -> R.string.protection_desc_active
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PauseCard(paused: Boolean, onPause: (Int) -> Unit, onResume: () -> Unit) {
    SectionCard(title = stringResource(R.string.pause_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (paused) {
                FilterChip(selected = true, onClick = onResume, label = { Text(stringResource(R.string.resume_now)) })
            } else {
                FilterChip(selected = false, onClick = { onPause(5) }, label = { Text(stringResource(R.string.pause_5)) })
                FilterChip(selected = false, onClick = { onPause(15) }, label = { Text(stringResource(R.string.pause_15)) })
                FilterChip(selected = false, onClick = { onPause(60) }, label = { Text(stringResource(R.string.pause_60)) })
            }
        }
    }
}

@Composable
private fun TopDomainsCard(topDomains: List<Pair<String, Long>>) {
    SectionCard(title = stringResource(R.string.top_domains_title)) {
        if (topDomains.isEmpty()) {
            EmptyHint(stringResource(R.string.top_domains_empty))
        } else {
            topDomains.take(6).forEach { (domain, count) ->
                KeyValueRow(label = domain, value = Format.count(count))
            }
        }
    }
}

@Composable
private fun RecentCard(recent: List<EngineState.LogEntry>) {
    SectionCard(title = stringResource(R.string.recent_title)) {
        if (recent.isEmpty()) {
            EmptyHint(stringResource(R.string.recent_empty))
        } else {
            recent.take(14).forEach { entry ->
                KeyValueRow(label = entry.domain, value = Format.relative(entry.timeMs))
            }
        }
    }
}
