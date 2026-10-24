package org.lukemech.lmtoolbox.features.media.md_edit

import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.helpers.FeatureUi
import org.lukemech.lmtoolbox.ui.pages.features.media.md_edit.MetadataEditorPage

/** The metadata editor's pages (see [MetadataEditorFeature]). */
@Suppress("unused") // Loaded by its name, MetadataEditorFeature.uiClass
object ExifToolUi : FeatureUi {
    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        MetadataEditorPage(arguments?.getOrElse("sharedText") { null })
    }
}
