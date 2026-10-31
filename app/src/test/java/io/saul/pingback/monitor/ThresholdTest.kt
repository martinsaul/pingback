package io.saul.pingback.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdTest {
    @Test
    fun defaultIsFivePingsFromTwoProviders() {
        val t = Threshold()
        assertEquals(5, t.consecutivePings)
        assertEquals(2, t.minProviders)
    }

    @Test
    fun metOnlyWhenEnoughProvidersReachTheStreak() {
        val t = Threshold()
        assertFalse(t.isMet(listOf(5, 4, 0, 0)))
        assertTrue(t.isMet(listOf(5, 5, 0, 0)))
        assertTrue(t.isMet(listOf(9, 0, 7, 0)))
        assertEquals(3, t.providersMeeting(listOf(5, 6, 7, 1)))
    }

    @Test
    fun customThreshold() {
        val t = Threshold(consecutivePings = 3, minProviders = 4)
        assertFalse(t.isMet(listOf(3, 3, 3, 2)))
        assertTrue(t.isMet(listOf(3, 3, 3, 3)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroPings() {
        Threshold(consecutivePings = 0)
    }
}

class RestoreDetectorTest {
    @Test
    fun startingOnlineDoesNotAlert() {
        val d = RestoreDetector()
        repeat(4) { assertFalse(d.onRound(met = false, anyProviderFailed = false)) }
        assertFalse(d.onRound(met = true, anyProviderFailed = false))
    }

    @Test
    fun alertsOnceWhenBackAfterOutage() {
        val d = RestoreDetector()
        assertFalse(d.onRound(met = false, anyProviderFailed = true))
        assertTrue(d.isInOutage)
        assertFalse(d.onRound(met = false, anyProviderFailed = false))
        assertTrue(d.onRound(met = true, anyProviderFailed = false))
        assertFalse(d.onRound(met = true, anyProviderFailed = false))
        assertFalse(d.isInOutage)
    }

    @Test
    fun aSingleFlakyProviderWhileMetIsNotAnOutage() {
        val d = RestoreDetector()
        assertFalse(d.onRound(met = true, anyProviderFailed = true))
        assertFalse(d.onRound(met = true, anyProviderFailed = false))
    }

    @Test
    fun alertsAgainAfterASecondOutage() {
        val d = RestoreDetector()
        d.onRound(met = false, anyProviderFailed = true)
        assertTrue(d.onRound(met = true, anyProviderFailed = false))
        d.onRound(met = false, anyProviderFailed = true)
        assertTrue(d.onRound(met = true, anyProviderFailed = false))
    }
}
