package org.lukemech.lmtoolbox.features.electronics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lukemech.lmtoolbox.features.electronics.color_code.significantDigits
import org.lukemech.lmtoolbox.features.electronics.common.ESeries
import org.lukemech.lmtoolbox.features.electronics.ohm.Ohm
import org.lukemech.lmtoolbox.features.electronics.ohm.solveOhm
import org.lukemech.lmtoolbox.features.electronics.pt100.resistanceAt
import org.lukemech.lmtoolbox.features.electronics.pt100.temperatureAt
import org.lukemech.lmtoolbox.features.electronics.series_parallel.findPairs
import org.lukemech.lmtoolbox.features.electronics.smd_code.parseSmdCode

class CalculatorsTest {
    @Test
    fun `ohm's law from any two`() {
        val expected = mapOf(Ohm.U to 10.0, Ohm.I to 0.01, Ohm.R to 1000.0, Ohm.P to 0.1)
        listOf(Ohm.U to Ohm.I, Ohm.U to Ohm.R, Ohm.U to Ohm.P, Ohm.I to Ohm.R, Ohm.I to Ohm.P, Ohm.R to Ohm.P).forEach { (a, b) ->
            val solved = solveOhm(mapOf(a to expected.getValue(a), b to expected.getValue(b)))
            expected.forEach { (q, v) -> assertEquals("$a, $b -> $q", v, solved.getValue(q), v * 1e-9) }
        }
    }

    @Test
    fun `pt100 as IEC 60751 has it`() {
        assertEquals(100.0, resistanceAt(0.0, 100.0), 1e-9)
        assertEquals(138.5055, resistanceAt(100.0, 100.0), 1e-3)
        assertEquals(18.52, resistanceAt(-200.0, 100.0), 1e-2)
        // And back, below 0 °C too
        listOf(-150.0, -20.0, 0.0, 25.0, 400.0).forEach { t ->
            assertEquals(t, temperatureAt(resistanceAt(t, 1000.0), 1000.0), 1e-6)
        }
    }

    @Test
    fun `smd markings`() {
        assertEquals(10_000.0, parseSmdCode("103")!!, 1e-9)
        assertEquals(10_000.0, parseSmdCode("1002")!!, 1e-9)
        assertEquals(4.7, parseSmdCode("4R7")!!, 1e-9)
        assertEquals(0.1, parseSmdCode("R10")!!, 1e-9)
        // EIA-96: 01 is 100, C ×100
        assertEquals(10_000.0, parseSmdCode("01C")!!, 1e-6)
        assertNull(parseSmdCode("XYZ"))
        assertNull(parseSmdCode(""))
    }

    @Test
    fun `color code digits`() {
        assertEquals(listOf(4, 7) to 2, significantDigits(4700.0, 2))
        assertEquals(listOf(1, 0, 0) to 1, significantDigits(1000.0, 3))
        // Rounded up to a digit more
        assertEquals(listOf(1, 0) to 1, significantDigits(99.96, 2))
        assertNull(significantDigits(0.0, 2))
    }

    @Test
    fun `pairs close to the target`() {
        val series: (Double, Double) -> Double = { a, b -> a + b }
        val pairs = findPairs(ESeries.E24, 1500.0, series)
        assertTrue(pairs.isNotEmpty())
        pairs.forEach { (a, b) -> assertTrue(kotlin.math.abs(a + b - 1500.0) / 1500.0 < 0.02) }
    }
}
