package org.lukemech.lmtoolbox.features.electronics.energy

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lukemech.lmtoolbox.features.electronics.common.formatNumber

/** The energy converter's maths (EnergyCalc) and how its numbers read. */
class EnergyCalcTest {
    @Test
    fun units() {
        assertEquals(3.6e6, convertEnergy(1.0, EnergyUnit.KWh, EnergyUnit.J), 1e-6)
        assertEquals(860.42, convertEnergy(1.0, EnergyUnit.KWh, EnergyUnit.Kcal), 0.01)
        assertEquals(3412.14, convertEnergy(1.0, EnergyUnit.KWh, EnergyUnit.Btu), 0.01)
        assertEquals(6.241509e18, convertEnergy(1.0, EnergyUnit.J, EnergyUnit.EV), 1e12)
        assertEquals(1.0, convertEnergy(4.184, EnergyUnit.J, EnergyUnit.Cal), 1e-12)
    }

    @Test
    fun batteryWattHours() {
        assertEquals(7.4, wattHours(2000.0, 3.7), 1e-12)
        assertEquals(18.5, wattHours(5000.0, 3.7), 1e-12)
    }

    @Test
    fun numbersReadPlainlyOrAsPowers() {
        assertEquals("3600", formatNumber(3600.0, 6))
        assertEquals("0.27778", formatNumber(0.277777, 5))
        assertEquals("3.6 × 10⁶", formatNumber(3.6e6, 6))
        assertEquals("6.24151 × 10¹⁸", formatNumber(6.241509074e18, 6))
        assertEquals("1.60218 × 10⁻¹⁹", formatNumber(1.602176634e-19, 6))
    }
}
