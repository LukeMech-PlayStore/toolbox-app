package org.lukemech.lmtoolbox.features.reference.ascii

import androidx.compose.ui.test.hasText
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.e2e.ToolTest

/** ASCII table, gone through (see ToolTest), and searched. */
class AsciiTest : ToolTest(AsciiFeature) {
    @Test
    fun searchFindsTheRow() {
        ui.openTool(AsciiFeature)
        ui.waitShown(hasText("NUL"))
        ui.type(ui.str(R.string.rf_search_table), "ESC")
        ui.waitShown(hasText("1B"), what = "ESC's hex")
        // The rows without it fold away
        ui.compose.waitUntil(10_000) { !ui.exists(hasText("NUL")) }
        assertTrue(ui.exists(hasText("27")))
    }
}
