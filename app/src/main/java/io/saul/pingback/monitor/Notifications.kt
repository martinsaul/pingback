package io.saul.pingback.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.saul.pingback.R
import io.saul.pingback.ui.MainActivity

object Notifications {
    private const val CHANNEL_MONITOR = "monitor"
    private const val CHANNEL_ALERTS = "alerts"
    const val ID_MONITOR = 1
    private const val ID_BACK = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "Monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while PingBack is checking your connection"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Internet is back", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts you when your connection meets the threshold again"
            }
        )
    }

    fun monitoring(context: Context, state: MonitorState, threshold: Threshold) =
        NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_pingback)
            .setContentTitle(phaseTitle(state.phase))
            .setContentText(summary(state, threshold))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .addAction(0, "Stop", stopIntent(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    fun showInternetBack(context: Context, state: MonitorState, threshold: Threshold) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_pingback)
            .setContentTitle("Internet is back")
            .setContentText(summary(state, threshold))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_BACK, notification)
    }

    fun phaseTitle(phase: Phase) = when (phase) {
        Phase.STOPPED -> "Stopped"
        Phase.CHECKING -> "Checking..."
        Phase.ONLINE -> "Online"
        Phase.OFFLINE -> "Offline"
    }

    fun summary(state: MonitorState, threshold: Threshold) =
        "${state.providersMeeting} of ${state.providers.size} providers with " +
            "${threshold.consecutivePings}+ replies in a row (need ${threshold.minProviders})"

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopIntent(context: Context) = PendingIntent.getService(
        context, 1,
        Intent(context, MonitorService::class.java).setAction(MonitorService.ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
