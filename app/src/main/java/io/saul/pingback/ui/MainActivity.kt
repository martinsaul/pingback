package io.saul.pingback.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import io.saul.pingback.monitor.MonitorService
import io.saul.pingback.monitor.Phase
import io.saul.pingback.monitor.MonitorRepository

class MainActivity : ComponentActivity() {

    // Monitoring starts whether or not the user allows notifications; without the permission the
    // app still works on screen, it just cannot alert in the background.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            MonitorService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PingBackTheme {
                MainScreen(onPlayStop = ::togglePlay)
            }
        }
    }

    private fun togglePlay() {
        if (MonitorRepository.state.value.phase != Phase.STOPPED) {
            MonitorService.stop(this)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            MonitorService.start(this)
        }
    }
}
