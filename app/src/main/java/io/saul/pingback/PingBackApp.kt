package io.saul.pingback

import android.app.Application
import android.os.StrictMode
import io.saul.pingback.monitor.MonitorRepository
import io.saul.pingback.monitor.Notifications

class PingBackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Debug builds crash on any network call made from the main thread, so a regression
            // is caught immediately during development.
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyLog().penaltyDeath().build()
            )
        }
        MonitorRepository.init(this)
        Notifications.createChannels(this)
    }
}
