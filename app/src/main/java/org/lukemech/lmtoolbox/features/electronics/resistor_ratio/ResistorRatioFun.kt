package org.lukemech.lmtoolbox.features.electronics.resistor_ratio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.resistor_ratio.ResistorRatioPage

/** Resistor ratio: the standard pair nearest a ratio, and the ratio of a pair. */
object ResistorRatioFeature : AppFeature {

    override val route = "resistor_ratio"
    override val titleStringResId: Int = R.string.fp_resistor_ratio
    override val keywordsResId: Int = R.string.fk_resistor_ratio
    override val descriptionResId: Int = R.string.fd_resistor_ratio
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Balance

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        ResistorRatioPage()
    }
}
