package org.lukemech.lmtoolbox.features.electronics.range_converter

import org.junit.Assert.assertTrue
import org.junit.Test
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.e2e.ToolTest

/** Range converter, gone through (see ToolTest), and right both ways. */
class RangeConverterTest : ToolTest(RangeConverterFeature) {
    @Test
    fun midScaleBothWays() {
        ui.openTool(RangeConverterFeature)
        val input = ui.str(R.string.ec_input)
        val output = ui.str(R.string.ec_output)
        // Its defaults: 12 mA of 4–20 mA is half of 0–100 %
        ui.compose.waitUntil(10_000) { ui.fieldText(output) == "50" }
        // A quarter back: 8 mA
        ui.type(output, "25")
        ui.compose.waitUntil(10_000) { ui.fieldText(input) == "8" }
        assertTrue("25 % of 4–20 mA = 8 mA, got ${ui.fieldText(input)}", ui.fieldText(input) == "8")
    }
}
