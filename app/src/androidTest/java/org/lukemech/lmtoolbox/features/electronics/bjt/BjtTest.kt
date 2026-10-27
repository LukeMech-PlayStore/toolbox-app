package org.lukemech.lmtoolbox.features.electronics.bjt

import androidx.compose.ui.test.hasText
import org.junit.Test
import org.lukemech.lmtoolbox.e2e.ToolTest

/** Transistor, gone through (see ToolTest), and its base resistor right. */
class BjtTest : ToolTest(BjtFeature) {
    @Test
    fun baseResistorSaturatesTheLoad() {
        ui.openTool(BjtFeature)
        // Its defaults: 100 mA, hFE 100, three times over: IB = 3 mA from 5 V less 0.7 V
        ui.waitShown(hasText("1.433 kΩ"), what = "RB = (5 − 0.7) V / 3 mA")
    }
}
