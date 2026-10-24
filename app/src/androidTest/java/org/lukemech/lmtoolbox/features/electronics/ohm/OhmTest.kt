package org.lukemech.lmtoolbox.features.electronics.ohm

import org.junit.Assert.assertTrue
import org.junit.Test
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.e2e.ToolTest

/** Ohm's law, gone through (see ToolTest), and right as typed. */
class OhmTest : ToolTest(OhmFeature) {
    @Test
    fun voltageFollowsTheResistance() {
        ui.openTool(OhmFeature)
        val voltage = ui.str(R.string.ec_voltage)
        // Its defaults: 10 mA through 1 kΩ
        ui.compose.waitUntil(10_000) { ui.fieldText(voltage).startsWith("10") }
        // Its unit (kΩ) is picked beside it
        ui.type(ui.str(R.string.ec_resistance), "2")
        ui.compose.waitUntil(10_000) { ui.fieldText(voltage).startsWith("20") }
        assertTrue("U = 10 mA · 2 kΩ = 20 V, got ${ui.fieldText(voltage)}", ui.fieldText(voltage).startsWith("20"))
    }
}
