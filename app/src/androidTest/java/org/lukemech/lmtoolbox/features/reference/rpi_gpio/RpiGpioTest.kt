package org.lukemech.lmtoolbox.features.reference.rpi_gpio

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import org.junit.Test
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.e2e.ToolTest

/** Raspberry Pi GPIO, gone through (see ToolTest); a pin tapped in the list is told about, the search narrows it. */
class RpiGpioTest : ToolTest(RpiGpioFeature) {
    @Test
    fun pinTappedAndSearched() {
        ui.openTool(RpiGpioFeature)
        ui.click(hasText("GPIO14") and hasClickAction(), what = "GPIO14 in the list")
        ui.waitShown(hasText("${ui.str(R.string.rf_pin)} 8 · GPIO14"), what = "pin 8's details")
        ui.type(ui.str(R.string.rf_search_pins), "UART")
        ui.waitShown(hasText("UART RXD"), what = "GPIO15 found by UART")
    }
}
