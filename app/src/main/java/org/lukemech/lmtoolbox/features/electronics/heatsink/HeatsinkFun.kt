package org.lukemech.lmtoolbox.features.electronics.heatsink

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.heatsink.HeatsinkPage

/** Heat sink: junction temperature, and the heat sink a part needs. */
object HeatsinkFeature : AppFeature {

    override val route = "heatsink"
    override val titleStringResId: Int = R.string.fp_heatsink
    override val keywordsResId: Int = R.string.fk_heatsink
    override val descriptionResId: Int = R.string.fd_heatsink
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Thermostat

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        HeatsinkPage()
    }
}
