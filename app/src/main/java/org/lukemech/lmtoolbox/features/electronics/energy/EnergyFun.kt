package org.lukemech.lmtoolbox.features.electronics.energy

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.energy.EnergyPage

/** Energy converter: J, Wh, cal, BTU, eV..., and a battery's mAh as Wh. */
object EnergyFeature : AppFeature {

    override val route = "energy"
    override val titleStringResId: Int = R.string.fp_energy
    override val keywordsResId: Int = R.string.fk_energy
    override val descriptionResId: Int = R.string.fd_energy
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.BatteryChargingFull

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        EnergyPage()
    }
}
