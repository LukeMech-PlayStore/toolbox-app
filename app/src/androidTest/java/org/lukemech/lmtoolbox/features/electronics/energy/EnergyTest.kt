package org.lukemech.lmtoolbox.features.electronics.energy

import androidx.compose.ui.test.hasText
import org.junit.Test
import org.lukemech.lmtoolbox.e2e.ToolTest

/** Energy converter, gone through (see ToolTest), and right. */
class EnergyTest : ToolTest(EnergyFeature) {
    @Test
    fun kilowattHourAndBattery() {
        ui.openTool(EnergyFeature)
        // Its defaults: 1 kWh, and 2000 mAh at 3.7 V
        ui.waitShown(hasText("3600"), what = "1 kWh = 3600 kJ")
        ui.waitShown(hasText("7.4"), what = "2000 mAh · 3.7 V = 7.4 Wh")
    }
}
