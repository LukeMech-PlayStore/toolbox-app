package org.lukemech.lmtoolbox.features.electronics.battery_life

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import org.junit.Test
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.e2e.ToolTest

/** Batteries (life and sizes), gone through (see ToolTest); a battery picked fills its capacity in, its sizes a page. */
class BatteryLifeTest : ToolTest(BatteryLifeFeature) {
    @Test
    fun batteryPickedAndSizesShown() {
        ui.openTool(BatteryLifeFeature)
        ui.click(hasText(ui.str(R.string.ec_battery)) and hasClickAction(), what = "the battery choice")
        // Found as a user would: 21 batteries, it far down their list (a lazy one in a page, too far to scroll to)
        ui.type(ui.str(R.string.search_choices), "CR2032")
        // Not the search's field, saying it too
        ui.click(hasText("CR2032", substring = true) and hasClickAction() and !hasSetTextAction(), what = "CR2032 in its list")
        ui.waitShown(hasText("225"), what = "its capacity filled in")
        ui.click(hasText(ui.str(R.string.rf_calculate)) and hasClickAction(), what = "the page picker")
        ui.click(hasText(ui.str(R.string.rf_coin_cells)) and hasClickAction(), what = "the coin cells' table")
        ui.waitShown(hasText("20 × 3.2"), what = "CR2032's size")
    }
}
