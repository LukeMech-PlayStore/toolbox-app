package org.lukemech.lmtoolbox.features.electronics.resistor_ratio

import androidx.compose.ui.test.hasText
import org.junit.Test
import org.lukemech.lmtoolbox.e2e.ToolTest

/** Resistor ratio, gone through (see ToolTest), and its pairs right. */
class ResistorRatioTest : ToolTest(ResistorRatioFeature) {
    @Test
    fun exactPairComesFirst() {
        ui.openTool(ResistorRatioFeature)
        // Its default, 2.5 in E24: 1.2 k and 3 k, exactly
        ui.waitShown(hasText("1.2 kΩ : 3 kΩ"), what = "1.2 kΩ : 3 kΩ for 2.5")
    }
}
