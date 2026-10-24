package org.lukemech.lmtoolbox.ui.pages.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.helpers.SettingsSubPage

/** A settings sub-page ([SettingsSubPage]) by its route's last part. */
@Composable
fun SettingsSubPageContent(sub: String?) {
    // Extend this when new subpages are added
    when (SettingsSubPage.fromRoute(sub)) {
        SettingsSubPage.OpenSourceLicenses -> OpenSourceLicensesPage()
        SettingsSubPage.Tasks -> TasksPage()
        SettingsSubPage.Packages -> InstalledPackagesPage()
        SettingsSubPage.ToolSettings -> ToolSettingsPage()
        null -> Text("Settings page not found")
    }
}
