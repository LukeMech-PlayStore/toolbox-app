package org.lukemech.lmtoolbox.features.electronics.bjt

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.bjt.BjtPage

/** Bipolar transistor: the base resistor of a switch, a divider-biased stage's operating point. */
object BjtFeature : AppFeature {

    override val route = "bjt"
    override val titleStringResId: Int = R.string.fp_bjt
    override val keywordsResId: Int = R.string.fk_bjt
    override val descriptionResId: Int = R.string.fd_bjt
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.ToggleOn

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        BjtPage()
    }
}
