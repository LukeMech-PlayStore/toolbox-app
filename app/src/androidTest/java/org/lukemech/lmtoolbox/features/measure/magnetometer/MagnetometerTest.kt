package org.lukemech.lmtoolbox.features.measure.magnetometer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lukemech.lmtoolbox.e2e.ToolTest

/** Magnetic field, gone through (see ToolTest), and its maths right: the strength, the detector's zero and signal. */
class MagnetometerTest : ToolTest(MagnetometerFeature) {
    @Test
    fun strengthOfComponents() {
        assertEquals(50.0, magnitude(30.0, 40.0, 0.0), 1e-9)
        assertEquals(magnitude(30.0, 40.0, 0.0), magnitude(0.0, -40.0, 30.0), 1e-9)
    }

    @Test
    fun baselineAveragesItsReadings() {
        val baseline = Baseline(count = 4)
        listOf(48.0, 50.0, 52.0).forEach(baseline::add)
        assertNull(baseline.value)
        baseline.add(50.0)
        assertEquals(50.0, baseline.value!!, 1e-9)
        // Kept once taken, until it's restarted
        baseline.add(100.0)
        assertEquals(50.0, baseline.value!!, 1e-9)
        baseline.restart()
        assertNull(baseline.value)
    }

    @Test
    fun deviationEitherWay() {
        assertEquals(12.5, deviation(62.5, 50.0), 1e-9)
        assertEquals(12.5, deviation(37.5, 50.0), 1e-9)
    }

    @Test
    fun signalRisesWithTheDeviation() {
        assertEquals(0.0, strength(DETECT_FROM / 2), 0.0)
        assertNull(toneFrequency(1.0))
        assertNull(buzzInterval(1.0))
        assertEquals(0.0, strength(DETECT_FROM), 1e-9)
        assertEquals(1.0, strength(DETECT_FULL), 1e-9)
        assertEquals(1.0, strength(DETECT_FULL * 10), 0.0)
        assertTrue(toneFrequency(40.0)!! > toneFrequency(5.0)!!)
        assertTrue(buzzInterval(40.0)!! < buzzInterval(5.0)!!)
    }
}
