package io.saul.pingback.monitor

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The monitoring loop. Each round it re-reads the active network (so a Wi-Fi to mobile switch
 * picks up the new gateway and DNS servers), pings every target in parallel, updates the reply
 * streaks, and checks the threshold.
 *
 * All blocking work (network reads and pings) runs on [io], never on the main thread.
 */
class MonitorEngine(
    private val ping: (String) -> PingResult,
    private val readNetwork: () -> NetworkSnapshot,
    private val settings: () -> Settings,
    private val onState: (MonitorState) -> Unit,
    private val onInternetBack: (MonitorState) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val statuses = HashMap<String, TargetStatus>()
    private val detector = RestoreDetector()
    private var rounds = 0
    private var lastRestoredAt: Long? = null

    /** Runs rounds until the calling coroutine is cancelled. */
    suspend fun run() {
        while (true) {
            val started = clock()
            runRound()
            val intervalMs = settings().intervalSeconds * 1000L
            delay((intervalMs - (clock() - started)).coerceAtLeast(0))
        }
    }

    /** Performs one round and returns the resulting state. */
    suspend fun runRound(): MonitorState {
        val network = withContext(io) { readNetwork() }
        val gatewayTarget = network.gateway?.let { Target("gateway:$it", "Gateway", it, TargetKind.GATEWAY) }
        val dnsTargets = network.dnsServers.map { Target("dns:$it", "DNS server", it, TargetKind.DNS) }
        val targets = Providers.ALL + listOfNotNull(gatewayTarget) + dnsTargets

        val results = coroutineScope {
            targets.map { t -> async(io) { t to ping(t.address) } }.awaitAll()
        }

        // Forget gateway/DNS entries from a network we are no longer on.
        statuses.keys.retainAll(targets.map { it.id }.toSet())
        for ((target, result) in results) {
            val prev = statuses[target.id] ?: TargetStatus(target)
            statuses[target.id] = prev.copy(
                target = target,
                last = result,
                streak = if (result.success) prev.streak + 1 else 0,
                sent = prev.sent + 1,
                received = prev.received + if (result.success) 1 else 0,
            )
        }
        rounds++

        val threshold = settings().threshold
        val providerStatuses = Providers.ALL.map { statuses.getValue(it.id) }
        val streaks = providerStatuses.map { it.streak }
        val met = threshold.isMet(streaks)
        val anyFailed = providerStatuses.any { it.last?.success == false }
        val restored = detector.onRound(met, anyFailed)
        if (restored) lastRestoredAt = clock()

        val state = MonitorState(
            phase = when {
                met -> Phase.ONLINE
                detector.isInOutage -> Phase.OFFLINE
                else -> Phase.CHECKING
            },
            network = network,
            providers = providerStatuses,
            gateway = gatewayTarget?.let { statuses.getValue(it.id) },
            dns = dnsTargets.map { statuses.getValue(it.id) },
            providersMeeting = threshold.providersMeeting(streaks),
            rounds = rounds,
            lastRestoredAt = lastRestoredAt,
        )
        onState(state)
        if (restored) onInternetBack(state)
        return state
    }
}
