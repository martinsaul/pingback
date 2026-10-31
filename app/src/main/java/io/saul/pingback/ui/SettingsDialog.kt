package io.saul.pingback.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.saul.pingback.monitor.Providers
import io.saul.pingback.monitor.Settings
import io.saul.pingback.monitor.SettingsStore
import io.saul.pingback.monitor.Threshold
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(current: Settings, onDismiss: () -> Unit, onSave: (Settings) -> Unit) {
    var pings by remember { mutableIntStateOf(current.threshold.consecutivePings) }
    var providers by remember { mutableIntStateOf(current.threshold.minProviders) }
    var interval by remember { mutableIntStateOf(current.intervalSeconds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("When is the internet back?") },
        text = {
            Column {
                Text(
                    "PingBack alerts you once at least $providers of ${Providers.ALL.size} providers " +
                        "have each replied to $pings pings in a row.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                IntSlider("Pings in a row", pings, SettingsStore.PINGS_RANGE) { pings = it }
                IntSlider("Providers that must agree", providers, 1..Providers.ALL.size) { providers = it }
                IntSlider("Seconds between pings", interval, SettingsStore.INTERVAL_RANGE) { interval = it }
                TextButton(onClick = {
                    pings = Threshold.DEFAULT_CONSECUTIVE_PINGS
                    providers = Threshold.DEFAULT_MIN_PROVIDERS
                    interval = Settings.DEFAULT_INTERVAL_SECONDS
                }) { Text("Reset to defaults") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Settings(Threshold(pings, providers), interval)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun IntSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value.toString(), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}
