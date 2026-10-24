package org.lukemech.lmtoolbox.features.electronics.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitsTest {
    @Test
    fun `values with SI prefixes and units`() {
        assertEquals(1100.0, parseValue("1.1 kΩ")!!, 1e-9)
        assertEquals(1e-4, parseValue("100 μF")!!, 1e-12)
        assertEquals(1e-4, parseValue("100 u")!!, 1e-12)
        assertEquals(0.02, parseValue("20 m")!!, 1e-12)
        assertEquals(1.5, parseValue("1,5")!!, 1e-12)
        assertEquals(1e5, parseValue("1e5")!!, 1e-6)
        assertNull(parseValue(""))
        assertNull(parseValue("abc"))
    }

    @Test
    fun `results with the prefix that reads best`() {
        assertEquals("1.1 kΩ", formatValue(1100.0, "Ω"))
        assertEquals("100 μF", formatValue(1e-4, "F"))
        assertEquals("2.5 V", formatValue(2.5, "V"))
        assertEquals("—", formatValue(Double.NaN, "V"))
    }

    @Test
    fun `field text with the field's own prefixes`() {
        assertEquals("4.7 k", fieldText(4700.0, "Ω"))
        assertEquals("100 m", fieldText(0.1, "W"))
        // Henries have no k: the value as one they take
        assertEquals("4700", fieldText(4700.0, "H"))
        assertEquals("1234", fieldText(1234.0, ""))
        assertEquals("0", fieldText(0.0, "Ω"))
        assertEquals("", fieldText(Double.POSITIVE_INFINITY, "Ω"))
    }

    @Test
    fun `standard values are the standard's`() {
        // As IEC 60063 has them, not as 10^(i/24) rounds (4.6, 3.2, 2.6)
        assertEquals(4700.0, ESeries.nearest(ESeries.E24, 4700.0), 1e-9)
        assertEquals(3.3, ESeries.nearest(ESeries.E12, 3.2), 1e-9)
        assertEquals(240.0, ESeries.nearest(ESeries.E24, 250.0), 1e-9)
        assertEquals(listOf(1.0, 1.5, 2.2, 3.3, 4.7, 6.8), ESeries.E6.values)
        assertEquals(24, ESeries.E24.values.size)
        assertEquals(96, ESeries.E96.values.size)
        assertEquals(9.20, ESeries.E192.values.single { it > 9.18 && it < 9.21 }, 1e-9)
    }
}
