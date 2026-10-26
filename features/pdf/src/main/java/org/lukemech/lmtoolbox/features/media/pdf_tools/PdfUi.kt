package org.lukemech.lmtoolbox.features.media.pdf_tools

import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.helpers.FeatureUi
import org.lukemech.lmtoolbox.ui.pages.features.media.pdf_tools.FolderSetting
import org.lukemech.lmtoolbox.ui.pages.features.media.pdf_tools.PdfToolsPage

/** The PDF tools' pages (see [PdfToolsFeature]). */
@Suppress("unused") // Loaded by its name, PdfToolsFeature.uiClass
object PdfUi : FeatureUi {
    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        PdfToolsPage(arguments?.getOrElse("sharedText") { null })
    }

    override val hasSettingsContent get() = true

    /** Where what's made goes. */
    @Composable
    override fun SettingsContent() = FolderSetting()

    /** What runs stops, and the open files are forgotten: their copies go with the data. */
    override fun onClearData() = PdfJobs.forget()
}
