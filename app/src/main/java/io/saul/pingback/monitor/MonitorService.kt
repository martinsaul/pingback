package io.saul.pingback.monitor

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that runs [MonitorEngine] so checks continue with the screen off or the app
 * in the background. Its coroutine scope uses [Dispatchers.IO], so nothing here touches the
 * network from the main thread.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var lastNotificationText: String? = null

    // Guards against a round that finishes after the user pressed stop: without it, a late
    // update could repaint the screen as running or re-post the ongoing notification.
    private val lock = Any()
    private var stopped = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val initial = if (job == null) MonitorState(phase = Phase.CHECKING) else MonitorRepository.state.value
        if (job == null) MonitorRepository.publish(initial)
        ServiceCompat.startForeground(
            this,
            Notifications.ID_MONITOR,
            Notifications.monitoring(this, initial, MonitorRepository.settings.value.threshold),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (job == null) job = scope.launch { newEngine().run() }
        // If the system kills the service, don't restart it: the user pressed play, not the OS.
        return START_NOT_STICKY
    }

    private fun newEngine(): MonitorEngine {
        val pinger = Pinger()
        val reader = NetworkInfoReader(getSystemService(ConnectivityManager::class.java))
        val notifications = getSystemService(NotificationManager::class.java)
        return MonitorEngine(
            ping = pinger::ping,
            readNetwork = reader::snapshot,
            settings = { MonitorRepository.settings.value },
            onState = onState@{ state ->
                synchronized(lock) {
                    if (stopped) return@onState
                    MonitorRepository.publish(state)
                    val threshold = MonitorRepository.settings.value.threshold
                    val text = Notifications.phaseTitle(state.phase) + Notifications.summary(state, threshold)
                    if (text != lastNotificationText) {
                        lastNotificationText = text
                        notifications.notify(
                            Notifications.ID_MONITOR,
                            Notifications.monitoring(this, state, threshold),
                        )
                    }
                }
            },
            onInternetBack = onBack@{ state ->
                synchronized(lock) {
                    if (stopped) return@onBack
                    Notifications.showInternetBack(this, state, MonitorRepository.settings.value.threshold)
                }
            },
        )
    }

    override fun onDestroy() {
        synchronized(lock) {
            stopped = true
            scope.cancel()
            MonitorRepository.markStopped()
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "io.saul.pingback.action.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
