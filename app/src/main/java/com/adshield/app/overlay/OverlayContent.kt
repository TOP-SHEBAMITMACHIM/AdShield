package com.adshield.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.adshield.app.R
import com.adshield.app.core.AppGraph
import com.adshield.app.core.EngineState
import com.adshield.app.core.PanelCandidates
import com.adshield.app.ui.EmptyHint

/** The small circle that stays on top of other apps; tapping it opens the panel. */
@Composable
fun OverlayBubble(onOpen: () -> Unit, onMove: (Float, Float) -> Unit) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onOpen)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onMove(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = stringResource(R.string.overlay_open),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * The panel itself: what was let through recently, one tap away from being blocked, plus a field
 * for a host the user already knows about.
 */
@Composable
fun OverlayPanelBody(onClose: () -> Unit) {
    val context = LocalContext.current
    val whitelist by AppGraph.rules.whitelistFlow.collectAsState()
    val blacklist by AppGraph.rules.blacklistFlow.collectAsState()
    val allowed by EngineState.allowedRecent.collectAsState()
    val running by EngineState.isRunning.collectAsState()
    val pausedUntil by EngineState.pausedUntil.collectAsState()
    val today by EngineState.todayBlocked.collectAsState()

    var manual by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val candidates = remember(allowed, whitelist, blacklist) {
        PanelCandidates.offered(allowed, whitelist, blacklist)
    }

    fun block(domain: String) {
        if (AppGraph.rules.addBlacklist(domain)) {
            AppGraph.refreshFiltersAsync()
            message = context.getString(R.string.overlay_blocked_done, domain)
            invalid = false
            manual = ""
        } else {
            invalid = true
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            when {
                                !running -> R.string.protection_off
                                pausedUntil > System.currentTimeMillis() -> R.string.protection_paused
                                else -> R.string.protection_active
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
                }
            }

            Text(
                stringResource(R.string.overlay_blocked_today, today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                stringResource(R.string.overlay_recent_title),
                style = MaterialTheme.typography.titleSmall
            )

            if (candidates.isEmpty()) {
                EmptyHint(stringResource(R.string.overlay_empty))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    candidates.forEach { host ->
                        CandidateRow(host = host, onBlock = { block(host) })
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manual,
                    onValueChange = {
                        manual = it
                        invalid = false
                    },
                    singleLine = true,
                    isError = invalid,
                    label = { Text(stringResource(R.string.overlay_manual_label)) },
                    placeholder = { Text(stringResource(R.string.blacklist_hint)) },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { if (manual.isNotBlank()) block(manual) },
                    enabled = manual.isNotBlank()
                ) {
                    Text(stringResource(R.string.overlay_block), maxLines = 1)
                }
            }

            if (invalid) {
                Text(
                    stringResource(R.string.rules_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CandidateRow(host: String, onBlock: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hostnames are Latin: laid out inside the RTL panel their line would be shifted and
        // clipped, so each one gets an LTR line of its own.
        val hostModifier = Modifier.weight(1f)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                host,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = hostModifier
            )
        }
        TextButton(onClick = onBlock) {
            Text(stringResource(R.string.overlay_block), maxLines = 1)
        }
    }
}
