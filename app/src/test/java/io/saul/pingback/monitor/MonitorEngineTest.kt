package io.saul.pingback.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MonitorEngineTest {
    private val up = PingResult(true, 10.0, PingMethod.ICMP)
    private val down = PingResult(false, null, PingMethod.ICMP)

    private class Harness(var reachable: Set<String>, var network: NetworkSnapshot) {
        var alerts = 0
        val engine = MonitorEngine(
            ping = { if (it in reachable) PingResult(true, 10.0, PingMethod.ICMP) else PingResult(false, null, PingMethod.ICMP) },
            readNetwork = { network },
            settings = { Settings() },
            onState = {},
            onInternetBack = { alerts++ },
            io = Dispatchers.Unconfined,
        )
    }

    private val home = NetworkSnapshot("Wi-Fi", "192.168.1.1", listOf("192.168.1.1", "8.8.4.4"))

    @Test
    fun outageThenRecoveryAlertsAfterFiveRoundsFromTwoProviders() = runBlocking {
        val h = Harness(reachable = setOf("192.168.1.1"), network = home)
        var s = h.engine.runRound()
        assertEquals(Phase.OFFLINE, s.phase)
        assertEquals(1, s.gateway!!.streak)

        // Only one provider comes back: never enough.
        h.reachable = setOf("192.168.1.1", "1.1.1.1")
        repeat(6) { s = h.engine.runRound() }
        assertEquals(Phase.OFFLINE, s.phase)
        assertEquals(0, h.alerts)

        // A second provider joins; it needs five replies in a row.
        h.reachable = h.reachable + "9.9.9.9"
        repeat(4) { s = h.engine.runRound() }
        assertEquals(0, h.alerts)
        s = h.engine.runRound()
        assertEquals(Phase.ONLINE, s.phase)
        assertEquals(1, h.alerts)
        assertNotNull(s.lastRestoredAt)

        h.engine.runRound()
        assertEquals(1, h.alerts)
    }

    @Test
    fun aMissedReplyResetsTheStreak() = runBlocking {
        val all = Providers.ALL.map { it.address }.toSet()
        val h = Harness(reachable = all, network = home)
        repeat(3) { h.engine.runRound() }
        h.reachable = emptySet()
        var s = h.engine.runRound()
        assertEquals(0, s.providers.first().streak)
        h.reachable = all
        s = h.engine.runRound()
        assertEquals(1, s.providers.first().streak)
        assertEquals(5, s.providers.first().sent)
        assertEquals(4, s.providers.first().received)
    }

    @Test
    fun followsNetworkChanges() = runBlocking {
        val h = Harness(reachable = emptySet(), network = home)
        var s = h.engine.runRound()
        assertEquals("192.168.1.1", s.gateway!!.target.address)
        assertEquals(2, s.dns.size)

        h.network = NetworkSnapshot("Mobile data", null, listOf("10.0.0.53"))
        s = h.engine.runRound()
        assertNull(s.gateway)
        assertEquals(listOf("10.0.0.53"), s.dns.map { it.target.address })
        assertEquals(1, s.dns.single().sent)
    }
}
