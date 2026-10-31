package io.saul.pingback.monitor

/** What a target represents. Only [PROVIDER] targets count toward the "internet is back" threshold. */
enum class TargetKind { PROVIDER, GATEWAY, DNS }

/**
 * Something PingBack pings.
 *
 * @property id stable key used to keep a target's reply streak across rounds.
 * @property address a literal IP address, so checks never depend on DNS resolution working.
 */
data class Target(
    val id: String,
    val label: String,
    val address: String,
    val kind: TargetKind,
)

/**
 * Large, anycast public resolvers run by independent operators. They are addressed by IP, answer
 * ICMP echo, and are about as close to "always up" as anything on the internet, so if two of
 * them reply consistently the connection is genuinely working.
 */
object Providers {
    val ALL: List<Target> = listOf(
        Target("cloudflare", "Cloudflare", "1.1.1.1", TargetKind.PROVIDER),
        Target("google", "Google", "8.8.8.8", TargetKind.PROVIDER),
        Target("quad9", "Quad9", "9.9.9.9", TargetKind.PROVIDER),
        Target("opendns", "OpenDNS (Cisco)", "208.67.222.222", TargetKind.PROVIDER),
    )
}
