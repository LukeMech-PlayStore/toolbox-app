package org.lukemech.lmtoolbox.features.electronics.zener

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.zener.ZenerPage

/** Zener diode: the series resistor of a Zener regulator. */
object ZenerFeature : AppFeature {

    override val route = "zener"
    override val titleStringResId: Int = R.string.fp_zener
    override val keywordsResId: Int = R.string.fk_zener
    override val descriptionResId: Int = R.string.fd_zener
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Shield

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        ZenerPage()
    }
}
