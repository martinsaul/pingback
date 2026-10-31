package io.saul.pingback.monitor

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** The parts of the active network PingBack reports on. */
data class NetworkSnapshot(
    /** Wi-Fi, Mobile data, Ethernet, VPN, or null if there is no active network. */
    val transport: String? = null,
    val gateway: String? = null,
    val dnsServers: List<String> = emptyList(),
)

/** Reads the gateway and DNS servers of the device's current default network. */
class NetworkInfoReader(private val connectivity: ConnectivityManager) {

    fun snapshot(): NetworkSnapshot {
        val network = connectivity.activeNetwork ?: return NetworkSnapshot()
        val links = connectivity.getLinkProperties(network) ?: return NetworkSnapshot()
        val caps = connectivity.getNetworkCapabilities(network)

        val defaultGateways = links.routes
            .filter { it.isDefaultRoute }
            .mapNotNull { it.gateway }
            // A route with no gateway reports 0.0.0.0 or ::. (RouteInfo.hasGateway() is API 29+.)
            .filterNot { it.isAnyLocalAddress }
        // Prefer the IPv4 gateway: it is what most people recognise (e.g. 192.168.1.1).
        val gateway = defaultGateways.firstOrNull { it is Inet4Address } ?: defaultGateways.firstOrNull()

        return NetworkSnapshot(
            transport = caps?.let(::transportName),
            gateway = gateway?.hostAddress,
            dnsServers = links.dnsServers.mapNotNull { it.hostAddress }.distinct(),
        )
    }

    private fun transportName(caps: NetworkCapabilities): String = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
        else -> "Other"
    }
}
