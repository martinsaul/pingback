package io.saul.pingback.monitor

/** Overall verdict shown at the top of the screen and in the ongoing notification. */
enum class Phase {
    /** Monitoring is off. */
    STOPPED,

    /** Running, no outage seen yet, and streaks are still building toward the threshold. */
    CHECKING,

    /** The threshold is met. */
    ONLINE,

    /** Providers stopped replying and the threshold is not met. */
    OFFLINE,
}

data class TargetStatus(
    val target: Target,
    val last: PingResult? = null,
    /** Replies in a row, reset to 0 by any missed reply. */
    val streak: Int = 0,
    val sent: Int = 0,
    val received: Int = 0,
)

data class MonitorState(
    val phase: Phase = Phase.STOPPED,
    val network: NetworkSnapshot = NetworkSnapshot(),
    val providers: List<TargetStatus> = Providers.ALL.map { TargetStatus(it) },
    val gateway: TargetStatus? = null,
    val dns: List<TargetStatus> = emptyList(),
    val providersMeeting: Int = 0,
    val rounds: Int = 0,
    /** Wall-clock time of the last "internet is back" alert, if any. */
    val lastRestoredAt: Long? = null,
)

/** User-adjustable settings. */
data class Settings(
    val threshold: Threshold = Threshold(),
    /** Seconds between the start of one round of pings and the next. */
    val intervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
) {
    companion object {
        const val DEFAULT_INTERVAL_SECONDS = 2
    }
}
