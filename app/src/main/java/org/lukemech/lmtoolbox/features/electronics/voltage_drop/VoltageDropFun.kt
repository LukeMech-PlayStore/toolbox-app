package org.lukemech.lmtoolbox.features.electronics.voltage_drop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.voltage_drop.VoltageDropPage

/** Voltage drop: along a cable. */
object VoltageDropFeature : AppFeature {

    override val route = "voltage_drop"
    override val titleStringResId: Int = R.string.fp_voltage_drop
    override val keywordsResId: Int = R.string.fk_voltage_drop
    override val descriptionResId: Int = R.string.fd_voltage_drop
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Cable

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        VoltageDropPage()
    }
}
