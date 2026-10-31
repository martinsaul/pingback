package io.saul.pingback.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PingOutputTest {
    @Test
    fun parsesAndroidPingOutput() {
        val out = """
            PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.
            64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=12.4 ms

            --- 1.1.1.1 ping statistics ---
            1 packets transmitted, 1 received, 0% packet loss, time 0ms
            rtt min/avg/max/mdev = 12.413/12.413/12.413/0.000 ms
        """.trimIndent()
        assertEquals(12.4, PingOutput.parseLatencyMs(out)!!, 0.0001)
    }

    @Test
    fun parsesIntegerAndSubMillisecondTimes() {
        assertEquals(8.0, PingOutput.parseLatencyMs("icmp_seq=1 ttl=64 time=8 ms")!!, 0.0001)
        assertEquals(1.0, PingOutput.parseLatencyMs("icmp_seq=1 ttl=64 time<1 ms")!!, 0.0001)
    }

    @Test
    fun noReplyHasNoLatency() {
        assertNull(PingOutput.parseLatencyMs("1 packets transmitted, 0 received, 100% packet loss"))
    }
}
