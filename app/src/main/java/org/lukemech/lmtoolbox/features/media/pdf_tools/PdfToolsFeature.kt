package org.lukemech.lmtoolbox.features.media.pdf_tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.ModuleFeature
import org.lukemech.lmtoolbox.helpers.ResourceTile

/** PDF tools: their pages and qpdf's and Ghostscript's use are in the pdf module (features/pdf, its media/pdf_tools). */
object PdfToolsFeature : ModuleFeature(
    route = "pdf_tools",
    titleStringResId = R.string.fp_PdfTools,
    keywordsResId = R.string.fk_PdfTools,
    descriptionResId = R.string.fd_PdfTools,
    category = FeatureCategory.Media,
    moduleIcon = Icons.Default.PictureAsPdf,
    linuxExtensions = listOf("pdf"),
    uiClass = "org.lukemech.lmtoolbox.features.media.pdf_tools.PdfUi",
    // The open files' copies (qpdf and Ghostscript read them) and what's made of them, until it's saved
    cacheName = "pdf",
    resourceTiles = listOf(ResourceTile.Cpu, ResourceTile.Ram, ResourceTile.Disk),
) {
    const val S_DSHKEY = "PdfTools_!ShareTo"
    /** A folder picked to save to (OutputFolder); none: Downloads. */
    const val S_OUTPUT = "PdfTools_output"

    override val preferenceKeys = listOf(S_DSHKEY, S_OUTPUT)
}
