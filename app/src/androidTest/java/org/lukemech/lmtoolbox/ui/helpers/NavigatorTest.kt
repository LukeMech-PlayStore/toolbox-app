package org.lukemech.lmtoolbox.ui.helpers

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import org.junit.Test
import org.lukemech.lmtoolbox.e2e.E2eTest
import org.lukemech.lmtoolbox.features.electronics.ohm.OhmFeature
import org.lukemech.lmtoolbox.helpers.Screen

/** The bottom bar (BottomNavigationBar): home, the last tool, the settings. */
class NavigatorTest : E2eTest() {
    @Test
    fun bottomBarGoesBackToTheLastTool() {
        ui.openTool(OhmFeature)
        ui.settings()
        ui.waitPage(Screen.Settings.route)
        ui.click(hasContentDescription(ui.str(OhmFeature.titleStringResId)) and hasClickAction())
        ui.waitPage("feature/${OhmFeature.route}")
        ui.home()
        ui.waitPage(Screen.Main.route)
    }
}
