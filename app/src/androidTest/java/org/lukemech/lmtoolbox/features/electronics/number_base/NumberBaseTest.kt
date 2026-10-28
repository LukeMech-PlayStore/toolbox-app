package org.lukemech.lmtoolbox.features.electronics.number_base

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.e2e.ToolTest

/** Number bases, gone through (see ToolTest), and right in every base. */
class NumberBaseTest : ToolTest(NumberBaseFeature) {
    @Test
    fun fortyTwoInEveryBase() {
        ui.openTool(NumberBaseFeature)
        // Its default: 42
        ui.compose.waitUntil(10_000) { ui.fieldText(ui.str(R.string.ec_hexadecimal)) == "2A" }
        assertEquals("101010", ui.fieldText(ui.str(R.string.ec_binary)))
        assertEquals("52", ui.fieldText(ui.str(R.string.ec_octal)))
        // FF typed in hex is 255
        ui.type(ui.str(R.string.ec_hexadecimal), "FF")
        ui.compose.waitUntil(10_000) { ui.fieldText(ui.str(R.string.ec_decimal)) == "255" }
    }
}
