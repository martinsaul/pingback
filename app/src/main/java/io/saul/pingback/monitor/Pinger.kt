package io.saul.pingback.monitor

import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/** How a reply was obtained. */
enum class PingMethod { ICMP, TCP }

data class PingResult(
    val success: Boolean,
    /** Round-trip time in milliseconds, when known. */
    val latencyMs: Double?,
    val method: PingMethod,
)

/**
 * Sends one probe to an address and blocks until it answers or times out.
 *
 * This does network I/O and waits on a child process, so it must never run on the main thread;
 * [MonitorEngine] always calls it from `Dispatchers.IO`.
 *
 * The primary probe is the system `ping` binary (ICMP echo), which ordinary apps may run on
 * stock Android. If the binary is missing or cannot be started (some custom ROMs and locked-down
 * devices), PingBack falls back to a TCP connection on port 53. For the TCP probe, a refused
 * connection still counts as a reply: the host had to be reachable to refuse it.
 */
class Pinger(private val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS) {

    @Volatile
    private var icmpUnavailable = false

    fun ping(address: String): PingResult {
        if (!icmpUnavailable) {
            try {
                return icmp(address)
            } catch (_: IOException) {
                // The binary could not be started. Remember that, and use TCP from now on.
                icmpUnavailable = true
            }
        }
        return tcp(address)
    }

    private fun icmp(address: String): PingResult {
        val binary = if (address.contains(':')) "ping6" else "ping"
        val process = ProcessBuilder(binary, "-c", "1", "-W", timeoutSeconds.toString(), address)
            .redirectErrorStream(true)
            .start()
        try {
            if (!process.waitFor(timeoutSeconds + PROCESS_GRACE_SECONDS, TimeUnit.SECONDS)) {
                return PingResult(false, null, PingMethod.ICMP)
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            // Exit code 0 means a reply came back; 1 means no reply; 2 means an error such as
            // "network is unreachable". Only 0 is a success.
            val ok = process.exitValue() == 0
            return PingResult(ok, if (ok) PingOutput.parseLatencyMs(output) else null, PingMethod.ICMP)
        } finally {
            process.destroy()
        }
    }

    private fun tcp(address: String): PingResult {
        val started = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(address, TCP_PORT), timeoutSeconds * 1000) }
            PingResult(true, elapsedMs(started), PingMethod.TCP)
        } catch (e: ConnectException) {
            val refused = e.message?.contains("ECONNREFUSED") == true ||
                e.message?.contains("refused", ignoreCase = true) == true
            PingResult(refused, if (refused) elapsedMs(started) else null, PingMethod.TCP)
        } catch (_: IOException) {
            PingResult(false, null, PingMethod.TCP)
        }
    }

    private fun elapsedMs(startedNanos: Long) = (System.nanoTime() - startedNanos) / 1_000_000.0

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 2
        private const val PROCESS_GRACE_SECONDS = 2L
        private const val TCP_PORT = 53
    }
}

/** Parsing for the output of the system `ping` binary. */
object PingOutput {
    private val TIME = Regex("""time[=<]\s*([0-9]+(?:\.[0-9]+)?)\s*ms""")

    /** Returns the round-trip time from a line such as `time=12.3 ms`, or null if absent. */
    fun parseLatencyMs(output: String): Double? =
        TIME.find(output)?.groupValues?.get(1)?.toDoubleOrNull()
}
