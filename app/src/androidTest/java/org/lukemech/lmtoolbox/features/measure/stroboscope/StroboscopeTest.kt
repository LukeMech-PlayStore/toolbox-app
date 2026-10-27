package org.lukemech.lmtoolbox.features.measure.stroboscope

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.lukemech.lmtoolbox.e2e.ToolTest

/** Stroboscope, gone through (see ToolTest), and its maths right: RPM, the cap, the flashes, the tap tempo. */
class StroboscopeTest : ToolTest(StroboscopeFeature) {
    @Test
    fun rpmOfHz() {
        assertEquals(600.0, rpm(10.0), 1e-9)
        assertEquals(25.0, hzOfRpm(1500.0), 1e-9)
        assertEquals(7.5, hzOfRpm(rpm(7.5)), 1e-9)
    }

    @Test
    fun rateCappedByTheTorch() {
        assertEquals(MAX_HZ, clampRate(50.0), 0.0)
        assertEquals(MIN_HZ, clampRate(0.1), 0.0)
        assertEquals(12.0, clampRate(12.0), 0.0)
    }

    @Test
    fun flashesShortButLit() {
        // 30 % of a 1 s period; at 20 Hz (50 ms) 15 ms; never under 8 ms, never past half
        assertEquals(300_000_000L, flashOnTime(1_000_000_000L))
        assertEquals(15_000_000L, flashOnTime(50_000_000L))
        assertEquals(8_000_000L, flashOnTime(20_000_000L))
        assertEquals(5_000_000L, flashOnTime(10_000_000L))
    }

    @Test
    fun tapTempoFromTheLastTaps() {
        assertNull(tapTempo(listOf(1000L)))
        // Every 250 ms: 4 Hz
        assertEquals(4.0, tapTempo(listOf(0L, 250L, 500L, 750L))!!, 1e-9)
        // A pause starts it over: the taps before it don't count
        assertEquals(2.0, tapTempo(listOf(0L, 100L, 5000L, 5500L, 6000L))!!, 1e-9)
        assertNull(tapTempo(listOf(0L, 100L, 5000L)))
        // Only the last five: an old slow tap is left out
        assertEquals(10.0, tapTempo(listOf(0L, 1000L, 1100L, 1200L, 1300L, 1400L))!!, 1e-9)
    }

    @Test
    fun fieldTextKeepsItShort() {
        assertEquals("12.5", fieldText(12.5))
        assertEquals("300", fieldText(300.0))
        assertEquals("0.33", fieldText(1.0 / 3))
    }
}
