package io.saul.pingback.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.saul.pingback.monitor.MonitorRepository
import io.saul.pingback.monitor.MonitorState
import io.saul.pingback.monitor.Notifications
import io.saul.pingback.monitor.Phase
import io.saul.pingback.monitor.PingMethod
import io.saul.pingback.monitor.Settings
import io.saul.pingback.monitor.TargetStatus
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onPlayStop: () -> Unit) {
    val state by MonitorRepository.state.collectAsStateWithLifecycle()
    val settings by MonitorRepository.settings.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PingBack") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusHeader(state, settings, onPlayStop) }
            item { SectionTitle("Internet providers") }
            items(state.providers, key = { it.target.id }) { TargetRow(it, settings) }
            item {
                SectionTitle(
                    "Your network" + (state.network.transport?.let { " ($it)" } ?: "")
                )
            }
            if (state.phase == Phase.STOPPED) {
                item { Hint("Press play to find your gateway and DNS servers.") }
            } else {
                item {
                    val gateway = state.gateway
                    if (gateway != null) {
                        TargetRow(gateway, settings)
                    } else {
                        Hint("No gateway on this network (normal on mobile data).")
                    }
                }
                if (state.dns.isEmpty()) {
                    item { Hint("No DNS servers reported by this network.") }
                }
                items(state.dns, key = { it.target.id }) { TargetRow(it, settings) }
            }
            item {
                Hint(
                    "Only the internet providers count toward \"Internet is back\". " +
                        "Your gateway and DNS servers are shown to help you see where a problem is."
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            current = settings,
            onDismiss = { showSettings = false },
            onSave = {
                MonitorRepository.updateSettings(it)
                showSettings = false
            },
        )
    }
}

@Composable
private fun StatusHeader(state: MonitorState, settings: Settings, onPlayStop: () -> Unit) {
    val running = state.phase != Phase.STOPPED
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledIconButton(
                onClick = onPlayStop,
                modifier = Modifier.size(96.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (running) Bad else Good,
                ),
            ) {
                Icon(
                    if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (running) "Stop" else "Start",
                    modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                Notifications.phaseTitle(state.phase),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = when (state.phase) {
                    Phase.ONLINE -> Good
                    Phase.OFFLINE -> Bad
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (running) {
                    Notifications.summary(state, settings.threshold)
                } else {
                    "Alerts when ${settings.threshold.minProviders} providers each reply " +
                        "${settings.threshold.consecutivePings} times in a row"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            state.lastRestoredAt?.let {
                Text(
                    "Last back online: " + DateFormat.getTimeInstance().format(Date(it)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TargetRow(status: TargetStatus, settings: Settings) {
    val last = status.last
    val color = when {
        last == null -> Pending
        last.success -> Good
        else -> Bad
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).background(color, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(status.target.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    status.target.address + if (last?.method == PingMethod.TCP) "  (TCP)" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when {
                        last == null -> "-"
                        !last.success -> "no reply"
                        last.latencyMs != null -> "%.0f ms".format(last.latencyMs)
                        else -> "ok"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "streak ${status.streak}/${settings.threshold.consecutivePings}" +
                        if (status.sent > 0) "  -  ${status.received * 100 / status.sent}%" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
