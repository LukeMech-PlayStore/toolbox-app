package org.lukemech.lmtoolbox.features.electronics.rc_filter

import kotlin.math.PI
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The filters' maths (RcFilterCalc), against values worked out by hand. */
class RcFilterCalcTest {
    @Test
    fun firstOrderIsThreeDecibelsDownAtItsCutoff() {
        assertEquals(-3.0103, firstOrderGainDb(1.0, highPass = false), 1e-4)
        assertEquals(-3.0103, firstOrderGainDb(1.0, highPass = true), 1e-4)
        // A decade past it, 20 dB a decade
        assertEquals(-20.043, firstOrderGainDb(10.0, highPass = false), 1e-3)
        assertEquals(-20.043, firstOrderGainDb(0.1, highPass = true), 1e-3)
    }

    @Test
    fun rlCutoffAndLcResonance() {
        assertEquals(1000.0 / (2 * PI * 0.01), rlCutoff(1000.0, 0.01), 1e-9)
        // 10 mH and 100 nF: 5.033 kHz
        assertEquals(5032.9, lcResonance(0.01, 100e-9), 0.1)
        assertEquals(sqrt(0.01 / 100e-9) / 100.0, seriesQ(100.0, 0.01, 100e-9), 1e-9)
    }

    @Test
    fun butterworthSecondOrder() {
        val q = 1 / sqrt(2.0)
        // Q = 1/√2: −3 dB at f0 itself, no peak, 40 dB a decade
        assertEquals(1.0, secondOrderCutoffRatio(q, highPass = false), 1e-9)
        assertEquals(1.0, secondOrderCutoffRatio(q, highPass = true), 1e-9)
        assertEquals(-3.0103, secondOrderGainDb(1.0, q, highPass = false), 1e-4)
        assertEquals(-40.0, secondOrderGainDb(10.0, q, highPass = false), 0.01)
        assertNull(secondOrderPeakDb(q))
    }

    @Test
    fun highQPeaks() {
        // Q = 10: at f0 the gain is Q (20 dB), the peak a hair above it
        assertEquals(20.0, secondOrderGainDb(1.0, 10.0, highPass = false), 1e-9)
        assertEquals(20.0109, secondOrderPeakDb(10.0)!!, 1e-3)
    }
}
