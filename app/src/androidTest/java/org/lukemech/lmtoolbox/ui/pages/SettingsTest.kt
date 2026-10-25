package org.lukemech.lmtoolbox.ui.pages

import org.junit.Test
import org.lukemech.lmtoolbox.e2e.E2eTest
import org.lukemech.lmtoolbox.e2e.Explorer
import org.lukemech.lmtoolbox.helpers.Screen

/** The settings page and the pages it leads to (tasks, packages, licenses, the tools' settings), two deep. */
class SettingsTest : E2eTest() {
    @Test
    fun settingsThroughAndThrough() {
        ui.settings()
        ui.waitPage(Screen.Settings.route)
        Explorer(ui, "settings", depth = 2).explore()
    }
}
