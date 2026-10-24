package org.lukemech.lmtoolbox.features.electronics.pt100

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.pt100.Pt100Page

/** PT100: a platinum sensor's resistance and temperature. */
object Pt100Feature : AppFeature {

    override val route = "pt100"
    override val titleStringResId: Int = R.string.fp_pt100
    override val keywordsResId: Int = R.string.fk_pt100
    override val descriptionResId: Int = R.string.fd_pt100
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.DeviceThermostat

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        Pt100Page()
    }
}
