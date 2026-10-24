package org.lukemech.lmtoolbox.ui.pages.settings

import org.junit.Test
import org.lukemech.lmtoolbox.MainActivity
import org.lukemech.lmtoolbox.e2e.E2eTest
import org.lukemech.lmtoolbox.e2e.Explorer
import org.lukemech.lmtoolbox.helpers.FeatureRegistry

/** Each tool's settings (FeatureSettingsPage), opened as its widget opens them: favourite, its defaults, its widget's. */
class FeatureSettingsTest : E2eTest() {
    @Test
    fun everyToolsSettings() {
        FeatureRegistry.features.forEach { feature ->
            ui.compose.runOnUiThread {
                ui.compose.activity.startActivity(MainActivity.openFeatureIntent(context, feature.route, settings = true))
            }
            ui.waitPage("settings/features/${feature.route}")
            Explorer(ui, "settings-${feature.route}", maxActions = 30, depth = 0).explore()
        }
    }
}
