package org.lukemech.lmtoolbox.features.electronics.power

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.power.PowerPage

/** Power: DC, single- and three-phase AC. */
object PowerFeature : AppFeature {

    override val route = "power"
    override val titleStringResId: Int = R.string.fp_power
    override val keywordsResId: Int = R.string.fk_power
    override val descriptionResId: Int = R.string.fd_power
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Power

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        PowerPage()
    }
}
