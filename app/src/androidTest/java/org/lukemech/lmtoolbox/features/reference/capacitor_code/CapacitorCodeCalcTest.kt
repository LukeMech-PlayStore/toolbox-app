package org.lukemech.lmtoolbox.features.reference.capacitor_code

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Capacitor markings read (CapacitorCodeCalc). */
class CapacitorCodeCalcTest {
    private fun farads(code: String) = parseCapacitorCode(code)!!.farads

    @Test
    fun threeDigits() {
        assertEquals(100e-9, farads("104"), 1e-18)
        assertEquals(10e-9, farads("103"), 1e-18)
        assertEquals(4.7e-6, farads("475"), 1e-15)
        assertEquals(1.0e-12, farads("109"), 1e-21)
        assertEquals(0.47e-12, farads("478"), 1e-21)
        assertNull(parseCapacitorCode("107"))
    }

    @Test
    fun picofaradsAndLetters() {
        assertEquals(47e-12, farads("47"), 1e-21)
        assertEquals(4.7e-9, farads("4n7"), 1e-18)
        assertEquals(0.47e-12, farads("p47"), 1e-21)
        assertEquals(2.2e-6, farads("2u2"), 1e-15)
        assertEquals(2.2e-6, farads("2μ2"), 1e-15)
        assertEquals(1.5e-12, farads("1R5"), 1e-21)
        assertEquals(10e-9, farads("10n"), 1e-18)
    }

    @Test
    fun tolerances() {
        assertEquals(CapacitorCode(22e-9, "±5 %"), parseCapacitorCode("223J")!!.let { it.copy(farads = Math.round(it.farads * 1e12) * 1e-12) })
        assertEquals("±10 %", parseCapacitorCode("104K")!!.tolerance)
        assertEquals("+80 / −20 %", parseCapacitorCode("4N7Z")!!.tolerance)
        assertNull(parseCapacitorCode("104Q"))
        assertNull(parseCapacitorCode("4n7n"))
        assertNull(parseCapacitorCode(""))
    }
}
