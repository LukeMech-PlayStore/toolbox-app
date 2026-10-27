package org.lukemech.lmtoolbox.features.electronics.bjt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The transistor's maths (BjtCalc), against values worked out by hand. */
class BjtCalcTest {
    @Test
    fun switchBaseResistor() {
        val s = bjtSwitch(ic = 0.1, hfe = 100.0, overdrive = 3.0, vDrive = 5.0, vbe = 0.7, vceSat = 0.2)!!
        assertEquals(3e-3, s.ib, 1e-12)
        assertEquals(4.3 / 3e-3, s.rb, 1e-9)
        assertEquals(0.2 * 0.1 + 0.7 * 3e-3, s.transistorPower, 1e-12)
        assertEquals(3e-3 * 4.3, s.rbPower, 1e-12)
        // From the load's resistance: 11.8 V across 120 Ω
        assertEquals(11.8 / 120, loadCurrent(12.0, 120.0, 0.2), 1e-12)
        // A drive under VBE opens nothing
        assertNull(bjtSwitch(0.1, 100.0, 3.0, 0.5, 0.7, 0.2))
    }

    @Test
    fun dividerBiasActive() {
        val q = dividerBias(vcc = 12.0, r1 = 47e3, r2 = 10e3, rc = 2.2e3, re = 470.0, beta = 100.0, vbe = 0.7)
        val vth = 12.0 * 10 / 57
        val rth = 47e3 * 10e3 / 57e3
        val ib = (vth - 0.7) / (rth + 101 * 470)
        assertEquals(Region.Active, q.region)
        assertEquals(ib, q.ib, 1e-12)
        assertEquals(100 * ib, q.ic, 1e-12)
        assertEquals(101 * ib * 470, q.ve, 1e-9)
        assertEquals(q.ve + 0.7, q.vb, 1e-9)
        assertEquals(12.0 - 100 * ib * 2.2e3, q.vc, 1e-9)
        assertEquals(5.25, q.vce, 0.02)
    }

    @Test
    fun cutoffAndSaturation() {
        // UTh = 0.6 V, under VBE
        assertEquals(Region.Cutoff, dividerBias(12.0, 190e3, 10e3, 2.2e3, 470.0, 100.0, 0.7).region)
        // A big RC: the collector can't drop that much
        val saturated = dividerBias(12.0, 47e3, 10e3, 100e3, 470.0, 100.0, 0.7)
        assertEquals(Region.Saturation, saturated.region)
        assertEquals(VCE_SAT, saturated.vce, 1e-9)
        assertEquals(11.8 / 100470, saturated.ic, 1e-12)
    }
}
