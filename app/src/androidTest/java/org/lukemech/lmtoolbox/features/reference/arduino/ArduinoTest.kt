package org.lukemech.lmtoolbox.features.reference.arduino

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import org.junit.Test
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.e2e.ToolTest

/** Arduino boards, opened from the home page and gone through (see ToolTest); a board found in the picker's search. */
class ArduinoTest : ToolTest(ArduinoFeature) {
    @Test
    fun boardSearchedAndPicked() {
        ui.openTool(ArduinoFeature)
        ui.click(hasText("Uno R3") and hasClickAction(), what = "the board picker")
        ui.type(ui.str(R.string.rf_search_sheets), "2560")
        ui.click(hasText("Mega 2560") and hasClickAction(), what = "the Mega in the list")
        // Its chip's tile, over its pins (the list's lazy: D53, far down, isn't there until it's scrolled to)
        ui.waitShown(hasText("ATmega2560"), what = "the Mega's chip")
    }
}
