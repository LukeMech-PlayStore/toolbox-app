package org.lukemech.lmtoolbox.features.electronics.voltage_divider

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.voltage_divider.VoltageDividerPage

/** Voltage divider: its output, loaded or not. */
object VoltageDividerFeature : AppFeature {

    override val route = "voltage_divider"
    override val titleStringResId: Int = R.string.fp_voltage_divider
    override val keywordsResId: Int = R.string.fk_voltage_divider
    override val descriptionResId: Int = R.string.fd_voltage_divider
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Splitscreen

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        VoltageDividerPage()
    }
}
