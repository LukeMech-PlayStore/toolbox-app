package org.lukemech.lmtoolbox.features.electronics.ohm

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.ohm.OhmPage

/** Ohm's law: current, voltage, resistance and power from two of them. */
object OhmFeature : AppFeature {

    override val route = "ohm"
    override val titleStringResId: Int = R.string.fp_ohm
    override val keywordsResId: Int = R.string.fk_ohm
    override val descriptionResId: Int = R.string.fd_ohm
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Bolt

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        OhmPage()
    }
}
