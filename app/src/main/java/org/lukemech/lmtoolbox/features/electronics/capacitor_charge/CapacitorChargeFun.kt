package org.lukemech.lmtoolbox.features.electronics.capacitor_charge

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.capacitor_charge.CapacitorChargePage

/** Capacitor charge: an RC circuit over time. */
object CapacitorChargeFeature : AppFeature {

    override val route = "capacitor_charge"
    override val titleStringResId: Int = R.string.fp_capacitor_charge
    override val keywordsResId: Int = R.string.fk_capacitor_charge
    override val descriptionResId: Int = R.string.fd_capacitor_charge
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.HourglassBottom

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        CapacitorChargePage()
    }
}
