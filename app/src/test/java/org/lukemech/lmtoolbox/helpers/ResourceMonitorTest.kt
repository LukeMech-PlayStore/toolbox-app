package org.lukemech.lmtoolbox.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResourceMonitorTest {
    private fun counters(at: Long, ticks: Map<Int, Long>, read: Map<Int, Long>? = null, rx: Long? = null) =
        ResourceCounters(at, ticks, rssBytes = 1024, readBytes = read, writtenBytes = read?.mapValues { 0L }, rxBytes = rx, txBytes = rx?.let { 0L })

    @Test
    fun noTimePassedTellsNothing() {
        val sample = ResourceMonitor.sample(counters(0, mapOf(1 to 0L)), counters(0, mapOf(1 to 100L)))
        assertNull(sample.cpuPercent)
        assertEquals(1024L, sample.rssBytes)
    }

    @Test
    fun eachProcessByItsOwnDifference() {
        // One gone meanwhile (2), one new (3): neither throws the sum off
        val before = counters(0, mapOf(1 to 100L, 2 to 5_000L), read = mapOf(1 to 1_000L, 2 to 9_000L), rx = 10_000)
        val now = counters(1000, mapOf(1 to 150L, 3 to 10L), read = mapOf(1 to 3_000L, 3 to 500L), rx = 12_000)
        val sample = ResourceMonitor.sample(before, now)
        assertEquals(2_500L, sample.readPerSec)
        assertEquals(0L, sample.writePerSec)
        assertEquals(2_000L, sample.rxPerSec)
        assertEquals(0L, sample.txPerSec)
    }

    @Test
    fun unreadIsUnknown() {
        val sample = ResourceMonitor.sample(counters(0, emptyMap()), counters(1000, emptyMap()))
        assertNull(sample.readPerSec)
        assertNull(sample.rxPerSec)
        assertEquals(0f, sample.cpuPercent)
    }
}
