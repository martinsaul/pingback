package io.saul.pingback.monitor

/**
 * When the connection counts as "back": at least [minProviders] providers have each answered
 * [consecutivePings] pings in a row. The default is 5 consecutive replies from at least 2 providers.
 */
data class Threshold(
    val consecutivePings: Int = DEFAULT_CONSECUTIVE_PINGS,
    val minProviders: Int = DEFAULT_MIN_PROVIDERS,
) {
    init {
        require(consecutivePings >= 1) { "consecutivePings must be at least 1" }
        require(minProviders >= 1) { "minProviders must be at least 1" }
    }

    /** Number of providers whose current reply streak reaches [consecutivePings]. */
    fun providersMeeting(providerStreaks: Collection<Int>): Int =
        providerStreaks.count { it >= consecutivePings }

    fun isMet(providerStreaks: Collection<Int>): Boolean =
        providersMeeting(providerStreaks) >= minProviders

    companion object {
        const val DEFAULT_CONSECUTIVE_PINGS = 5
        const val DEFAULT_MIN_PROVIDERS = 2
    }
}

/**
 * Decides when to raise the "internet is back" alert.
 *
 * The alert fires when the threshold becomes met after the connection was seen to be down. "Down"
 * means a round in which the threshold was not met and at least one provider failed to reply, so
 * the first few rounds after pressing play on a healthy connection (when streaks are still
 * building up) do not count as an outage, and starting PingBack while online does not alert.
 */
class RestoreDetector {
    private var sawOutage = false

    /**
     * Feed one finished round. Returns true exactly when the alert should be shown.
     *
     * @param met whether the threshold is met after this round.
     * @param anyProviderFailed whether any provider missed its reply this round.
     */
    fun onRound(met: Boolean, anyProviderFailed: Boolean): Boolean {
        if (!met) {
            if (anyProviderFailed) sawOutage = true
            return false
        }
        if (sawOutage) {
            sawOutage = false
            return true
        }
        return false
    }

    /** True while an outage has been seen and the connection has not yet met the threshold. */
    val isInOutage: Boolean get() = sawOutage
}
