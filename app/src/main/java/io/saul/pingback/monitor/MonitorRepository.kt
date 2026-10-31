package io.saul.pingback.monitor

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide holder for the live monitor state and the user's settings. */
object MonitorRepository {
    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private lateinit var store: SettingsStore

    fun init(context: Context) {
        store = SettingsStore(context)
        _settings.value = store.load()
    }

    fun publish(state: MonitorState) {
        _state.value = state
    }

    fun markStopped() {
        _state.value = _state.value.copy(phase = Phase.STOPPED)
    }

    fun updateSettings(settings: Settings) {
        _settings.value = settings
        store.save(settings)
    }
}

/** Persists [Settings] in SharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val defaults = Settings()
        return Settings(
            threshold = Threshold(
                consecutivePings = prefs.getInt(KEY_PINGS, defaults.threshold.consecutivePings)
                    .coerceIn(PINGS_RANGE),
                minProviders = prefs.getInt(KEY_PROVIDERS, defaults.threshold.minProviders)
                    .coerceIn(1, Providers.ALL.size),
            ),
            intervalSeconds = prefs.getInt(KEY_INTERVAL, defaults.intervalSeconds).coerceIn(INTERVAL_RANGE),
        )
    }

    fun save(settings: Settings) {
        prefs.edit {
            putInt(KEY_PINGS, settings.threshold.consecutivePings)
            putInt(KEY_PROVIDERS, settings.threshold.minProviders)
            putInt(KEY_INTERVAL, settings.intervalSeconds)
        }
    }

    companion object {
        val PINGS_RANGE = 1..30
        val INTERVAL_RANGE = 1..30
        private const val KEY_PINGS = "consecutive_pings"
        private const val KEY_PROVIDERS = "min_providers"
        private const val KEY_INTERVAL = "interval_seconds"
    }
}
