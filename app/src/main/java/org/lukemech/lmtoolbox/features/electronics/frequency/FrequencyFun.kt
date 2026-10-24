package org.lukemech.lmtoolbox.features.electronics.frequency

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.frequency.FrequencyPage

/** Frequency: period and wavelength. */
object FrequencyFeature : AppFeature {

    override val route = "frequency"
    override val titleStringResId: Int = R.string.fp_frequency
    override val keywordsResId: Int = R.string.fk_frequency
    override val descriptionResId: Int = R.string.fd_frequency
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.GraphicEq

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        FrequencyPage()
    }
}
